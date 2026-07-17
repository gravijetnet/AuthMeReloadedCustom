package fr.xephi.authme.process.email;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.EmailConfirmationManager;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test for {@link AsyncChangeEmail}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class AsyncChangeEmailTest {

    @InjectMocks
    private AsyncChangeEmail process;

    @Mock
    private Player player;

    @Mock
    private PlayerCache playerCache;

    @Mock
    private DataSource dataSource;

    @Mock
    private CommonService service;

    @Mock
    private ValidationService validationService;

    @Mock
    private EmailConfirmationManager emailConfirmationManager;

    @Mock
    private EmailSaver emailSaver;

    @BeforeAll
    public static void setUp() {
        TestHelper.setupLogger();
    }

    @Test
    public void shouldChangeEmailWhenNoConfirmationIsRequired() {
        // given
        String newEmail = "new@mail.tld";
        given(player.getName()).willReturn("Bobby");
        given(playerCache.isAuthenticated("bobby")).willReturn(true);
        PlayerAuth auth = authWithMail("old@mail.tld");
        given(playerCache.getAuth("bobby")).willReturn(auth);
        given(validationService.validateEmail(newEmail)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(newEmail, player)).willReturn(true);
        given(emailConfirmationManager.isConfirmationRequired()).willReturn(false);

        // when
        process.changeEmail(player, "old@mail.tld", newEmail);

        // then
        verify(emailSaver).saveEmail(auth, player, "old@mail.tld", newEmail);
    }

    @Test
    public void shouldNotBeCaseSensitiveWhenComparingEmails() {
        // given
        String newEmail = "newmail@example.com";
        given(player.getName()).willReturn("Debra");
        given(playerCache.isAuthenticated("debra")).willReturn(true);
        String oldEmail = "OLD-mail@example.org";
        PlayerAuth auth = authWithMail(oldEmail);
        given(playerCache.getAuth("debra")).willReturn(auth);
        given(validationService.validateEmail(newEmail)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(newEmail, player)).willReturn(true);
        given(emailConfirmationManager.isConfirmationRequired()).willReturn(false);

        // when
        process.changeEmail(player, "old-mail@example.org", newEmail);

        // then
        verify(emailSaver).saveEmail(auth, player, "old-mail@example.org", newEmail);
    }

    @Test
    public void shouldSendConfirmationCodeToNewAddress() {
        // given
        String newEmail = "new@mail.tld";
        String oldEmail = "old@mail.tld";
        PlayerAuth auth = authWithMail(oldEmail);
        given(player.getName()).willReturn("Bobby");
        given(playerCache.isAuthenticated("bobby")).willReturn(true);
        given(playerCache.getAuth("bobby")).willReturn(auth);
        given(validationService.validateEmail(newEmail)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(newEmail, player)).willReturn(true);
        given(emailConfirmationManager.isConfirmationRequired()).willReturn(true);
        given(emailConfirmationManager.getExpirationMinutes()).willReturn(15);
        given(emailConfirmationManager.createAndSendCode("Bobby", newEmail, oldEmail)).willReturn(true);

        // when
        process.changeEmail(player, oldEmail, newEmail);

        // then
        verify(service).send(player, MessageKey.EMAIL_CONFIRMATION_SENT, newEmail, "15");
        verify(emailSaver, never()).saveEmail(any(), any(), any(), anyString());
    }

    @Test
    public void shouldShowErrorWhenConfirmationCodeCannotBeSent() {
        // given
        String newEmail = "new@mail.tld";
        String oldEmail = "old@mail.tld";
        PlayerAuth auth = authWithMail(oldEmail);
        given(player.getName()).willReturn("Bobby");
        given(playerCache.isAuthenticated("bobby")).willReturn(true);
        given(playerCache.getAuth("bobby")).willReturn(auth);
        given(validationService.validateEmail(newEmail)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(newEmail, player)).willReturn(true);
        given(emailConfirmationManager.isConfirmationRequired()).willReturn(true);
        given(emailConfirmationManager.createAndSendCode("Bobby", newEmail, oldEmail)).willReturn(false);

        // when
        process.changeEmail(player, oldEmail, newEmail);

        // then
        verify(service).send(player, MessageKey.EMAIL_SEND_FAILURE);
        verify(emailSaver, never()).saveEmail(any(), any(), any(), anyString());
    }

    @Test
    public void shouldShowAddEmailUsage() {
        // given
        given(player.getName()).willReturn("Bobby");
        given(playerCache.isAuthenticated("bobby")).willReturn(true);
        PlayerAuth auth = authWithMail(null);
        given(playerCache.getAuth("bobby")).willReturn(auth);

        // when
        process.changeEmail(player, "old@mail.tld", "new@mailt.tld");

        // then
        verify(service).send(player, MessageKey.USAGE_ADD_EMAIL);
        verifyNoInteractions(emailSaver);
    }

    @Test
    public void shouldRejectInvalidNewMail() {
        // given
        String newEmail = "bogus";
        given(player.getName()).willReturn("Bobby");
        given(playerCache.isAuthenticated("bobby")).willReturn(true);
        PlayerAuth auth = authWithMail("old@mail.tld");
        given(playerCache.getAuth("bobby")).willReturn(auth);
        given(validationService.validateEmail(newEmail)).willReturn(false);

        // when
        process.changeEmail(player, "old@mail.tld", newEmail);

        // then
        verify(service).send(player, MessageKey.INVALID_NEW_EMAIL);
        verifyNoInteractions(emailSaver);
    }

    @Test
    public void shouldRejectInvalidOldEmail() {
        // given
        String newEmail = "new@mail.tld";
        given(player.getName()).willReturn("Bobby");
        given(playerCache.isAuthenticated("bobby")).willReturn(true);
        PlayerAuth auth = authWithMail("other@address.email");
        given(playerCache.getAuth("bobby")).willReturn(auth);
        given(validationService.validateEmail(newEmail)).willReturn(true);

        // when
        process.changeEmail(player, "old@mail.tld", newEmail);

        // then
        verify(service).send(player, MessageKey.INVALID_OLD_EMAIL);
        verifyNoInteractions(emailSaver);
    }

    @Test
    public void shouldRejectAlreadyUsedEmail() {
        // given
        String newEmail = "new@example.com";
        given(player.getName()).willReturn("Username");
        given(playerCache.isAuthenticated("username")).willReturn(true);
        PlayerAuth auth = authWithMail("old@example.com");
        given(playerCache.getAuth("username")).willReturn(auth);
        given(validationService.validateEmail(newEmail)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(newEmail, player)).willReturn(false);

        // when
        process.changeEmail(player, "old@example.com", newEmail);

        // then
        verify(service).send(player, MessageKey.EMAIL_ALREADY_USED_ERROR);
        verifyNoInteractions(emailSaver);
    }

    @Test
    public void shouldSendLoginMessage() {
        // given
        given(player.getName()).willReturn("Bobby");
        given(playerCache.isAuthenticated("bobby")).willReturn(false);
        given(dataSource.isAuthAvailable("Bobby")).willReturn(true);

        // when
        process.changeEmail(player, "old@mail.tld", "new@mail.tld");

        // then
        verify(service).send(player, MessageKey.LOGIN_MESSAGE);
        verifyNoInteractions(emailSaver);
    }

    @Test
    public void shouldShowRegistrationMessage() {
        // given
        given(player.getName()).willReturn("Bobby");
        given(playerCache.isAuthenticated("bobby")).willReturn(false);
        given(dataSource.isAuthAvailable("Bobby")).willReturn(false);

        // when
        process.changeEmail(player, "old@mail.tld", "new@mail.tld");

        // then
        verify(service).send(player, MessageKey.REGISTER_MESSAGE);
        verifyNoInteractions(emailSaver);
    }

    private static PlayerAuth authWithMail(String email) {
        PlayerAuth auth = mock(PlayerAuth.class);
        when(auth.getEmail()).thenReturn(email);
        return auth;
    }

}
