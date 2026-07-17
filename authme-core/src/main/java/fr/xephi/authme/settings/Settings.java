package fr.xephi.authme.settings;

import ch.jalu.configme.SettingsManagerImpl;
import ch.jalu.configme.configurationdata.ConfigurationData;
import ch.jalu.configme.migration.MigrationService;
import ch.jalu.configme.resource.PropertyResource;
import com.google.common.io.Files;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static fr.xephi.authme.util.FileUtils.copyFileFromResource;

/**
 * The AuthMe settings manager.
 */
public class Settings extends SettingsManagerImpl {

    /**
     * Version of the email templates bundled with this AuthMe build. A template in the plugin
     * folder whose marker is absent or lower is backed up and replaced on startup.
     */
    static final int EMAIL_TEMPLATE_VERSION = 3;

    /** Marker to put on templates which are written outside of the JAR, so they are not replaced. */
    static final String EMAIL_TEMPLATE_VERSION_MARKER =
        "<!-- authme-template-version: " + EMAIL_TEMPLATE_VERSION + " -->";

    private static final Pattern EMAIL_TEMPLATE_VERSION_PATTERN =
        Pattern.compile("authme-template-version:\\s*(\\d+)");

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(Settings.class);
    private final File pluginFolder;
    private String passwordEmailMessage;
    private String verificationEmailMessage;
    private String recoveryCodeEmailMessage;
    private String emailConfirmationMessage;

    /**
     * Constructor.
     *
     * @param pluginFolder the AuthMe plugin folder
     * @param resource the property resource to read and write properties to
     * @param migrationService migration service to check the settings file with
     * @param configurationData configuration data (properties and comments)
     */
    public Settings(File pluginFolder, PropertyResource resource, MigrationService migrationService,
                    ConfigurationData configurationData) {
        super(resource, configurationData, migrationService);
        this.pluginFolder = pluginFolder;
        loadSettingsFromFiles();
    }

    /**
     * Return the text to use in email registrations.
     *
     * @return The email message
     */
    public String getPasswordEmailMessage() {
        return passwordEmailMessage;
    }

    /**
     * Return the text for verification emails (before sensitive commands can be used).
     *
     * @return The email message
     */
    public String getVerificationEmailMessage() {
        return verificationEmailMessage;
    }

    /**
     * Return the text to use when someone requests to receive a recovery code.
     *
     * @return The email message
     */
    public String getRecoveryCodeEmailMessage() {
        return recoveryCodeEmailMessage;
    }

    /**
     * Return the text to use when someone must confirm an email address they supplied.
     *
     * @return The email message
     */
    public String getEmailConfirmationMessage() {
        return emailConfirmationMessage;
    }

    private void loadSettingsFromFiles() {
        passwordEmailMessage = readEmailTemplate("email.html");
        verificationEmailMessage = readEmailTemplate("verification_code_email.html");
        recoveryCodeEmailMessage = readEmailTemplate("recovery_code_email.html");
        emailConfirmationMessage = readEmailTemplate("email_confirmation.html");
    }

    @Override
    public void reload() {
        super.reload();
        loadSettingsFromFiles();
    }

    /**
     * Reads an email template from the plugin folder. A template left over from an older AuthMe
     * version is moved aside first so that the current one is written in its place.
     *
     * @param filename the template to read
     * @return the template's contents
     */
    private String readEmailTemplate(String filename) {
        final File file = new File(pluginFolder, filename);
        if (file.exists() && isOutdatedEmailTemplate(file)) {
            backUpOutdatedEmailTemplate(file, filename);
        }
        return readFile(filename);
    }

    /**
     * Returns whether the given template predates the bundled one. A template that cannot be read
     * counts as up-to-date so that an unreadable file is never discarded.
     *
     * @param file the template in the plugin folder
     * @return true if the file should be replaced, false otherwise
     */
    private boolean isOutdatedEmailTemplate(File file) {
        final String contents;
        try {
            contents = Files.asCharSource(file, StandardCharsets.UTF_8).read();
        } catch (IOException e) {
            logger.logException("Failed to read email template '" + file.getName()
                + "' to check its version; keeping it as is:", e);
            return false;
        }

        Matcher matcher = EMAIL_TEMPLATE_VERSION_PATTERN.matcher(contents);
        if (!matcher.find()) {
            return true;
        }
        try {
            return Integer.parseInt(matcher.group(1)) < EMAIL_TEMPLATE_VERSION;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private void backUpOutdatedEmailTemplate(File file, String filename) {
        File backup = new File(FileUtils.createBackupFilePath(file));
        try {
            Files.copy(file, backup);
        } catch (IOException e) {
            logger.logException("Failed to back up email template '" + filename
                + "'; keeping it as is:", e);
            return;
        }
        if (!file.delete()) {
            logger.warning("Could not delete outdated email template '" + filename
                + "'; it will keep being used. Delete it manually to get the current design.");
            FileUtils.delete(backup);
            return;
        }
        logger.info("Replaced email template '" + filename + "' with the current version; "
            + "your previous file was kept as '" + backup.getName() + "'");
    }

    /**
     * Reads a file from the plugin folder or copies it from the JAR to the plugin folder.
     *
     * @param filename the file to read
     * @return the file's contents
     */
    private String readFile(String filename) {
        final File file = new File(pluginFolder, filename);
        if (copyFileFromResource(file, filename)) {
            try {
                return Files.asCharSource(file, StandardCharsets.UTF_8).read();
            } catch (IOException e) {
                logger.logException("Failed to read file '" + filename + "':", e);
            }
        } else {
            logger.warning("Failed to copy file '" + filename + "' from JAR");
        }
        return "";
    }
}
