package fr.xephi.authme.service;

import fr.xephi.authme.TestHelper;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link RealNameService}.
 */
@ExtendWith(MockitoExtension.class)
class RealNameServiceTest {

    @InjectMocks
    private RealNameService realNameService;

    @Mock
    private PhoenixHook phoenixHook;

    @BeforeAll
    static void setupLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldReturnNameThePlayerConnectedWith() {
        // given
        UUID uuid = UUID.randomUUID();
        Player player = mockPlayer(uuid, "Bobby");
        realNameService.rememberConnectingPlayer(uuid, "Bobby");

        // when
        String result = realNameService.getRealName(player);

        // then
        assertThat(result, equalTo("Bobby"));
        verify(phoenixHook, never()).getRealName(player);
    }

    @Test
    void shouldReturnConnectNameForDisguisedPlayer() {
        // given
        UUID uuid = UUID.randomUUID();
        realNameService.rememberConnectingPlayer(uuid, "Bobby");
        // the player got disguised after joining, so Bukkit reports the disguise name
        Player player = mockPlayer(uuid, "Notch");

        // when
        String result = realNameService.getRealName(player);

        // then
        assertThat(result, equalTo("Bobby"));
    }

    @Test
    void shouldFallBackToPhoenixForPlayerJoinedBeforeAuthMeWasLoaded() {
        // given
        UUID uuid = UUID.randomUUID();
        Player player = mockPlayer(uuid, "Notch");
        given(phoenixHook.getRealName(player)).willReturn("Bobby");

        // when
        String firstResult = realNameService.getRealName(player);
        String secondResult = realNameService.getRealName(player);

        // then
        assertThat(firstResult, equalTo("Bobby"));
        assertThat(secondResult, equalTo("Bobby"));
        // the resolved name is cached: this runs on the main thread for every player event
        verify(phoenixHook).getRealName(player);
    }

    @Test
    void shouldFallBackToCurrentNameIfNothingIsKnown() {
        // given
        Player player = mockPlayer(UUID.randomUUID(), "Bobby");
        given(phoenixHook.getRealName(player)).willReturn(null);

        // when
        String result = realNameService.getRealName(player);

        // then
        assertThat(result, equalTo("Bobby"));
    }

    private static Player mockPlayer(UUID uuid, String name) {
        Player player = mock(Player.class);
        given(player.getUniqueId()).willReturn(uuid);
        // only read when the name could not be resolved otherwise
        lenient().when(player.getName()).thenReturn(name);
        return player;
    }
}
