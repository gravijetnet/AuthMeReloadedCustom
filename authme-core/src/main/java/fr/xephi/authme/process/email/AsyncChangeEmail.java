package fr.xephi.authme.process.email;

import fr.xephi.authme.data.EmailConfirmationManager;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.process.AsynchronousProcess;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.util.PlayerUtils;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.Locale;

/**
 * Async task for changing the email.
 */
public class AsyncChangeEmail implements AsynchronousProcess {

    @Inject
    private CommonService service;

    @Inject
    private PlayerCache playerCache;

    @Inject
    private DataSource dataSource;

    @Inject
    private ValidationService validationService;

    @Inject
    private EmailConfirmationManager emailConfirmationManager;

    @Inject
    private EmailSaver emailSaver;

    AsyncChangeEmail() {
    }

    /**
     * Handles the request to change the player's email address.
     *
     * @param player   the player to change the email for
     * @param oldEmail provided old email
     * @param newEmail provided new email
     */
    public void changeEmail(Player player, String oldEmail, String newEmail) {
        String playerName = PlayerUtils.getName(player).toLowerCase(Locale.ROOT);

        if (!playerCache.isAuthenticated(playerName)) {
            outputUnloggedMessage(player);
            return;
        }

        PlayerAuth auth = playerCache.getAuth(playerName);
        String currentEmail = auth.getEmail();

        if (currentEmail == null) {
            service.send(player, MessageKey.USAGE_ADD_EMAIL);
        } else if (newEmail == null || !validationService.validateEmail(newEmail)) {
            service.send(player, MessageKey.INVALID_NEW_EMAIL);
        } else if (!oldEmail.equalsIgnoreCase(currentEmail)) {
            service.send(player, MessageKey.INVALID_OLD_EMAIL);
        } else if (!validationService.isEmailFreeForRegistration(newEmail, player)) {
            service.send(player, MessageKey.EMAIL_ALREADY_USED_ERROR);
        } else if (emailConfirmationManager.isConfirmationRequired()) {
            requestConfirmation(player, oldEmail, newEmail);
        } else {
            emailSaver.saveEmail(auth, player, oldEmail, newEmail);
        }
    }

    /**
     * Mails a code to the new address; the player must send it back before the address is saved.
     * The code goes to the new address, so confirming it proves the player can read mail there.
     *
     * @param player the player who requested the change
     * @param oldEmail the address being replaced
     * @param newEmail the address to confirm
     */
    private void requestConfirmation(Player player, String oldEmail, String newEmail) {
        String name = PlayerUtils.getName(player);
        if (emailConfirmationManager.createAndSendCode(name, newEmail, oldEmail)) {
            service.send(player, MessageKey.EMAIL_CONFIRMATION_SENT,
                newEmail, String.valueOf(emailConfirmationManager.getExpirationMinutes()));
        } else {
            service.send(player, MessageKey.EMAIL_SEND_FAILURE);
        }
    }

    private void outputUnloggedMessage(Player player) {
        if (dataSource.isAuthAvailable(PlayerUtils.getName(player))) {
            service.send(player, MessageKey.LOGIN_MESSAGE);
        } else {
            service.send(player, MessageKey.REGISTER_MESSAGE);
        }
    }
}
