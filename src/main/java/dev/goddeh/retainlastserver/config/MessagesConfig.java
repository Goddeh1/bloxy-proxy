package dev.goddeh.retainlastserver.config;

import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessagesConfig {

    private final ProxyServer proxy;
    private final Path dataDirectory;
    private final Logger logger;
    private final Path configFile;
    private ConfigurationLoader<CommentedConfigurationNode> loader;
    private CommentedConfigurationNode rootNode;

    // Pattern to match placeholders like %%placeholder%%
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%%([^%]+)%%");

    public MessagesConfig(ProxyServer proxy, Path dataDirectory, Logger logger) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        this.configFile = dataDirectory.resolve("messages.conf");
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
                // Create default messages file with proper HOCON syntax
                String defaultMessages = createDefaultMessagesConfig();
                Files.writeString(configFile, defaultMessages);
            }

            try {
                // Load the config
                rootNode = loader.load();
                logger.info("Messages config loaded");
            } catch (Exception e) {
                logger.error("Failed to load messages config, using default messages", e);
                // Continue with default values if loading fails
            }

        } catch (IOException e) {
            logger.error("Failed to initialize messages config", e);
        }
    }

    private String createDefaultMessagesConfig() {
        return "# BloxCore Messages Configuration\n" +
                "# Use %% for placeholders. Example: %%player%% for player name\n" +
                "# Color codes use § symbol (e.g., §a for green)\n\n" +

                "general {\n" +
                "  no_permission = \"§cYou do not have permission to use this command.\"\n" +
                "  command_not_found = \"§cCommand not found.\"\n" +
                "  player_not_found = \"§cPlayer %%player%% not found. They need to be online to be added to the whitelist.\"\n" +
                "}\n\n" +

                "server {\n" +
                "  came_online = \"§a[NETWORK] Server '%%server%%' is now online.\"\n" +
                "  connect_button = \"[CONNECT]\"\n" +
                "  went_offline = \"§c[NETWORK] Server '%%server%%' is now offline.\"\n" +
                "  all_offline = \"All servers are currently offline. Please try again later.\"\n" +
                "}\n\n" +

                "player {\n" +
                "  connect = \"§a[CONNECT] %%player%%\"\n" +
                "  disconnect = \"§c[DISCONNECT] %%player%%\"\n" +
                "  switch = \"§6[SWITCH] §e%%player%%: %%from%% §f->§e %%to%%\"\n" +
                "  not_authorized = \"§f§lᴛʙ ɴᴇᴛᴡᴏʀᴋ §8§l| §7You're not authorised to connect to this network.\"\n" +
                "  not_whitelisted = \"§f§lᴛʙ ɴᴇᴛᴡᴏʀᴋ §8§l| §7You are not whitelisted on this network.\"\n" +
                "  admin_only = \"§f§lᴛʙ ɴᴇᴛᴡᴏʀᴋ §8§l| §7This server is in admin-only mode.\"\n" +
                "}\n\n" +

                "network {\n" +
                "  help_header = \"=== Network Command Help ===\"\n" +
                "  help_whitelist_add = \"/network whitelist add <player> - Add a player to the whitelist\"\n" +
                "  help_whitelist_remove = \"/network whitelist remove <player> - Remove a player from the whitelist\"\n" +
                "  help_whitelist_list = \"/network whitelist list - List all whitelisted players\"\n" +
                "  help_whitelist_adminonly = \"/network whitelist adminonly - Toggle admin-only mode\"\n" +
                "}\n\n" +

                "whitelist {\n" +
                "  usage = \"Usage: /network whitelist <add/remove/list/adminonly>\"\n" +
                "  add_usage = \"Usage: /network whitelist add <player>\"\n" +
                "  remove_usage = \"Usage: /network whitelist remove <player>\"\n" +
                "  enabled = \"Whitelist has been automatically enabled.\"\n" +
                "  already_whitelisted = \"Player %%player%% is already whitelisted.\"\n" +
                "  added = \"Added %%player%% to the whitelist.\"\n" +
                "  removed = \"Removed %%player%% from the whitelist.\"\n" +
                "  not_whitelisted = \"Player %%player%% is not whitelisted.\"\n" +
                "  list_header = \"=== Whitelist Status ===\"\n" +
                "  list_enabled = \"Enabled: %%status%%\"\n" +
                "  list_admin_only = \"Admin-only mode: %%status%%\"\n" +
                "  list_players_header = \"Whitelisted players (%%count%%):\"\n" +
                "  list_none = \"  None\"\n" +
                "  admin_only_enabled = \"Admin-only mode has been enabled.\"\n" +
                "  admin_only_disabled = \"Admin-only mode has been disabled.\"\n" +
                "  admin_only_info = \"Only players with the 'blox.admin' permission can join the server now.\"\n" +
                "}\n";
    }

    public void saveConfig() {
        try {
            loader.save(rootNode);
        } catch (IOException e) {
            logger.error("Failed to save messages config", e);
        }
    }

    /**
     * Gets a raw message from the config, without any placeholders replaced
     *
     * @param path Dot-separated path to the message
     * @param defaultValue Default value if the message is not found
     * @return The raw message
     */
    public String getRawMessage(String path, String defaultValue) {
        String[] parts = path.split("\\.");
        CommentedConfigurationNode node = rootNode;

        for (String part : parts) {
            node = node.getNode(part);
        }

        return node.getString(defaultValue);
    }

    /**
     * Gets a message with placeholders replaced
     *
     * @param path Dot-separated path to the message
     * @param defaultValue Default value if the message is not found
     * @param placeholders Map of placeholders to values
     * @return The formatted message
     */
    public String getMessage(String path, String defaultValue, Map<String, String> placeholders) {
        String message = getRawMessage(path, defaultValue);

        if (placeholders != null) {
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String placeholder = matcher.group(1);
                String replacement = placeholders.getOrDefault(placeholder, matcher.group(0));
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }

            matcher.appendTail(sb);
            message = sb.toString();
        }

        return message;
    }

    /**
     * Gets a message with placeholders replaced as a Component
     *
     * @param path Dot-separated path to the message
     * @param defaultValue Default value if the message is not found
     * @param placeholders Map of placeholders to values
     * @return The formatted message as a Component
     */
    public Component getComponent(String path, String defaultValue, Map<String, String> placeholders) {
        String message = getMessage(path, defaultValue, placeholders);
        return LegacyComponentSerializer.legacySection().deserialize(message);
    }

    /**
     * Gets a special server online message with a clickable connect button
     *
     * @param serverName The name of the server that came online
     * @return A component with the server online message and clickable button
     */
    public Component getServerOnlineMessage(String serverName) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("server", serverName);

        Component message = getComponent("server.came_online", "§a[NETWORK] Server '%%server%%' is now online.", placeholders);
        Component connectButton = Component.text(getRawMessage("server.connect_button", "[CONNECT]"))
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/server " + serverName));

        return TextComponent.ofChildren(message, Component.space(), connectButton);
    }

    public void reload() {
        loadConfig();
    }
}