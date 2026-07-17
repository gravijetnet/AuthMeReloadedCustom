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
import fr.xephi.authme.util.Utils;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.Locale;

/**
 * Async task to add an email to an account.
 */
public class AsyncAddEmail implements AsynchronousProcess {

    @Inject
    private CommonService service;

    @Inject
    private DataSource dataSource;

    @Inject
    private PlayerCache playerCache;

    @Inject
    private ValidationService validationService;

    @Inject
    private EmailConfirmationManager emailConfirmationManager;

    @Inject
    private EmailSaver emailSaver;

    AsyncAddEmail() {
    }

    /**
     * Handles the request to add the given email to the player's account.
     *
     * @param player the player to add the email to
     * @param email the email to add
     */
    public void addEmail(Player player, String email) {
        String playerName = PlayerUtils.getName(player).toLowerCase(Locale.ROOT);

        if (!playerCache.isAuthenticated(playerName)) {
            sendUnloggedMessage(player);
            return;
        }

        PlayerAuth auth = playerCache.getAuth(playerName);
        String currentEmail = auth.getEmail();

        if (!Utils.isEmailEmpty(currentEmail)) {
            service.send(player, MessageKey.USAGE_CHANGE_EMAIL);
        } else if (!validationService.validateEmail(email)) {
            service.send(player, MessageKey.INVALID_EMAIL);
        } else if (!validationService.isEmailFreeForRegistration(email, player)) {
            service.send(player, MessageKey.EMAIL_ALREADY_USED_ERROR);
        } else if (emailConfirmationManager.isConfirmationRequired()) {
            requestConfirmation(player, email);
        } else {
            emailSaver.saveEmail(auth, player, null, email);
        }
    }

    /**
     * Mails a code to the given address; the player must send it back before the address is saved.
     *
     * @param player the player who supplied the address
     * @param email the address to confirm
     */
    private void requestConfirmation(Player player, String email) {
        String name = PlayerUtils.getName(player);
        if (emailConfirmationManager.createAndSendCode(name, email, null)) {
            service.send(player, MessageKey.EMAIL_CONFIRMATION_SENT,
                email, String.valueOf(emailConfirmationManager.getExpirationMinutes()));
        } else {
            service.send(player, MessageKey.EMAIL_SEND_FAILURE);
        }
    }

    private void sendUnloggedMessage(Player player) {
        if (dataSource.isAuthAvailable(PlayerUtils.getName(player))) {
            service.send(player, MessageKey.LOGIN_MESSAGE);
        } else {
            service.send(player, MessageKey.REGISTER_MESSAGE);
        }
    }

}
