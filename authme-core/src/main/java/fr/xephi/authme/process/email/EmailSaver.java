package fr.xephi.authme.process.email;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.events.EmailChangedEvent;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import org.bukkit.entity.Player;

import javax.inject.Inject;

/**
 * Writes a player's email address to the database, firing {@link EmailChangedEvent} and informing
 * the player. Shared by the flows which save an address right away and those which only save it
 * after the player confirmed it with a code.
 */
class EmailSaver {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(EmailSaver.class);

    @Inject
    private CommonService service;

    @Inject
    private DataSource dataSource;

    @Inject
    private PlayerCache playerCache;

    @Inject
    private BukkitService bukkitService;

    EmailSaver() {
    }

    /**
     * Saves the given address as the player's email and informs services.
     *
     * @param auth the player's auth object
     * @param player the player
     * @param oldEmail the address being replaced, or null if the player had none yet
     * @param newEmail the address to save
     * @return true if the address was saved, false otherwise
     */
    boolean saveEmail(PlayerAuth auth, Player player, String oldEmail, String newEmail) {
        EmailChangedEvent event = bukkitService.createAndCallEvent(isAsync
            -> new EmailChangedEvent(player, oldEmail, newEmail, isAsync));
        if (event.isCancelled()) {
            logger.info("Could not save email for player '" + player + "' – event was cancelled");
            service.send(player, oldEmail == null
                ? MessageKey.EMAIL_ADD_NOT_ALLOWED
                : MessageKey.EMAIL_CHANGE_NOT_ALLOWED);
            return false;
        }

        auth.setEmail(newEmail);
        if (!dataSource.updateEmail(auth)) {
            logger.warning("Could not save email for player '" + player + "'");
            service.send(player, MessageKey.ERROR);
            return false;
        }

        playerCache.updatePlayer(auth);
        // TODO: send an update when a messaging service will be implemented (ADD_MAIL / CHANGE_MAIL)
        service.send(player, oldEmail == null
            ? MessageKey.EMAIL_ADDED_SUCCESS
            : MessageKey.EMAIL_CHANGED_SUCCESS);
        return true;
    }
}
