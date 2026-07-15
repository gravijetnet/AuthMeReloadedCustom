package fr.xephi.authme.service;

import ch.jalu.injector.annotations.NoFieldScan;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.util.PlayerUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the name a player has to be authenticated under.
 * <p>
 * AuthMe identifies accounts by name. Nick/disguise plugins such as Phoenix rewrite the player's
 * GameProfile, so {@link Player#getName()} returns the disguise name — AuthMe would then look up a
 * non-existing account and ask the player to register again. Every place in AuthMe that needs the
 * account name of a player therefore goes through {@link fr.xephi.authme.util.PlayerUtils#getName}
 * which delegates here.
 * <p>
 * The name a player connects with is authoritative: a disguise is applied by the server after the
 * player has been accepted, so the name in {@code AsyncPlayerPreLoginEvent} is always the real one.
 * That name is remembered for the whole session. The {@link PhoenixHook} is used as a fallback for
 * players that were already online when AuthMe was loaded (e.g. after a plugin reload).
 */
@NoFieldScan
public class RealNameService implements PlayerUtils.NameResolver {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(RealNameService.class);
    private final Map<UUID, String> realNames = new ConcurrentHashMap<>();
    private final PhoenixHook phoenixHook;

    @Inject
    RealNameService(PhoenixHook phoenixHook) {
        this.phoenixHook = phoenixHook;
    }

    /**
     * Remembers the name a player connected with. Called as early as possible in the login sequence,
     * before any disguise plugin can rewrite the player's profile.
     *
     * @param uuid the player's unique id
     * @param name the name the player connected with
     */
    public void rememberConnectingPlayer(UUID uuid, String name) {
        if (uuid != null && name != null) {
            realNames.put(uuid, name);
        }
    }

    /**
     * Returns the account name of the given player, i.e. their name with any disguise removed.
     *
     * @param player the player to look up
     * @return the name to authenticate the player under
     */
    @Override
    public String getRealName(Player player) {
        String connectedName = realNames.get(player.getUniqueId());
        if (connectedName != null) {
            return connectedName;
        }

        String phoenixName = phoenixHook.getRealName(player);
        if (phoenixName != null && !phoenixName.isEmpty()) {
            realNames.put(player.getUniqueId(), phoenixName);
            logger.debug("Resolved real name of `{0}` to `{1}` via Phoenix", player.getName(), phoenixName);
            return phoenixName;
        }

        return player.getName();
    }

    /**
     * Drops the remembered name of a player, unless they are online again in the meantime. Called with a
     * delay after quitting, because AuthMe saves the player's data asynchronously after the quit event.
     *
     * @param uuid the player's unique id
     */
    public void forgetIfOffline(UUID uuid) {
        if (Bukkit.getPlayer(uuid) == null) {
            realNames.remove(uuid);
        }
    }
}
