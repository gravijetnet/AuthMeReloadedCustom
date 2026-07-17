package fr.xephi.authme.process.email;

import fr.xephi.authme.data.EmailConfirmationManager;
import fr.xephi.authme.data.EmailConfirmationManager.ConfirmationResult;
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
 * Async task to confirm an email address the player supplied earlier, using the code that was
 * mailed to that address.
 */
public class AsyncConfirmEmail implements AsynchronousProcess {

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

    AsyncConfirmEmail() {
    }

    /**
     * Handles the request to confirm the player's pending email address.
     *
     * @param player the player confirming their address
     * @param code the code the player supplied
     */
    public void confirmEmail(Player player, String code) {
        String playerName = PlayerUtils.getName(player).toLowerCase(Locale.ROOT);

        if (!playerCache.isAuthenticated(playerName)) {
            outputUnloggedMessage(player);
            return;
        }

        ConfirmationResult result = emailConfirmationManager.confirmCode(playerName, code);
        switch (result.getOutcome()) {
            case SUCCESS ->
                saveConfirmedEmail(player, playerCache.getAuth(playerName), result);
            case NO_PENDING_REQUEST ->
                service.send(player, MessageKey.EMAIL_CONFIRMATION_NO_PENDING);
            case INCORRECT_CODE ->
                service.send(player, MessageKey.EMAIL_CONFIRMATION_INCORRECT_CODE,
                    String.valueOf(result.getTriesLeft()));
            case TRIES_EXCEEDED ->
                service.send(player, MessageKey.EMAIL_CONFIRMATION_TRIES_EXCEEDED);
            default ->
                throw new IllegalStateException("Unknown confirmation outcome: " + result.getOutcome());
        }
    }

    /**
     * Saves the address now that the player has proven they can read mail sent to it.
     *
     * @param player the player
     * @param auth the player's auth object
     * @param result the successful confirmation, holding the address to save
     */
    private void saveConfirmedEmail(Player player, PlayerAuth auth, ConfirmationResult result) {
        // The address was free when it was supplied, but someone may have taken it since
        if (!validationService.isEmailFreeForRegistration(result.getEmail(), player)) {
            service.send(player, MessageKey.EMAIL_ALREADY_USED_ERROR);
            return;
        }
        emailSaver.saveEmail(auth, player, result.getOldEmail(), result.getEmail());
    }

    private void outputUnloggedMessage(Player player) {
        if (dataSource.isAuthAvailable(PlayerUtils.getName(player))) {
            service.send(player, MessageKey.LOGIN_MESSAGE);
        } else {
            service.send(player, MessageKey.REGISTER_MESSAGE);
        }
    }
}
