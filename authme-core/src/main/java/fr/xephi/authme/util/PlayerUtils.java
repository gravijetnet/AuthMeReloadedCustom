package fr.xephi.authme.util;

import org.bukkit.entity.Player;

/**
 * Player utilities.
 */
public final class PlayerUtils {

    /**
     * Resolves the account name of a player. See {@link PlayerUtils#getName(Player)}.
     */
    @FunctionalInterface
    public interface NameResolver {

        /**
         * @param player the player to look up
         * @return the name the player has to be authenticated under
         */
        String getRealName(Player player);
    }

    /**
     * Held statically (and not injected) because the account name of a player is needed in nearly every
     * class of the plugin; it is set once on startup, see {@code AuthMe#onEnable}.
     */
    private static volatile NameResolver nameResolver;

    // Utility class
    private PlayerUtils() {
    }

    /**
     * Sets the resolver used by {@link #getName(Player)}. Passing null restores the default behavior of
     * simply using the player's current name.
     *
     * @param resolver the resolver to use
     */
    public static void setNameResolver(NameResolver resolver) {
        nameResolver = resolver;
    }

    /**
     * Returns the name the given player has to be authenticated under, i.e. the name of their account.
     * <p>
     * This is <b>not</b> necessarily {@link Player#getName()}: nick/disguise plugins rewrite a player's
     * profile, and a disguised player must still be able to use the account they logged in with instead
     * of being asked to register their disguise name. Always prefer this method over
     * {@code player.getName()} when the name is used to identify an AuthMe account.
     *
     * @param player the player to return the account name for
     * @return the player's account name
     */
    public static String getName(Player player) {
        NameResolver resolver = nameResolver;
        return resolver == null ? player.getName() : resolver.getRealName(player);
    }

    /**
     * Returns the IP of the given player.
     *
     * @param player The player to return the IP address for
     * @return The player's IP address
     */
    public static String getPlayerIp(Player player) {
        return player.getAddress().getAddress().getHostAddress();
    }

    /**
     * Returns if the player is an NPC or not.
     *
     * @param player The player to check
     * @return True if the player is an NPC, false otherwise
     */
    public static boolean isNpc(Player player) {
        return player.hasMetadata("NPC");
    }

}
