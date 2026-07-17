package fr.xephi.authme.process.email;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.EmailConfirmationManager;
import fr.xephi.authme.data.EmailConfirmationManager.ConfirmationResult;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link AsyncConfirmEmail}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class AsyncConfirmEmailTest {

    @InjectMocks
    private AsyncConfirmEmail process;

    @Mock
    private Player player;

    @Mock
    private CommonService service;

    @Mock
    private PlayerCache playerCache;

    @Mock
    private DataSource dataSource;

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
    public void shouldSaveEmailOnCorrectCode() {
        // given
        String email = "new@mail.tld";
        PlayerAuth auth = givenAuthenticatedPlayer("Bobby", "bobby");
        ConfirmationResult confirmation = successResult(email, null);
        given(emailConfirmationManager.confirmCode("bobby", "123456")).willReturn(confirmation);
        given(validationService.isEmailFreeForRegistration(email, player)).willReturn(true);

        // when
        process.confirmEmail(player, "123456");

        // then
        verify(emailSaver).saveEmail(auth, player, null, email);
    }

    @Test
    public void shouldKeepOldEmailAsPreviousValueWhenChanging() {
        // given
        String oldEmail = "old@mail.tld";
        String newEmail = "new@mail.tld";
        PlayerAuth auth = givenAuthenticatedPlayer("Bobby", "bobby");
        ConfirmationResult confirmation = successResult(newEmail, oldEmail);
        given(emailConfirmationManager.confirmCode("bobby", "123456")).willReturn(confirmation);
        given(validationService.isEmailFreeForRegistration(newEmail, player)).willReturn(true);

        // when
        process.confirmEmail(player, "123456");

        // then
        verify(emailSaver).saveEmail(auth, player, oldEmail, newEmail);
    }

    @Test
    public void shouldRejectEmailTakenWhileAwaitingConfirmation() {
        // given
        String email = "new@mail.tld";
        givenAuthenticatedPlayer("Bobby", "bobby");
        ConfirmationResult confirmation = successResult(email, null);
        given(emailConfirmationManager.confirmCode("bobby", "123456")).willReturn(confirmation);
        given(validationService.isEmailFreeForRegistration(email, player)).willReturn(false);

        // when
        process.confirmEmail(player, "123456");

        // then
        verify(service).send(player, MessageKey.EMAIL_ALREADY_USED_ERROR);
        verify(emailSaver, never()).saveEmail(any(), any(), any(), anyString());
    }

    @Test
    public void shouldInformPlayerWithoutPendingRequest() {
        // given
        givenAuthenticatedPlayer("Bobby", "bobby");
        ConfirmationResult confirmation =
            resultWithOutcome(EmailConfirmationManager.ConfirmationOutcome.NO_PENDING_REQUEST, 0);
        given(emailConfirmationManager.confirmCode("bobby", "123456")).willReturn(confirmation);

        // when
        process.confirmEmail(player, "123456");

        // then
        verify(service).send(player, MessageKey.EMAIL_CONFIRMATION_NO_PENDING);
        verifyNoInteractions(emailSaver);
    }

    @Test
    public void shouldReportRemainingTriesOnIncorrectCode() {
        // given
        givenAuthenticatedPlayer("Bobby", "bobby");
        ConfirmationResult confirmation =
            resultWithOutcome(EmailConfirmationManager.ConfirmationOutcome.INCORRECT_CODE, 2);
        given(emailConfirmationManager.confirmCode("bobby", "000000")).willReturn(confirmation);

        // when
        process.confirmEmail(player, "000000");

        // then
        verify(service).send(player, MessageKey.EMAIL_CONFIRMATION_INCORRECT_CODE, "2");
        verifyNoInteractions(emailSaver);
    }

    @Test
    public void shouldInformPlayerWhenTriesAreExceeded() {
        // given
        givenAuthenticatedPlayer("Bobby", "bobby");
        ConfirmationResult confirmation =
            resultWithOutcome(EmailConfirmationManager.ConfirmationOutcome.TRIES_EXCEEDED, 0);
        given(emailConfirmationManager.confirmCode("bobby", "000000")).willReturn(confirmation);

        // when
        process.confirmEmail(player, "000000");

        // then
        verify(service).send(player, MessageKey.EMAIL_CONFIRMATION_TRIES_EXCEEDED);
        verifyNoInteractions(emailSaver);
    }

    @Test
    public void shouldShowLoginMessageForUnauthenticatedPlayer() {
        // given
        given(player.getName()).willReturn("Bobby");
        given(playerCache.isAuthenticated("bobby")).willReturn(false);
        given(dataSource.isAuthAvailable("Bobby")).willReturn(true);

        // when
        process.confirmEmail(player, "123456");

        // then
        verify(service).send(player, MessageKey.LOGIN_MESSAGE);
        verifyNoInteractions(emailConfirmationManager);
        verifyNoInteractions(emailSaver);
    }

    @Test
    public void shouldShowRegisterMessageForUnregisteredPlayer() {
        // given
        given(player.getName()).willReturn("Bobby");
        given(playerCache.isAuthenticated("bobby")).willReturn(false);
        given(dataSource.isAuthAvailable("Bobby")).willReturn(false);

        // when
        process.confirmEmail(player, "123456");

        // then
        verify(service).send(player, MessageKey.REGISTER_MESSAGE);
        verifyNoInteractions(emailConfirmationManager);
        verifyNoInteractions(emailSaver);
    }

    private PlayerAuth givenAuthenticatedPlayer(String name, String lowerName) {
        given(player.getName()).willReturn(name);
        given(playerCache.isAuthenticated(lowerName)).willReturn(true);
        PlayerAuth auth = mock(PlayerAuth.class);
        given(playerCache.getAuth(lowerName)).willReturn(auth);
        return auth;
    }

    private static ConfirmationResult successResult(String email, String oldEmail) {
        ConfirmationResult result = mock(ConfirmationResult.class);
        given(result.getOutcome()).willReturn(EmailConfirmationManager.ConfirmationOutcome.SUCCESS);
        given(result.getEmail()).willReturn(email);
        given(result.getOldEmail()).willReturn(oldEmail);
        return result;
    }

    private static ConfirmationResult resultWithOutcome(
            EmailConfirmationManager.ConfirmationOutcome outcome, int triesLeft) {
        ConfirmationResult result = mock(ConfirmationResult.class);
        given(result.getOutcome()).willReturn(outcome);
        given(result.getTriesLeft()).willReturn(triesLeft);
        return result;
    }
}
