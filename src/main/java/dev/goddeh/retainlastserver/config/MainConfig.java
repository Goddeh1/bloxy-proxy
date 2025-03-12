package dev.goddeh.retainlastserver.config;

import com.velocitypowered.api.proxy.ProxyServer;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MainConfig {

    private final ProxyServer proxy;
    private final Path dataDirectory;
    private final Logger logger;
    private final Path configFile;
    private ConfigurationLoader<CommentedConfigurationNode> loader;
    private CommentedConfigurationNode rootNode;

    // Default values
    private String limboServer = "limbo";
    private int serverCheckIntervalSeconds = 5;

    public MainConfig(ProxyServer proxy, Path dataDirectory, Logger logger) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        this.configFile = dataDirectory.resolve("config.conf");
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
                // Create default config file with proper HOCON syntax
                String defaultConfig =
                        "# BloxCore Main Configuration\n\n" +
                                "server {\n" +
                                "  # The name of the limbo server to use when a player is disconnected from another server\n" +
                                "  limbo_server = \"limbo\"\n\n" +
                                "  # How often (in seconds) to check the status of all servers\n" +
                                "  check_interval_seconds = 5\n" +
                                "}\n";

                Files.writeString(configFile, defaultConfig);
            }

            try {
                // Load the config
                rootNode = loader.load();

                // Read the values
                limboServer = rootNode.getNode("server", "limbo_server").getString(limboServer);
                serverCheckIntervalSeconds = rootNode.getNode("server", "check_interval_seconds").getInt(serverCheckIntervalSeconds);

                logger.info("Config loaded: Limbo server: '{}', Check interval: {}s",
                        limboServer, serverCheckIntervalSeconds);
            } catch (Exception e) {
                logger.error("Failed to load config, using default values", e);
                // Continue with default values if loading fails
            }

        } catch (IOException e) {
            logger.error("Failed to initialize main config", e);
        }
    }

    public void saveConfig() {
        try {
            rootNode.getNode("server", "limbo_server").setValue(limboServer);
            rootNode.getNode("server", "check_interval_seconds").setValue(serverCheckIntervalSeconds);

            loader.save(rootNode);
        } catch (IOException e) {
            logger.error("Failed to save main config", e);
        }
    }

    public String getLimboServer() {
        return limboServer;
    }

    public void setLimboServer(String limboServer) {
        this.limboServer = limboServer;
        saveConfig();
    }

    public int getServerCheckIntervalSeconds() {
        return serverCheckIntervalSeconds;
    }

    public void setServerCheckIntervalSeconds(int serverCheckIntervalSeconds) {
        this.serverCheckIntervalSeconds = serverCheckIntervalSeconds;
        saveConfig();
    }

    public void reload() {
        loadConfig();
    }
}