package fr.xephi.authme.process.email;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.events.EmailChangedEvent;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link EmailSaver}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class EmailSaverTest {

    @InjectMocks
    private EmailSaver emailSaver;

    @Mock
    private Player player;

    @Mock
    private CommonService service;

    @Mock
    private DataSource dataSource;

    @Mock
    private PlayerCache playerCache;

    @Mock
    private BukkitService bukkitService;

    @BeforeAll
    public static void setUp() {
        TestHelper.setupLogger();
    }

    @Test
    public void shouldSaveNewEmailAndReportItAsAdded() {
        // given
        String email = "new@mail.tld";
        PlayerAuth auth = mock(PlayerAuth.class);
        givenEventIsFired(null, email, false);
        given(dataSource.updateEmail(auth)).willReturn(true);

        // when
        boolean result = emailSaver.saveEmail(auth, player, null, email);

        // then
        assertThat(result, equalTo(true));
        verify(auth).setEmail(email);
        verify(dataSource).updateEmail(auth);
        verify(playerCache).updatePlayer(auth);
        verify(service).send(player, MessageKey.EMAIL_ADDED_SUCCESS);
    }

    @Test
    public void shouldReportReplacedEmailAsChanged() {
        // given
        String oldEmail = "old@mail.tld";
        String newEmail = "new@mail.tld";
        PlayerAuth auth = mock(PlayerAuth.class);
        givenEventIsFired(oldEmail, newEmail, false);
        given(dataSource.updateEmail(auth)).willReturn(true);

        // when
        boolean result = emailSaver.saveEmail(auth, player, oldEmail, newEmail);

        // then
        assertThat(result, equalTo(true));
        verify(service).send(player, MessageKey.EMAIL_CHANGED_SUCCESS);
    }

    @Test
    public void shouldReturnErrorWhenEmailCannotBeSaved() {
        // given
        String email = "new@mail.tld";
        PlayerAuth auth = mock(PlayerAuth.class);
        givenEventIsFired(null, email, false);
        given(dataSource.updateEmail(auth)).willReturn(false);

        // when
        boolean result = emailSaver.saveEmail(auth, player, null, email);

        // then
        assertThat(result, equalTo(false));
        verify(playerCache, never()).updatePlayer(any(PlayerAuth.class));
        verify(service).send(player, MessageKey.ERROR);
    }

    @Test
    public void shouldNotAddOnCancelledEvent() {
        // given
        String email = "new@mail.tld";
        PlayerAuth auth = mock(PlayerAuth.class);
        givenEventIsFired(null, email, true);

        // when
        boolean result = emailSaver.saveEmail(auth, player, null, email);

        // then
        assertThat(result, equalTo(false));
        verify(dataSource, never()).updateEmail(any(PlayerAuth.class));
        verify(service).send(player, MessageKey.EMAIL_ADD_NOT_ALLOWED);
    }

    @Test
    public void shouldNotChangeOnCancelledEvent() {
        // given
        String oldEmail = "old@mail.tld";
        String newEmail = "new@mail.tld";
        PlayerAuth auth = mock(PlayerAuth.class);
        givenEventIsFired(oldEmail, newEmail, true);

        // when
        boolean result = emailSaver.saveEmail(auth, player, oldEmail, newEmail);

        // then
        assertThat(result, equalTo(false));
        verify(dataSource, never()).updateEmail(any(PlayerAuth.class));
        verify(service).send(player, MessageKey.EMAIL_CHANGE_NOT_ALLOWED);
    }

    @SuppressWarnings("unchecked")
    private void givenEventIsFired(String oldEmail, String newEmail, boolean isCancelled) {
        EmailChangedEvent event = spy(new EmailChangedEvent(player, oldEmail, newEmail, false));
        event.setCancelled(isCancelled);
        given(bukkitService.createAndCallEvent(any(Function.class))).willReturn(event);
    }
}
