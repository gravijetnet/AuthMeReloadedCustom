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

/**
 * Test for {@link AsyncAddEmail}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class AsyncAddEmailTest {

    @InjectMocks
    private AsyncAddEmail asyncAddEmail;

    @Mock
    private Player player;

    @Mock
    private DataSource dataSource;

    @Mock
    private PlayerCache playerCache;

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
    public void shouldAddEmailWhenNoConfirmationIsRequired() {
        // given
        String email = "my.mail@example.org";
        PlayerAuth auth = givenAuthenticatedPlayerWithoutEmail("testEr", "tester");
        given(validationService.validateEmail(email)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(email, player)).willReturn(true);
        given(emailConfirmationManager.isConfirmationRequired()).willReturn(false);

        // when
        asyncAddEmail.addEmail(player, email);

        // then
        verify(emailSaver).saveEmail(auth, player, null, email);
        verifyNoInteractions(dataSource);
    }

    @Test
    public void shouldRequestConfirmationBeforeSavingEmail() {
        // given
        String email = "my.mail@example.org";
        givenAuthenticatedPlayerWithoutEmail("testEr", "tester");
        given(validationService.validateEmail(email)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(email, player)).willReturn(true);
        given(emailConfirmationManager.isConfirmationRequired()).willReturn(true);
        given(emailConfirmationManager.getExpirationMinutes()).willReturn(15);
        given(emailConfirmationManager.createAndSendCode("testEr", email, null)).willReturn(true);

        // when
        asyncAddEmail.addEmail(player, email);

        // then
        verify(service).send(player, MessageKey.EMAIL_CONFIRMATION_SENT, email, "15");
        verify(emailSaver, never()).saveEmail(any(), any(), any(), anyString());
    }

    @Test
    public void shouldShowErrorWhenConfirmationCodeCannotBeSent() {
        // given
        String email = "my.mail@example.org";
        givenAuthenticatedPlayerWithoutEmail("testEr", "tester");
        given(validationService.validateEmail(email)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(email, player)).willReturn(true);
        given(emailConfirmationManager.isConfirmationRequired()).willReturn(true);
        given(emailConfirmationManager.createAndSendCode("testEr", email, null)).willReturn(false);

        // when
        asyncAddEmail.addEmail(player, email);

        // then
        verify(service).send(player, MessageKey.EMAIL_SEND_FAILURE);
        verify(emailSaver, never()).saveEmail(any(), any(), any(), anyString());
    }

    @Test
    public void shouldNotAddMailIfPlayerAlreadyHasEmail() {
        // given
        given(player.getName()).willReturn("my_Player");
        given(playerCache.isAuthenticated("my_player")).willReturn(true);
        PlayerAuth auth = mock(PlayerAuth.class);
        given(auth.getEmail()).willReturn("another@mail.tld");
        given(playerCache.getAuth("my_player")).willReturn(auth);

        // when
        asyncAddEmail.addEmail(player, "some.mail@example.org");

        // then
        verify(service).send(player, MessageKey.USAGE_CHANGE_EMAIL);
        verifyNoInteractions(emailSaver);
    }

    @Test
    public void shouldNotAddMailIfItIsInvalid() {
        // given
        String email = "invalid_mail";
        givenAuthenticatedPlayerWithoutEmail("my_Player", "my_player");
        given(validationService.validateEmail(email)).willReturn(false);

        // when
        asyncAddEmail.addEmail(player, email);

        // then
        verify(service).send(player, MessageKey.INVALID_EMAIL);
        verifyNoInteractions(emailSaver);
    }

    @Test
    public void shouldNotAddMailIfAlreadyUsed() {
        // given
        String email = "player@mail.tld";
        givenAuthenticatedPlayerWithoutEmail("TestName", "testname");
        given(validationService.validateEmail(email)).willReturn(true);
        given(validationService.isEmailFreeForRegistration(email, player)).willReturn(false);

        // when
        asyncAddEmail.addEmail(player, email);

        // then
        verify(service).send(player, MessageKey.EMAIL_ALREADY_USED_ERROR);
        verifyNoInteractions(emailSaver);
    }

    @Test
    public void shouldShowLoginMessage() {
        // given
        given(player.getName()).willReturn("Username12");
        given(playerCache.isAuthenticated("username12")).willReturn(false);
        given(dataSource.isAuthAvailable("Username12")).willReturn(true);

        // when
        asyncAddEmail.addEmail(player, "test@mail.com");

        // then
        verify(service).send(player, MessageKey.LOGIN_MESSAGE);
        verifyNoInteractions(emailSaver);
    }

    @Test
    public void shouldShowRegisterMessage() {
        // given
        given(player.getName()).willReturn("user");
        given(playerCache.isAuthenticated("user")).willReturn(false);
        given(dataSource.isAuthAvailable("user")).willReturn(false);

        // when
        asyncAddEmail.addEmail(player, "test@mail.com");

        // then
        verify(service).send(player, MessageKey.REGISTER_MESSAGE);
        verifyNoInteractions(emailSaver);
    }

    private PlayerAuth givenAuthenticatedPlayerWithoutEmail(String name, String lowerName) {
        given(player.getName()).willReturn(name);
        given(playerCache.isAuthenticated(lowerName)).willReturn(true);
        PlayerAuth auth = mock(PlayerAuth.class);
        given(auth.getEmail()).willReturn(null);
        given(playerCache.getAuth(lowerName)).willReturn(auth);
        return auth;
    }

}
