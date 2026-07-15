package fr.xephi.authme.velocity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityConfigManagerTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void shouldCreateConfigFileWithDefaults() {
        VelocityConfigManager configManager = new VelocityConfigManager(tempDirectory);

        assertTrue(Files.exists(tempDirectory.resolve("config.yml")));
        assertTrue(configManager.getConfiguration().authServers().contains("lobby"));
        assertFalse(configManager.getConfiguration().allServersAreAuthServers());
        assertTrue(configManager.getConfiguration().serverSwitchRequiresAuth());
        assertEquals("Authentication required.", configManager.getConfiguration().serverSwitchKickMessage());
        assertFalse(configManager.getConfiguration().autoLoginEnabled());
        assertTrue(configManager.getConfiguration().commandsRequireAuth());
        assertTrue(configManager.getConfiguration().isBlacklistedCommand("login"));
        assertTrue(configManager.getConfiguration().isBlacklistedCommand("/login"));
        assertTrue(configManager.getConfiguration().chatRequiresAuth());
        assertFalse(configManager.getConfiguration().sharedSecret().isEmpty());
    }

    @Test
    void shouldPreserveExistingSharedSecret() throws IOException {
        Files.writeString(tempDirectory.resolve("config.yml"), "proxySharedSecret: my-existing-secret\n");

        VelocityConfigManager configManager = new VelocityConfigManager(tempDirectory);

        assertEquals("my-existing-secret", configManager.getConfiguration().sharedSecret());
    }

    @Test
    void shouldNormalizeConfiguredServersAndLogoutTarget() throws IOException {
        Files.writeString(tempDirectory.resolve("config.yml"), """
            authServers:
            - Lobby
            - HUB
            allServersAreAuthServers: false
            serverSwitch:
              requiresAuth: false
              kickMessage: Please authenticate first.
            autoLogin: true
            sendOnLogout: true
            unloggedUserServer: LiMbO
            """);

        VelocityProxyConfiguration configuration = new VelocityConfigManager(tempDirectory).getConfiguration();

        assertTrue(configuration.authServers().contains("lobby"));
        assertTrue(configuration.authServers().contains("hub"));
        assertFalse(configuration.serverSwitchRequiresAuth());
        assertEquals("Please authenticate first.", configuration.serverSwitchKickMessage());
        assertTrue(configuration.autoLoginEnabled());
        assertTrue(configuration.sendOnLogoutEnabled());
        assertEquals("limbo", configuration.sendOnLogoutTarget());
    }

    @Test
    void shouldNormalizeCommandBlacklist() throws IOException {
        Files.writeString(tempDirectory.resolve("config.yml"), """
            commands:
              requireAuth: true
              blacklist:
              - login
              - /REG
            chatRequiresAuth: true
            """);

        VelocityProxyConfiguration configuration = new VelocityConfigManager(tempDirectory).getConfiguration();

        assertTrue(configuration.commandsRequireAuth());
        assertTrue(configuration.isBlacklistedCommand("login"));
        assertTrue(configuration.isBlacklistedCommand("/login"));
        assertTrue(configuration.isBlacklistedCommand("/REG"));
        assertTrue(configuration.isBlacklistedCommand("reg"));
        assertFalse(configuration.isBlacklistedCommand("spawn"));
        assertTrue(configuration.chatRequiresAuth());
    }
}
