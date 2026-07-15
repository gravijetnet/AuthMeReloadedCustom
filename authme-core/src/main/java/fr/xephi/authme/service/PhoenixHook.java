package fr.xephi.authme.service;

import ch.jalu.injector.annotations.NoFieldScan;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Hooks into the Phoenix core plugin (RefineDevelopment) to find out the real name of a disguised
 * ("nicked") player.
 * <p>
 * Phoenix disguises a player by rewriting their GameProfile, which means {@link Player#getName()}
 * returns the disguise name instead of the account name. Phoenix keeps the account name in the
 * player's profile, so we can ask it for the original one.
 * <p>
 * The hook is written with reflection on purpose: Phoenix is a paid plugin whose API is not
 * necessarily available at build time, and its API has changed between major versions. If the plugin
 * (or an expected method) is missing, the hook stays silently disabled and AuthMe behaves as before.
 */
@NoFieldScan
public class PhoenixHook {

    private static final String PHOENIX_CLASS = "xyz.refinedev.phoenix.Phoenix";
    private static final String PROFILE_HANDLER_CLASS = "xyz.refinedev.phoenix.handler.IProfileHandler";
    private static final String PROFILE_CLASS = "xyz.refinedev.phoenix.profile.IProfile";
    private static final String DISGUISE_DATA_CLASS = "xyz.refinedev.phoenix.profile.disguise.IDisguiseData";

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(PhoenixHook.class);

    private Method phoenixGetInstance;
    private Method getProfileHandler;
    private Method getCachedProfile;
    private Method profileGetName;
    private Method profileGetDisguiseData;
    private Method disguiseIsDisguised;
    private Method disguiseGetRealName;

    private boolean available;

    @Inject
    PhoenixHook() {
        tryHookToPhoenix();
    }

    private void tryHookToPhoenix() {
        try {
            Class<?> phoenixClass = Class.forName(PHOENIX_CLASS);
            Class<?> profileHandlerClass = Class.forName(PROFILE_HANDLER_CLASS);
            Class<?> profileClass = Class.forName(PROFILE_CLASS);
            Class<?> disguiseDataClass = Class.forName(DISGUISE_DATA_CLASS);

            phoenixGetInstance = phoenixClass.getMethod("getInstance");
            getProfileHandler = phoenixClass.getMethod("getProfileHandler");
            getCachedProfile = profileHandlerClass.getMethod("getCachedProfile", UUID.class);
            profileGetName = profileClass.getMethod("getName");
            profileGetDisguiseData = profileClass.getMethod("getDisguiseData");
            disguiseIsDisguised = disguiseDataClass.getMethod("isDisguised");
            disguiseGetRealName = disguiseDataClass.getMethod("getRealName");

            available = true;
            logger.info("Hooked into Phoenix: disguised players will be authenticated with their real name");
        } catch (ClassNotFoundException e) {
            // Phoenix is not installed - nothing to do
            available = false;
        } catch (NoSuchMethodException | RuntimeException e) {
            available = false;
            logger.warning("Found Phoenix but could not hook into its API (" + e.getMessage()
                + "). Disguised players may be asked to register again.");
        }
    }

    /**
     * Returns whether the Phoenix API could be hooked into.
     *
     * @return true if the hook is usable
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Returns the real (undisguised) name of the given player according to Phoenix.
     *
     * @param player the player to look up
     * @return the player's account name, or null if it is unknown (Phoenix absent, profile not loaded)
     */
    public String getRealName(Player player) {
        if (!available) {
            return null;
        }

        try {
            Object phoenix = phoenixGetInstance.invoke(null);
            if (phoenix == null) {
                return null; // Phoenix is present but not enabled (yet)
            }
            Object profileHandler = getProfileHandler.invoke(phoenix);
            if (profileHandler == null) {
                return null;
            }
            // Only the local cache is queried: this may be called on the main thread for every player
            // event, so it must never hit the database or Redis.
            Object profile = getCachedProfile.invoke(profileHandler, player.getUniqueId());
            if (profile == null) {
                return null;
            }

            Object disguiseData = profileGetDisguiseData.invoke(profile);
            if (disguiseData != null && (Boolean) disguiseIsDisguised.invoke(disguiseData)) {
                String realName = (String) disguiseGetRealName.invoke(disguiseData);
                if (realName != null && !realName.isEmpty()) {
                    return realName;
                }
            }
            // The profile name is the account name; the disguise name only lives in the disguise data
            return (String) profileGetName.invoke(profile);
        } catch (Exception e) {
            available = false;
            logger.logException("Phoenix hook failed and has been disabled:", e);
            return null;
        }
    }
}
