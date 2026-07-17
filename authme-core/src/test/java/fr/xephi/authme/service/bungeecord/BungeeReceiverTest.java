package fr.xephi.authme.service.bungeecord;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import fr.xephi.authme.AuthMe;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.ProxySessionManager;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.security.HashUtils;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.HooksSettings;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class BungeeReceiverTest {

    @Mock
    private AuthMe plugin;

    @Mock
    private BukkitService bukkitService;

    @Mock
    private ProxySessionManager proxySessionManager;

    @Mock
    private Management management;

    @Mock
    private BungeeSender bungeeSender;

    @Mock
    private PlayerCache playerCache;

    @Mock
    private Settings settings;

    @Mock
    private Server server;

    @Mock
    private Messenger messenger;

    @Mock
    private Player player;

    @BeforeEach
    void setUp() {
        TestHelper.setupLogger();
        given(plugin.getServer()).willReturn(server);
        given(server.getMessenger()).willReturn(messenger);
    }

    private BungeeReceiver createEnabledReceiver() {
        given(settings.getProperty(HooksSettings.BUNGEECORD)).willReturn(true);
        return new BungeeReceiver(plugin, bukkitService, proxySessionManager, management, bungeeSender,
            playerCache, settings);
    }

    private static byte[] performLoginPayload(String name, String secret, long timestamp) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("perform.login");
        out.writeUTF(name);
        out.writeLong(timestamp);
        out.writeUTF(HashUtils.hmacSha256(secret, name + ":" + timestamp));
        return out.toByteArray();
    }

    private static byte[] simplePayload(String type, String argument) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(type);
        out.writeUTF(argument);
        return out.toByteArray();
    }

    @Test
    void shouldRegisterIncomingChannelWhenEnabled() {
        given(settings.getProperty(HooksSettings.BUNGEECORD)).willReturn(true);
        given(messenger.isIncomingChannelRegistered(plugin, "authme:main")).willReturn(false);

        new BungeeReceiver(plugin, bukkitService, proxySessionManager, management, bungeeSender, playerCache,
            settings);

        verify(messenger).registerIncomingPluginChannel(eq(plugin), eq("authme:main"), any(BungeeReceiver.class));
    }

    @Test
    void shouldUnregisterIncomingChannelWhenDisabledOnReload() {
        given(settings.getProperty(HooksSettings.BUNGEECORD)).willReturn(true, false);
        given(messenger.isIncomingChannelRegistered(plugin, "authme:main")).willReturn(false, true);

        BungeeReceiver bungeeReceiver =
            new BungeeReceiver(plugin, bukkitService, proxySessionManager, management, bungeeSender, playerCache,
                settings);
        bungeeReceiver.reload(settings);

        verify(messenger).registerIncomingPluginChannel(plugin, "authme:main", bungeeReceiver);
        verify(messenger).unregisterIncomingPluginChannel(plugin, "authme:main", bungeeReceiver);
    }

    @Test
    void shouldReannounceLoginWhenPlayerIsAlreadyAuthenticated() {
        // given: the proxy asks us to log in a player that something else (e.g. FastLogin) already logged in.
        String secret = "test-secret";
        given(settings.getProperty(HooksSettings.PROXY_SHARED_SECRET)).willReturn(secret);
        BungeeReceiver receiver = createEnabledReceiver();
        given(bukkitService.getPlayerExact("alice")).willReturn(player);
        given(player.isOnline()).willReturn(true);
        given(playerCache.isAuthenticated("alice")).willReturn(true);

        // when
        receiver.onPluginMessageReceived("authme:main", player,
            performLoginPayload("alice", secret, System.currentTimeMillis()));

        // then: the proxy must be told the player is logged in. A bare ACK would only stop its retries and
        // leave it convinced the player is still unauthenticated.
        verify(bungeeSender).sendAuthMeBungeecordMessage(player, MessageType.LOGIN);
        verify(bungeeSender, never()).sendAuthMeBungeecordMessage(player, MessageType.PERFORM_LOGIN_ACK);
        verify(management, never()).forceLoginFromProxy(any());
    }

    @Test
    void shouldForceLoginAndAckWhenPlayerIsNotAuthenticatedYet() {
        // given
        String secret = "test-secret";
        given(settings.getProperty(HooksSettings.PROXY_SHARED_SECRET)).willReturn(secret);
        BungeeReceiver receiver = createEnabledReceiver();
        given(bukkitService.getPlayerExact("alice")).willReturn(player);
        given(player.isOnline()).willReturn(true);
        given(playerCache.isAuthenticated("alice")).willReturn(false);

        // when
        receiver.onPluginMessageReceived("authme:main", player,
            performLoginPayload("alice", secret, System.currentTimeMillis()));

        // then
        verify(management).forceLoginFromProxy(player);
        verify(bungeeSender).sendAuthMeBungeecordMessage(player, MessageType.PERFORM_LOGIN_ACK);
    }

    @Test
    void shouldReportLoggedInPlayerOnStatusRequest() {
        // given
        BungeeReceiver receiver = createEnabledReceiver();
        given(bukkitService.getPlayerExact("alice")).willReturn(player);
        given(player.isOnline()).willReturn(true);
        given(playerCache.isAuthenticated("alice")).willReturn(true);

        // when
        receiver.onPluginMessageReceived("authme:main", player, simplePayload("status.request", "alice"));

        // then
        verify(bungeeSender).sendAuthMeBungeecordMessage(player, MessageType.LOGIN);
    }

    @Test
    void shouldNotReportUnauthenticatedPlayerOnStatusRequest() {
        // given
        BungeeReceiver receiver = createEnabledReceiver();
        given(bukkitService.getPlayerExact("alice")).willReturn(player);
        given(player.isOnline()).willReturn(true);
        given(playerCache.isAuthenticated("alice")).willReturn(false);

        // when
        receiver.onPluginMessageReceived("authme:main", player, simplePayload("status.request", "alice"));

        // then: a status request must never be able to log anyone in
        verify(bungeeSender, never()).sendAuthMeBungeecordMessage(any(), any());
    }

    @Test
    void shouldNotSendAnythingOnProxyStarted() {
        // given: proxy.started carries no HMAC, so a client can forge it. It must therefore stay free of any
        // work that scales with the player count, or it becomes a cheap amplification vector.
        BungeeReceiver receiver = createEnabledReceiver();

        // when
        receiver.onPluginMessageReceived("authme:main", player, simplePayload("proxy.started", "velocity"));

        // then
        verify(bungeeSender, never()).sendAuthMeBungeecordMessage(any(), any());
        verify(bukkitService, never()).getOnlinePlayers();
    }
}
