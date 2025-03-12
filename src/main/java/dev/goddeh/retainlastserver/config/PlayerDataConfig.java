package dev.goddeh.retainlastserver.config;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PlayerDataConfig {

    private final ProxyServer proxy;
    private final Path dataDirectory;
    private final Logger logger;
    private final Path configFile;
    private ConfigurationLoader<CommentedConfigurationNode> loader;
    private CommentedConfigurationNode rootNode;

    // In-memory cache to reduce file I/O
    private final Map<UUID, PlayerData> playerDataCache = new ConcurrentHashMap<>();

    public PlayerDataConfig(ProxyServer proxy, Path dataDirectory, Logger logger) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        this.configFile = dataDirectory.resolve("data.conf");
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
                // Create default data file with proper HOCON syntax
                String defaultConfig =
                        "# BloxCore Player Data\n\n" +
                                "# Player data storage\n" +
                                "# Format: \n" +
                                "# players {\n" +
                                "#   \"01234567-89ab-cdef-0123-456789abcdef\" { # UUID\n" +
                                "#     name = \"playerName\"\n" +
                                "#     lastServer = \"serverName\"\n" +
                                "#     lastConnected = 1637589632147\n" +
                                "#     firstConnected = 1637489632147\n" +
                                "#     totalConnections = 5\n" +
                                "#     whitelisted = true\n" +
                                "#     awaitingReconnect = \"serverName\"\n" +
                                "#   }\n" +
                                "# }\n\n" +
                                "players {\n" +
                                "  # Player data will be populated here automatically\n" +
                                "}\n";

                Files.writeString(configFile, defaultConfig);
            }

            try {
                // Load the config
                rootNode = loader.load();
                playerDataCache.clear();

                // Load all player data into cache
                CommentedConfigurationNode playersNode = rootNode.getNode("players");
                Map<Object, ? extends CommentedConfigurationNode> players = playersNode.getChildrenMap();

                for (Map.Entry<Object, ? extends CommentedConfigurationNode> entry : players.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey().toString());
                        CommentedConfigurationNode playerNode = entry.getValue();

                        PlayerData playerData = new PlayerData(
                                uuid,
                                playerNode.getNode("name").getString("Unknown"),
                                playerNode.getNode("lastServer").getString(null),
                                playerNode.getNode("lastConnected").getLong(0),
                                playerNode.getNode("firstConnected").getLong(System.currentTimeMillis()),
                                playerNode.getNode("totalConnections").getInt(0),
                                playerNode.getNode("whitelisted").getBoolean(false),
                                playerNode.getNode("awaitingReconnect").getString(null)
                        );

                        playerDataCache.put(uuid, playerData);
                    } catch (IllegalArgumentException e) {
                        logger.warn("Invalid UUID in player data config: {}", entry.getKey());
                    }
                }

                logger.info("Loaded data for {} players", playerDataCache.size());
            } catch (Exception e) {
                logger.error("Failed to load player data, starting with empty cache", e);
                // Continue with empty cache if loading fails
            }

        } catch (IOException e) {
            logger.error("Failed to initialize player data config", e);
        }
    }

    public void saveConfig() {
        try {
            // Create a new root node if it doesn't exist
            if (rootNode == null) {
                rootNode = loader.createEmptyNode();
            }

            // Clear the players node before saving
            rootNode.getNode("players").setValue(null);

            // Save all player data from cache
            for (Map.Entry<UUID, PlayerData> entry : playerDataCache.entrySet()) {
                UUID uuid = entry.getKey();
                PlayerData data = entry.getValue();

                CommentedConfigurationNode playerNode = rootNode.getNode("players", uuid.toString());
                playerNode.getNode("name").setValue(data.getName());
                playerNode.getNode("lastServer").setValue(data.getLastServer());
                playerNode.getNode("lastConnected").setValue(data.getLastConnected());
                playerNode.getNode("firstConnected").setValue(data.getFirstConnected());
                playerNode.getNode("totalConnections").setValue(data.getTotalConnections());
                playerNode.getNode("whitelisted").setValue(data.isWhitelisted());
                playerNode.getNode("awaitingReconnect").setValue(data.getAwaitingReconnect());
            }

            loader.save(rootNode);
        } catch (IOException e) {
            logger.error("Failed to save player data config", e);
        }
    }

    /**
     * Gets a player's data, creating it if it doesn't exist
     *
     * @param uuid Player UUID
     * @return The player's data
     */
    public PlayerData getPlayerData(UUID uuid) {
        return playerDataCache.computeIfAbsent(uuid, id -> {
            // Try to get the player name from the online player
            String name = proxy.getPlayer(id).map(Player::getUsername).orElse("Unknown");

            // Create new player data
            PlayerData data = new PlayerData(
                    id,
                    name,
                    null,
                    0,
                    System.currentTimeMillis(),
                    0,
                    false,
                    null
            );

            return data;
        });
    }

    /**
     * Updates a player's data
     *
     * @param data The updated player data
     */
    public void updatePlayerData(PlayerData data) {
        playerDataCache.put(data.getUuid(), data);
        // We don't save on every update to reduce I/O
    }

    /**
     * Called when a player connects to the proxy
     *
     * @param player The player that connected
     */
    public void playerConnected(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = getPlayerData(uuid);

        data.setName(player.getUsername());
        data.setLastConnected(System.currentTimeMillis());
        data.setTotalConnections(data.getTotalConnections() + 1);

        updatePlayerData(data);
    }

    /**
     * Sets the last server a player was connected to
     *
     * @param uuid Player UUID
     * @param serverName Server name
     */
    public void setLastServer(UUID uuid, String serverName) {
        PlayerData data = getPlayerData(uuid);
        data.setLastServer(serverName);
        updatePlayerData(data);
    }

    /**
     * Sets the server a player is waiting to reconnect to
     *
     * @param uuid Player UUID
     * @param serverName Server name, or null if not waiting
     */
    public void setAwaitingReconnect(UUID uuid, String serverName) {
        PlayerData data = getPlayerData(uuid);
        data.setAwaitingReconnect(serverName);
        updatePlayerData(data);
    }

    /**
     * Sets whether a player is whitelisted
     *
     * @param uuid Player UUID
     * @param whitelisted Whether the player is whitelisted
     */
    public void setWhitelisted(UUID uuid, boolean whitelisted) {
        PlayerData data = getPlayerData(uuid);
        data.setWhitelisted(whitelisted);
        updatePlayerData(data);
        // Immediately save whitelisting changes
        saveConfig();
    }

    /**
     * Gets all whitelisted players
     *
     * @return Map of UUIDs to player names
     */
    public Map<UUID, String> getWhitelistedPlayers() {
        Map<UUID, String> result = new HashMap<>();

        for (Map.Entry<UUID, PlayerData> entry : playerDataCache.entrySet()) {
            if (entry.getValue().isWhitelisted()) {
                result.put(entry.getKey(), entry.getValue().getName());
            }
        }

        return result;
    }

    /**
     * Checks if a player is whitelisted
     *
     * @param uuid Player UUID
     * @return true if the player is whitelisted
     */
    public boolean isWhitelisted(UUID uuid) {
        PlayerData data = getPlayerData(uuid);
        return data.isWhitelisted();
    }

    /**
     * Gets the server a player is waiting to reconnect to
     *
     * @param uuid Player UUID
     * @return Server name, or null if not waiting
     */
    public String getAwaitingReconnect(UUID uuid) {
        PlayerData data = getPlayerData(uuid);
        return data.getAwaitingReconnect();
    }

    /**
     * Gets a player's last server
     *
     * @param uuid Player UUID
     * @return Server name, or null if not known
     */
    public String getLastServer(UUID uuid) {
        PlayerData data = getPlayerData(uuid);
        return data.getLastServer();
    }

    public void reload() {
        loadConfig();
    }

    /**
     * Schedules periodic saves to ensure data is not lost
     *
     * @param plugin The plugin instance
     * @param intervalSeconds Seconds between saves
     */
    public void startAutosaveTask(Object plugin, int intervalSeconds) {
        proxy.getScheduler()
                .buildTask(plugin, this::saveConfig)
                .repeat(intervalSeconds, TimeUnit.SECONDS)
                .schedule();
    }

    /**
     * Inner class to represent player data
     */
    public static class PlayerData {
        private final UUID uuid;
        private String name;
        private String lastServer;
        private long lastConnected;
        private final long firstConnected;
        private int totalConnections;
        private boolean whitelisted;
        private String awaitingReconnect;

        public PlayerData(UUID uuid, String name, String lastServer, long lastConnected,
                          long firstConnected, int totalConnections, boolean whitelisted,
                          String awaitingReconnect) {
            this.uuid = uuid;
            this.name = name;
            this.lastServer = lastServer;
            this.lastConnected = lastConnected;
            this.firstConnected = firstConnected;
            this.totalConnections = totalConnections;
            this.whitelisted = whitelisted;
            this.awaitingReconnect = awaitingReconnect;
        }

        public UUID getUuid() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLastServer() {
            return lastServer;
        }

        public void setLastServer(String lastServer) {
            this.lastServer = lastServer;
        }

        public long getLastConnected() {
            return lastConnected;
        }

        public void setLastConnected(long lastConnected) {
            this.lastConnected = lastConnected;
        }

        public long getFirstConnected() {
            return firstConnected;
        }

        public int getTotalConnections() {
            return totalConnections;
        }

        public void setTotalConnections(int totalConnections) {
            this.totalConnections = totalConnections;
        }

        public boolean isWhitelisted() {
            return whitelisted;
        }

        public void setWhitelisted(boolean whitelisted) {
            this.whitelisted = whitelisted;
        }

        public String getAwaitingReconnect() {
            return awaitingReconnect;
        }

        public void setAwaitingReconnect(String awaitingReconnect) {
            this.awaitingReconnect = awaitingReconnect;
        }
    }
}