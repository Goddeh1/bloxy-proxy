package dev.goddeh.retainlastserver.command.network.whitelist;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.goddeh.retainlastserver.RetainLastServer;
import dev.goddeh.retainlastserver.config.WhitelistConfig;
import dev.goddeh.retainlastserver.util.UUIDLookupService;
import net.kyori.adventure.text.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class WhitelistAddCommand {

    private final RetainLastServer plugin;
    private final WhitelistConfig whitelistConfig;

    public WhitelistAddCommand(RetainLastServer plugin, WhitelistConfig whitelistConfig) {
        this.plugin = plugin;
        this.whitelistConfig = whitelistConfig;
    }

    public void execute(CommandSource source, String[] args) {
        if (args.length == 0) {
            source.sendMessage(plugin.getMessagesConfig().getComponent(
                    "whitelist.add_usage",
                    "Usage: /network whitelist add <player>",
                    null));
            return;
        }

        String playerName = args[0];
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", playerName);

        // Enable whitelist if it's being used for the first time
        if (!whitelistConfig.isEnabled()) {
            whitelistConfig.setEnabled(true);
            source.sendMessage(plugin.getMessagesConfig().getComponent(
                    "whitelist.enabled",
                    "Whitelist has been automatically enabled.",
                    null));
        }

        // First try to get the player if they're online
        Optional<Player> optionalPlayer = plugin.getProxy().getPlayer(playerName);

        if (optionalPlayer.isPresent()) {
            // Player is online, use their info directly
            Player player = optionalPlayer.get();
            UUID uuid = player.getUniqueId();
            String correctName = player.getUsername();

            handlePlayerAdd(source, uuid, correctName);
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

                                handlePlayerAdd(source, uuid, correctName != null ? correctName : playerName);
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

    private void handlePlayerAdd(CommandSource source, UUID uuid, String playerName) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", playerName);

        if (whitelistConfig.isWhitelisted(uuid)) {
            source.sendMessage(plugin.getMessagesConfig().getComponent(
                    "whitelist.already_whitelisted",
                    "Player %%player%% is already whitelisted.",
                    placeholders));
            return;
        }

        whitelistConfig.addPlayer(uuid, playerName);
        source.sendMessage(plugin.getMessagesConfig().getComponent(
                "whitelist.added",
                "Added %%player%% to the whitelist.",
                placeholders));
    }
}