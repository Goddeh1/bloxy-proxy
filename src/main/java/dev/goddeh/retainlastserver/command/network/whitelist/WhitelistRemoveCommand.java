package dev.goddeh.retainlastserver.command.network.whitelist;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.goddeh.retainlastserver.RetainLastServer;
import dev.goddeh.retainlastserver.config.WhitelistConfig;
import dev.goddeh.retainlastserver.util.UUIDLookupService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class WhitelistRemoveCommand {

    private final RetainLastServer plugin;
    private final WhitelistConfig whitelistConfig;

    public WhitelistRemoveCommand(RetainLastServer plugin, WhitelistConfig whitelistConfig) {
        this.plugin = plugin;
        this.whitelistConfig = whitelistConfig;
    }

    public void execute(CommandSource source, String[] args) {
        if (args.length == 0) {
            source.sendMessage(plugin.getMessagesConfig().getComponent(
                    "whitelist.remove_usage",
                    "Usage: /network whitelist remove <player>",
                    null));
            return;
        }

        String playerName = args[0];
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", playerName);

        // First check if there's an existing whitelisted player with this name
        Optional<UUID> existingPlayerUuid = findWhitelistedPlayerByName(playerName);

        if (existingPlayerUuid.isPresent()) {
            // Found in whitelist by name, remove them
            whitelistConfig.removePlayer(existingPlayerUuid.get());
            source.sendMessage(plugin.getMessagesConfig().getComponent(
                    "whitelist.removed",
                    "Removed %%player%% from the whitelist.",
                    placeholders));
            return;
        }

        // If not found by name, check if the player is online to get their UUID
        Optional<Player> optionalPlayer = plugin.getProxy().getPlayer(playerName);

        if (optionalPlayer.isPresent()) {
            // Player is online, remove them by UUID
            Player player = optionalPlayer.get();
            UUID uuid = player.getUniqueId();
            String correctName = player.getUsername();

            placeholders.put("player", correctName);

            if (whitelistConfig.removePlayer(uuid)) {
                source.sendMessage(plugin.getMessagesConfig().getComponent(
                        "whitelist.removed",
                        "Removed %%player%% from the whitelist.",
                        placeholders));
            } else {
                source.sendMessage(plugin.getMessagesConfig().getComponent(
                        "whitelist.not_whitelisted",
                        "Player %%player%% is not whitelisted.",
                        placeholders));
            }
        } else {
            // Player is not online, use the Mojang API to look up their UUID
            source.sendMessage(plugin.getMessagesConfig().getComponent(
                    "general.looking_up_player",
                    "§eLooking up player %%player%%...",
                    placeholders));

            UUIDLookupService.getUUID(playerName).thenAccept(uuid -> {
                plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                    if (uuid != null) {
                        // Get the correct capitalization of the name
                        UUIDLookupService.getCorrectUsername(playerName).thenAccept(correctName -> {
                            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                                Map<String, String> updatedPlaceholders = new HashMap<>();
                                updatedPlaceholders.put("player", correctName != null ? correctName : playerName);

                                if (whitelistConfig.removePlayer(uuid)) {
                                    source.sendMessage(plugin.getMessagesConfig().getComponent(
                                            "whitelist.removed",
                                            "Removed %%player%% from the whitelist.",
                                            updatedPlaceholders));
                                } else {
                                    source.sendMessage(plugin.getMessagesConfig().getComponent(
                                            "whitelist.not_whitelisted",
                                            "Player %%player%% is not whitelisted.",
                                            updatedPlaceholders));
                                }
                            }).schedule();
                        });
                    } else {
                        source.sendMessage(plugin.getMessagesConfig().getComponent(
                                "general.player_not_found",
                                "§cPlayer %%player%% not found. Please check the spelling and try again.",
                                placeholders));
                    }
                }).schedule();
            }).exceptionally(ex -> {
                plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                    source.sendMessage(plugin.getMessagesConfig().getComponent(
                            "general.lookup_error",
                            "§cError looking up player %%player%%: " + ex.getMessage(),
                            placeholders));
                }).schedule();
                return null;
            });
        }
    }

    private Optional<UUID> findWhitelistedPlayerByName(String playerName) {
        for (Map.Entry<UUID, String> entry : whitelistConfig.getWhitelistedPlayers().entrySet()) {
            if (entry.getValue().equalsIgnoreCase(playerName)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }
}