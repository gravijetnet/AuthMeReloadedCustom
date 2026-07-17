package fr.xephi.authme.settings;

import ch.jalu.configme.configurationdata.ConfigurationData;
import ch.jalu.configme.configurationdata.ConfigurationDataBuilder;
import ch.jalu.configme.resource.PropertyReader;
import ch.jalu.configme.resource.PropertyResource;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.settings.properties.TestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import fr.xephi.authme.TempFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link Settings}.
 */
public class SettingsTest {

    private static final ConfigurationData CONFIG_DATA =
        ConfigurationDataBuilder.createConfiguration(TestConfiguration.class);
    public TempFolder temporaryFolder = new TempFolder();
    private File testPluginFolder;

    @BeforeAll
    public static void setUpLogger() {
        TestHelper.setupLogger();
    }

    @BeforeEach
    public void setUpTestPluginFolder() throws IOException {
        testPluginFolder = temporaryFolder.newFolder();
    }

    @Test
    public void shouldLoadEmailMessage() throws IOException {
        // given
        String emailMessage = withCurrentVersion("Sample email message\nThat's all!");
        writeTemplate("email.html", emailMessage);

        PropertyResource resource = mockPropertyResourceAndReader();
        Settings settings = new Settings(testPluginFolder, resource, null, CONFIG_DATA);

        // when
        String result = settings.getPasswordEmailMessage();

        // then
        assertThat(result, equalTo(emailMessage));
    }

    @Test
    public void shouldLoadRecoveryCodeMessage() throws IOException {
        // given
        String emailMessage = withCurrentVersion("Your recovery code is %code.");
        writeTemplate("recovery_code_email.html", emailMessage);

        PropertyResource resource = mockPropertyResourceAndReader();
        Settings settings = new Settings(testPluginFolder, resource, null, CONFIG_DATA);

        // when
        String result = settings.getRecoveryCodeEmailMessage();

        // then
        assertThat(result, equalTo(emailMessage));
    }

    @Test
    public void shouldLoadVerificationMessage() throws IOException {
        // given
        String emailMessage = withCurrentVersion("Please verify your identity with <recoverycode />.");
        writeTemplate("verification_code_email.html", emailMessage);

        PropertyResource resource = mockPropertyResourceAndReader();
        Settings settings = new Settings(testPluginFolder, resource, null, CONFIG_DATA);

        // when
        String result = settings.getVerificationEmailMessage();

        // then
        assertThat(result, equalTo(emailMessage));
    }

    @Test
    public void shouldLoadEmailConfirmationMessage() throws IOException {
        // given
        String emailMessage = withCurrentVersion("Confirm with <confirmationcode />.");
        writeTemplate("email_confirmation.html", emailMessage);

        PropertyResource resource = mockPropertyResourceAndReader();
        Settings settings = new Settings(testPluginFolder, resource, null, CONFIG_DATA);

        // when
        String result = settings.getEmailConfirmationMessage();

        // then
        assertThat(result, equalTo(emailMessage));
    }

    @Test
    public void shouldReplaceTemplateWithoutVersionMarkerAndKeepBackup() throws IOException {
        // given
        String outdatedTemplate = "Dear <playername />, this is the old design.";
        writeTemplate("email.html", outdatedTemplate);
        PropertyResource resource = mockPropertyResourceAndReader();

        // when
        Settings settings = new Settings(testPluginFolder, resource, null, CONFIG_DATA);

        // then
        assertThat(settings.getPasswordEmailMessage(),
            containsString(Settings.EMAIL_TEMPLATE_VERSION_MARKER));
        assertThat(settings.getPasswordEmailMessage(), not(containsString("the old design")));

        File[] backups = testPluginFolder.listFiles((dir, name) -> name.startsWith("backup_email_"));
        assertThat(backups, arrayWithSize(1));
        assertThat(readFile(backups[0]), equalTo(outdatedTemplate));
    }

    @Test
    public void shouldKeepTemplateWithNewerVersionMarker() throws IOException {
        // given
        String customTemplate = "<!-- authme-template-version: 99 -->\nMy own design.";
        writeTemplate("email.html", customTemplate);
        PropertyResource resource = mockPropertyResourceAndReader();

        // when
        Settings settings = new Settings(testPluginFolder, resource, null, CONFIG_DATA);

        // then
        assertThat(settings.getPasswordEmailMessage(), equalTo(customTemplate));
        assertThat(testPluginFolder.listFiles((dir, name) -> name.startsWith("backup_")),
            arrayWithSize(0));
    }

    private static String withCurrentVersion(String template) {
        return Settings.EMAIL_TEMPLATE_VERSION_MARKER + "\n" + template;
    }

    private void writeTemplate(String filename, String contents) throws IOException {
        File file = new File(testPluginFolder, filename);
        createFile(file);
        Files.write(file.toPath(), contents.getBytes(StandardCharsets.UTF_8));
    }

    private static String readFile(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static PropertyResource mockPropertyResourceAndReader() {
        PropertyReader reader = mock(PropertyReader.class, RETURNS_DEEP_STUBS);
        given(reader.getList(anyString())).willReturn(Collections.emptyList());
        PropertyResource resource = mock(PropertyResource.class);
        given(resource.createReader()).willReturn(reader);
        return resource;
    }

    private static void createFile(File file) {
        try {
            file.getParentFile().mkdirs();
            file.createNewFile();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
