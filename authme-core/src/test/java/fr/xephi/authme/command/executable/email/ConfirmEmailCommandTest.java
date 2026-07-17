package fr.xephi.authme.command.executable.email;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.process.Management;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link ConfirmEmailCommand}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class ConfirmEmailCommandTest {

    @InjectMocks
    private ConfirmEmailCommand command;

    @Mock
    private Management management;

    @Test
    public void shouldForwardCodeToManagement() {
        // given
        Player sender = mock(Player.class);

        // when
        command.runCommand(sender, Collections.singletonList("123456"));

        // then
        verify(management).performConfirmEmail(sender, "123456");
    }

    @Test
    public void shouldHaveUsageMessageForMissingCode() {
        // given / when / then
        assertThat(command.getArgumentsMismatchMessage(), equalTo(MessageKey.USAGE_CONFIRM_EMAIL));
    }
}
