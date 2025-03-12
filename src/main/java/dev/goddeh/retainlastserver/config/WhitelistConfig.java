package dev.goddeh.retainlastserver.config;

import com.velocitypowered.api.proxy.ProxyServer;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class WhitelistConfig {

    private final ProxyServer proxy;
    private final Path dataDirectory;
    private final Logger logger;
    private final Path configFile;
    private ConfigurationLoader<CommentedConfigurationNode> loader;
    private CommentedConfigurationNode rootNode;

    private boolean enabled = false;
    private boolean adminOnly = false;

    // Reference to player data config for storing whitelisted players
    private final PlayerDataConfig playerDataConfig;

    public WhitelistConfig(ProxyServer proxy, Path dataDirectory, Logger logger, PlayerDataConfig playerDataConfig) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        this.playerDataConfig = playerDataConfig;
        this.configFile = dataDirectory.resolve("whitelist.conf");
        loadConfig();
    }

    private void loadConfig() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }

            boolean needsCreate = !Files.exists(configFile);

            // Initialize the loader
            loader = HoconConfigurationLoader.builder()
                    .setPath(configFile)
                    .build();

            if (needsCreate) {
                // Create default whitelist config with proper HOCON syntax
                String defaultConfig =
                        "# BloxCore Whitelist Configuration\n\n" +
                                "# Whether the whitelist is enabled\n" +
                                "enabled = false\n\n" +
                                "# Whether only admins can join (players with blox.admin permission)\n" +
                                "admin_only = false\n";

                Files.writeString(configFile, defaultConfig);
            }

            try {
                // Load the config
                rootNode = loader.load();

                enabled = rootNode.getNode("enabled").getBoolean(false);
                adminOnly = rootNode.getNode("admin_only").getBoolean(false);

                logger.info("Loaded whitelist config. Enabled: {}, Admin only: {}",
                        enabled, adminOnly);
            } catch (Exception e) {
                logger.error("Failed to load whitelist config, using default values", e);
                // Continue with default values if loading fails
            }

        } catch (IOException e) {
            logger.error("Failed to initialize whitelist config", e);
        }
    }

    public void saveConfig() {
        try {
            rootNode.getNode("enabled").setValue(enabled);
            rootNode.getNode("admin_only").setValue(adminOnly);

            loader.save(rootNode);
        } catch (IOException e) {
            logger.error("Failed to save whitelist config", e);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        saveConfig();
    }

    public boolean isAdminOnly() {
        return adminOnly;
    }

    public void setAdminOnly(boolean adminOnly) {
        this.adminOnly = adminOnly;
        saveConfig();
    }

    public boolean isWhitelisted(UUID uuid) {
        return playerDataConfig.isWhitelisted(uuid);
    }

    public boolean addPlayer(UUID uuid, String name) {
        if (playerDataConfig.isWhitelisted(uuid)) {
            return false;
        }
        playerDataConfig.setWhitelisted(uuid, true);
        return true;
    }

    public boolean removePlayer(UUID uuid) {
        if (!playerDataConfig.isWhitelisted(uuid)) {
            return false;
        }
        playerDataConfig.setWhitelisted(uuid, false);
        return true;
    }

    public Map<UUID, String> getWhitelistedPlayers() {
        return playerDataConfig.getWhitelistedPlayers();
    }

    public void reload() {
        loadConfig();
    }
}