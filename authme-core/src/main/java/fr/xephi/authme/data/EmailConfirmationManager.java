package fr.xephi.authme.data;

import fr.xephi.authme.initialization.HasCleanup;
import fr.xephi.authme.initialization.SettingsDependent;
import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.EmailSettings;
import fr.xephi.authme.util.RandomStringUtils;
import fr.xephi.authme.util.expiring.ExpiringMap;

import javax.inject.Inject;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds the email addresses players have supplied but not yet confirmed with the code that was
 * mailed to them. An address only reaches the database once the matching code has been entered,
 * which proves that the player can read mail sent to it.
 */
public class EmailConfirmationManager implements SettingsDependent, HasCleanup {

    private static final int CODE_LENGTH = 6;

    private final EmailService emailService;
    private final ExpiringMap<String, PendingConfirmation> pendingConfirmations;

    private boolean requireConfirmation;
    private int expirationMinutes;
    private int maxTries;

    @Inject
    EmailConfirmationManager(Settings settings, EmailService emailService) {
        this.emailService = emailService;
        this.pendingConfirmations = new ExpiringMap<>(
            settings.getProperty(EmailSettings.CONFIRMATION_CODE_EXPIRATION_MINUTES), TimeUnit.MINUTES);
        reload(settings);
    }

    /**
     * Returns whether addresses must be confirmed before they are saved. Always false while the
     * mail settings are incomplete, since no code could be delivered in that case.
     *
     * @return true if a confirmation code is required, false otherwise
     */
    public boolean isConfirmationRequired() {
        return requireConfirmation && emailService.hasAllInformation();
    }

    /**
     * @return the number of minutes a confirmation code stays valid
     */
    public int getExpirationMinutes() {
        return expirationMinutes;
    }

    /**
     * Generates a code for the given address, remembers it as this player's pending address and
     * mails the code to it. Any address the player had pending before is replaced. Blocks while
     * the mail is being sent and must therefore be called from an asynchronous task.
     *
     * @param name the name of the player
     * @param email the address to confirm
     * @param oldEmail the address being replaced, or null if the player has none yet
     * @return true if the code could be mailed, false otherwise
     */
    public boolean createAndSendCode(String name, String email, String oldEmail) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        String code = RandomStringUtils.generateNum(CODE_LENGTH);
        pendingConfirmations.put(lowerName, new PendingConfirmation(email, oldEmail, code));

        if (emailService.sendEmailConfirmationMail(name, email, code)) {
            return true;
        }
        // Nothing was delivered, so the player could never confirm this entry
        pendingConfirmations.remove(lowerName);
        return false;
    }

    /**
     * Checks the given code against the player's pending address. The pending entry is consumed
     * when the code matches and when the player runs out of tries.
     *
     * @param name the name of the player
     * @param code the code the player supplied
     * @return the outcome, carrying the confirmed address upon success
     */
    public ConfirmationResult confirmCode(String name, String code) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        PendingConfirmation pending = pendingConfirmations.get(lowerName);
        if (pending == null) {
            return ConfirmationResult.noPendingRequest();
        }

        if (pending.code.equals(code)) {
            pendingConfirmations.remove(lowerName);
            return ConfirmationResult.success(pending.email, pending.oldEmail);
        }

        int triesLeft = maxTries - pending.failedAttempts.incrementAndGet();
        if (triesLeft <= 0) {
            pendingConfirmations.remove(lowerName);
            return ConfirmationResult.triesExceeded();
        }
        return ConfirmationResult.incorrectCode(triesLeft);
    }

    @Override
    public void reload(Settings settings) {
        requireConfirmation = settings.getProperty(EmailSettings.REQUIRE_CONFIRMATION);
        expirationMinutes = settings.getProperty(EmailSettings.CONFIRMATION_CODE_EXPIRATION_MINUTES);
        maxTries = Math.max(1, settings.getProperty(EmailSettings.CONFIRMATION_MAX_TRIES));
        pendingConfirmations.setExpiration(expirationMinutes, TimeUnit.MINUTES);
    }

    @Override
    public void performCleanup() {
        pendingConfirmations.removeExpiredEntries();
    }

    /** The outcome of checking a confirmation code. */
    public enum ConfirmationOutcome {

        /** The code matched; the address may be saved. */
        SUCCESS,

        /** The player has no address waiting to be confirmed, or it has expired. */
        NO_PENDING_REQUEST,

        /** The code did not match and the player has tries left. */
        INCORRECT_CODE,

        /** The code did not match and the pending address was discarded. */
        TRIES_EXCEEDED
    }

    /** The result of checking a confirmation code, with the details the caller needs to act on it. */
    public static final class ConfirmationResult {

        private final ConfirmationOutcome outcome;
        private final String email;
        private final String oldEmail;
        private final int triesLeft;

        private ConfirmationResult(ConfirmationOutcome outcome, String email, String oldEmail, int triesLeft) {
            this.outcome = outcome;
            this.email = email;
            this.oldEmail = oldEmail;
            this.triesLeft = triesLeft;
        }

        private static ConfirmationResult success(String email, String oldEmail) {
            return new ConfirmationResult(ConfirmationOutcome.SUCCESS, email, oldEmail, 0);
        }

        private static ConfirmationResult noPendingRequest() {
            return new ConfirmationResult(ConfirmationOutcome.NO_PENDING_REQUEST, null, null, 0);
        }

        private static ConfirmationResult incorrectCode(int triesLeft) {
            return new ConfirmationResult(ConfirmationOutcome.INCORRECT_CODE, null, null, triesLeft);
        }

        private static ConfirmationResult triesExceeded() {
            return new ConfirmationResult(ConfirmationOutcome.TRIES_EXCEEDED, null, null, 0);
        }

        public ConfirmationOutcome getOutcome() {
            return outcome;
        }

        /**
         * @return the confirmed address, only set for {@link ConfirmationOutcome#SUCCESS}
         */
        public String getEmail() {
            return email;
        }

        /**
         * @return the address being replaced, null when the player had none yet
         */
        public String getOldEmail() {
            return oldEmail;
        }

        /**
         * @return remaining tries, only meaningful for {@link ConfirmationOutcome#INCORRECT_CODE}
         */
        public int getTriesLeft() {
            return triesLeft;
        }
    }

    /** An address a player supplied, together with the code that was mailed to it. */
    private static final class PendingConfirmation {

        private final String email;
        private final String oldEmail;
        private final String code;
        private final AtomicInteger failedAttempts = new AtomicInteger();

        PendingConfirmation(String email, String oldEmail, String code) {
            this.email = email;
            this.oldEmail = oldEmail;
            this.code = code;
        }
    }
}
