package fr.xephi.authme.data;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.EmailConfirmationManager.ConfirmationOutcome;
import fr.xephi.authme.data.EmailConfirmationManager.ConfirmationResult;
import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.EmailSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link EmailConfirmationManager}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class EmailConfirmationManagerTest {

    @Mock
    private EmailService emailService;

    @BeforeAll
    public static void setUp() {
        TestHelper.setupLogger();
    }

    @Test
    public void shouldRequireConfirmationWhenEnabledAndMailIsConfigured() {
        // given
        EmailConfirmationManager manager = createManager(true, 15, 3);
        given(emailService.hasAllInformation()).willReturn(true);

        // when / then
        assertThat(manager.isConfirmationRequired(), equalTo(true));
    }

    @Test
    public void shouldNotRequireConfirmationWhenMailIsNotConfigured() {
        // given
        EmailConfirmationManager manager = createManager(true, 15, 3);
        given(emailService.hasAllInformation()).willReturn(false);

        // when / then
        assertThat(manager.isConfirmationRequired(), equalTo(false));
    }

    @Test
    public void shouldNotRequireConfirmationWhenDisabled() {
        // given
        EmailConfirmationManager manager = createManager(false, 15, 3);

        // when / then
        assertThat(manager.isConfirmationRequired(), equalTo(false));
    }

    @Test
    public void shouldMailSixDigitCodeAndConfirmIt() {
        // given
        EmailConfirmationManager manager = createManager(true, 15, 3);
        given(emailService.sendEmailConfirmationMail(anyString(), anyString(), anyString())).willReturn(true);

        // when
        boolean sent = manager.createAndSendCode("Bobby", "new@mail.tld", null);

        // then
        assertThat(sent, equalTo(true));
        String code = captureSentCode("Bobby", "new@mail.tld");
        assertThat(code, matchesPattern("\\d{6}"));

        ConfirmationResult result = manager.confirmCode("Bobby", code);
        assertThat(result.getOutcome(), equalTo(ConfirmationOutcome.SUCCESS));
        assertThat(result.getEmail(), equalTo("new@mail.tld"));
        assertThat(result.getOldEmail(), nullValue());
    }

    @Test
    public void shouldCarryOldEmailThroughConfirmation() {
        // given
        EmailConfirmationManager manager = createManager(true, 15, 3);
        given(emailService.sendEmailConfirmationMail(anyString(), anyString(), anyString())).willReturn(true);
        manager.createAndSendCode("Bobby", "new@mail.tld", "old@mail.tld");
        String code = captureSentCode("Bobby", "new@mail.tld");

        // when
        ConfirmationResult result = manager.confirmCode("Bobby", code);

        // then
        assertThat(result.getOutcome(), equalTo(ConfirmationOutcome.SUCCESS));
        assertThat(result.getOldEmail(), equalTo("old@mail.tld"));
    }

    @Test
    public void shouldNotKeepPendingEntryWhenMailCannotBeSent() {
        // given
        EmailConfirmationManager manager = createManager(true, 15, 3);
        given(emailService.sendEmailConfirmationMail(anyString(), anyString(), anyString())).willReturn(false);

        // when
        boolean sent = manager.createAndSendCode("Bobby", "new@mail.tld", null);

        // then
        assertThat(sent, equalTo(false));
        assertThat(manager.confirmCode("Bobby", captureSentCode("Bobby", "new@mail.tld")).getOutcome(),
            equalTo(ConfirmationOutcome.NO_PENDING_REQUEST));
    }

    @Test
    public void shouldReportNoPendingRequest() {
        // given
        EmailConfirmationManager manager = createManager(true, 15, 3);

        // when
        ConfirmationResult result = manager.confirmCode("Bobby", "123456");

        // then
        assertThat(result.getOutcome(), equalTo(ConfirmationOutcome.NO_PENDING_REQUEST));
    }

    @Test
    public void shouldCountDownTriesAndDiscardPendingEmail() {
        // given
        EmailConfirmationManager manager = createManager(true, 15, 3);
        given(emailService.sendEmailConfirmationMail(anyString(), anyString(), anyString())).willReturn(true);
        manager.createAndSendCode("Bobby", "new@mail.tld", null);
        String wrongCode = "000000".equals(captureSentCode("Bobby", "new@mail.tld")) ? "111111" : "000000";

        // when / then
        ConfirmationResult first = manager.confirmCode("Bobby", wrongCode);
        assertThat(first.getOutcome(), equalTo(ConfirmationOutcome.INCORRECT_CODE));
        assertThat(first.getTriesLeft(), equalTo(2));

        ConfirmationResult second = manager.confirmCode("Bobby", wrongCode);
        assertThat(second.getOutcome(), equalTo(ConfirmationOutcome.INCORRECT_CODE));
        assertThat(second.getTriesLeft(), equalTo(1));

        ConfirmationResult third = manager.confirmCode("Bobby", wrongCode);
        assertThat(third.getOutcome(), equalTo(ConfirmationOutcome.TRIES_EXCEEDED));

        // the pending address is gone, so the real code no longer works either
        assertThat(manager.confirmCode("Bobby", captureSentCode("Bobby", "new@mail.tld")).getOutcome(),
            equalTo(ConfirmationOutcome.NO_PENDING_REQUEST));
    }

    @Test
    public void shouldNotBeCaseSensitiveWithPlayerName() {
        // given
        EmailConfirmationManager manager = createManager(true, 15, 3);
        given(emailService.sendEmailConfirmationMail(anyString(), anyString(), anyString())).willReturn(true);
        manager.createAndSendCode("BoBBy", "new@mail.tld", null);
        String code = captureSentCode("BoBBy", "new@mail.tld");

        // when
        ConfirmationResult result = manager.confirmCode("bobby", code);

        // then
        assertThat(result.getOutcome(), equalTo(ConfirmationOutcome.SUCCESS));
    }

    private String captureSentCode(String name, String email) {
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailConfirmationMail(eq(name), eq(email), codeCaptor.capture());
        return codeCaptor.getValue();
    }

    private EmailConfirmationManager createManager(boolean requireConfirmation, int expirationMinutes,
                                                   int maxTries) {
        Settings settings = mock(Settings.class);
        given(settings.getProperty(EmailSettings.REQUIRE_CONFIRMATION)).willReturn(requireConfirmation);
        given(settings.getProperty(EmailSettings.CONFIRMATION_CODE_EXPIRATION_MINUTES))
            .willReturn(expirationMinutes);
        given(settings.getProperty(EmailSettings.CONFIRMATION_MAX_TRIES)).willReturn(maxTries);
        return new EmailConfirmationManager(settings, emailService);
    }
}
