package dev.goddeh.retainlastserver.command.network.whitelist;

import com.velocitypowered.api.command.CommandSource;
import dev.goddeh.retainlastserver.RetainLastServer;
import dev.goddeh.retainlastserver.config.WhitelistConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WhitelistListCommand {

    private final RetainLastServer plugin;
    private final WhitelistConfig whitelistConfig;

    public WhitelistListCommand(RetainLastServer plugin, WhitelistConfig whitelistConfig) {
        this.plugin = plugin;
        this.whitelistConfig = whitelistConfig;
    }

    public void execute(CommandSource source, String[] args) {
        Map<UUID, String> players = whitelistConfig.getWhitelistedPlayers();
        boolean enabled = whitelistConfig.isEnabled();
        boolean adminOnly = whitelistConfig.isAdminOnly();

        // Send header
        source.sendMessage(plugin.getMessagesConfig().getComponent(
                "whitelist.list_header",
                "=== Whitelist Status ===",
                null));

        // Send status
        Map<String, String> statusPlaceholders = new HashMap<>();
        statusPlaceholders.put("status", enabled ? "Yes" : "No");

        source.sendMessage(plugin.getMessagesConfig().getComponent(
                        "whitelist.list_enabled",
                        "Enabled: %%status%%",
                        statusPlaceholders)
                .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED));

        statusPlaceholders.put("status", adminOnly ? "Yes" : "No");
        source.sendMessage(plugin.getMessagesConfig().getComponent(
                        "whitelist.list_admin_only",
                        "Admin-only mode: %%status%%",
                        statusPlaceholders)
                .color(adminOnly ? NamedTextColor.GREEN : NamedTextColor.RED));

        // Send player list
        Map<String, String> countPlaceholders = new HashMap<>();
        countPlaceholders.put("count", String.valueOf(players.size()));

        source.sendMessage(plugin.getMessagesConfig().getComponent(
                        "whitelist.list_players_header",
                        "Whitelisted players (%%count%%):",
                        countPlaceholders)
                .color(NamedTextColor.YELLOW));

        if (players.isEmpty()) {
            source.sendMessage(plugin.getMessagesConfig().getComponent(
                            "whitelist.list_none",
                            "  None",
                            null)
                    .color(NamedTextColor.GRAY));
        } else {
            int i = 0;
            StringBuilder sb = new StringBuilder();

            for (String playerName : players.values()) {
                sb.append(playerName);
                i++;

                // Add comma if not the last player
                if (i < players.size()) {
                    sb.append(", ");
                }

                // Split into multiple messages if needed (every 10 players)
                if (i % 10 == 0 || i == players.size()) {
                    source.sendMessage(Component.text("  " + sb.toString()).color(NamedTextColor.WHITE));
                    sb = new StringBuilder();
                }
            }
        }
    }
}