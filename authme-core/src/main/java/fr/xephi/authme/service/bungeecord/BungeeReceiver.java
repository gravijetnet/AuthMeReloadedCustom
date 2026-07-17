package fr.xephi.authme.service.bungeecord;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import fr.xephi.authme.AuthMe;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.ProxySessionManager;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.initialization.SettingsDependent;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.security.HashUtils;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.HooksSettings;
import fr.xephi.authme.util.PlayerUtils;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import javax.inject.Inject;
import java.util.Optional;


public class BungeeReceiver implements PluginMessageListener, SettingsDependent {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(BungeeReceiver.class);

    private final AuthMe plugin;
    private final BukkitService bukkitService;
    private final ProxySessionManager proxySessionManager;
    private final Management management;
    private final BungeeSender bungeeSender;
    private final PlayerCache playerCache;

    private static final String AUTHME_CHANNEL = "authme:main";
    private static final long MAX_AGE_MILLIS = 30_000L;

    private boolean isEnabled;
    private String proxySharedSecret;

    @Inject
    BungeeReceiver(AuthMe plugin, BukkitService bukkitService, ProxySessionManager proxySessionManager,
                   Management management, BungeeSender bungeeSender, PlayerCache playerCache, Settings settings) {
        this.plugin = plugin;
        this.bukkitService = bukkitService;
        this.proxySessionManager = proxySessionManager;
        this.management = management;
        this.bungeeSender = bungeeSender;
        this.playerCache = playerCache;
        reload(settings);
    }

    @Override
    public void reload(Settings settings) {
        this.proxySharedSecret = settings.getProperty(HooksSettings.PROXY_SHARED_SECRET);
        this.isEnabled = settings.getProperty(HooksSettings.BUNGEECORD);
        final Messenger messenger = plugin.getServer().getMessenger();
        if (this.isEnabled && messenger != null) {
            if (!messenger.isIncomingChannelRegistered(plugin, AUTHME_CHANNEL)) {
                messenger.registerIncomingPluginChannel(plugin, AUTHME_CHANNEL, this);
            }
        } else if (messenger != null && messenger.isIncomingChannelRegistered(plugin, AUTHME_CHANNEL)) {
            messenger.unregisterIncomingPluginChannel(plugin, AUTHME_CHANNEL, this);
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!isEnabled || !channel.equals(AUTHME_CHANNEL)) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(data);

        String typeId;
        try {
            typeId = in.readUTF();
        } catch (IllegalStateException e) {
            logger.warning("Received malformed AuthMe plugin message on authme:main");
            return;
        }

        Optional<MessageType> type = MessageType.fromId(typeId);
        if (!type.isPresent()) {
            logger.debug("Received unsupported AuthMe plugin message type: {0}", typeId);
            return;
        }

        String argument;
        try {
            argument = in.readUTF();
        } catch (IllegalStateException e) {
            logger.warning("Received invalid AuthMe plugin message of type " + type.get().name()
                + ": argument is missing!");
            return;
        }

        if (type.get() == MessageType.PROXY_STARTED) {
            logger.info("Proxy plugin '" + argument + "' has started and registered the authme:main channel");
            return;
        }

        if (type.get() == MessageType.STATUS_REQUEST) {
            answerStatusRequest(argument);
            return;
        }

        if (type.get() == MessageType.PERFORM_LOGIN) {
            long timestamp;
            String hmac;
            try {
                timestamp = in.readLong();
                hmac = in.readUTF();
            } catch (IllegalStateException e) {
                logger.warning("Received perform.login without HMAC — update your proxy plugin");
                return;
            }
            if (!verifyHmac(argument, timestamp, hmac)) {
                return;
            }
            performLogin(argument);
        }
    }

    private boolean verifyHmac(String playerName, long timestamp, String providedHmac) {
        if (Math.abs(System.currentTimeMillis() - timestamp) > MAX_AGE_MILLIS) {
            logger.warning("Rejected perform.login for " + playerName + ": message has expired");
            return false;
        }
        String expectedHmac = HashUtils.hmacSha256(proxySharedSecret, playerName + ":" + timestamp);
        if (!HashUtils.isEqual(expectedHmac, providedHmac)) {
            logger.warning("Rejected perform.login for " + playerName + ": invalid HMAC");
            return false;
        }
        return true;
    }

    private void performLogin(String name) {
        logger.debug("Received perform.login request for " + name);
        Player player = bukkitService.getPlayerExact(name);
        if (player == null || !player.isOnline()) {
            proxySessionManager.processProxySessionMessage(name);
            logger.info(name + " is not yet online; queued for auto-login when they connect.");
            return;
        }

        if (playerCache.isAuthenticated(name)) {
            // The player is already logged in here (e.g. FastLogin or a session resume got there first), so
            // forceLoginFromProxy would be a no-op and would never emit a login message. Re-announce the state
            // instead: a bare ACK only stops the proxy's retries and would leave it stuck on "not authenticated".
            logger.debug("Player " + name + " is already logged in; re-announcing login state to the proxy");
            bungeeSender.sendAuthMeBungeecordMessage(player, MessageType.LOGIN);
            return;
        }

        management.forceLoginFromProxy(player);
        logger.debug("Sending auto-login ACK for " + PlayerUtils.getName(player));
        bungeeSender.sendAuthMeBungeecordMessage(player, MessageType.PERFORM_LOGIN_ACK);
        logger.info(PlayerUtils.getName(player) + " is being automatically logged in via proxy request.");
    }

    /**
     * Answers a state query from the proxy. Only authenticated players are reported, and the reported name comes
     * from the looked-up player rather than from the message, so this can never log anyone in — it just lets a
     * proxy that missed a login message catch up. That is why it needs no HMAC, unlike {@code perform.login}:
     * anything arriving on this channel may have been sent by a client rather than by the proxy.
     *
     * @param name the name of the player to report on
     */
    private void answerStatusRequest(String name) {
        logger.debug("Received status.request for " + name);
        Player player = bukkitService.getPlayerExact(name);
        if (player == null || !player.isOnline()) {
            logger.debug("Ignoring status.request for " + name + ": player is not online here");
            return;
        }
        if (playerCache.isAuthenticated(name)) {
            logger.debug("Reporting " + name + " as logged in to the proxy");
            bungeeSender.sendAuthMeBungeecordMessage(player, MessageType.LOGIN);
        } else {
            logger.debug("Not reporting " + name + " to the proxy: not logged in here");
        }
    }

}
