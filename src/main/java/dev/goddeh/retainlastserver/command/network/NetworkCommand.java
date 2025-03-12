package dev.goddeh.retainlastserver.command.network;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import dev.goddeh.retainlastserver.RetainLastServer;
import dev.goddeh.retainlastserver.command.network.whitelist.*;
import dev.goddeh.retainlastserver.config.MainConfig;
import dev.goddeh.retainlastserver.config.WhitelistConfig;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class NetworkCommand implements SimpleCommand {

    private final RetainLastServer plugin;
    private final MainConfig mainConfig;
    private final WhitelistConfig whitelistConfig;
    private final WhitelistAddCommand whitelistAddCommand;
    private final WhitelistRemoveCommand whitelistRemoveCommand;
    private final WhitelistListCommand whitelistListCommand;
    private final WhitelistAdminOnlyCommand whitelistAdminOnlyCommand;
    private final WhitelistOnCommand whitelistOnCommand;
    private final WhitelistOffCommand whitelistOffCommand;

    public NetworkCommand(RetainLastServer plugin, MainConfig mainConfig, WhitelistConfig whitelistConfig) {
        this.plugin = plugin;
        this.mainConfig = mainConfig;
        this.whitelistConfig = whitelistConfig;
        this.whitelistAddCommand = new WhitelistAddCommand(plugin, whitelistConfig);
        this.whitelistRemoveCommand = new WhitelistRemoveCommand(plugin, whitelistConfig);
        this.whitelistListCommand = new WhitelistListCommand(plugin, whitelistConfig);
        this.whitelistAdminOnlyCommand = new WhitelistAdminOnlyCommand(plugin, whitelistConfig);
        this.whitelistOnCommand = new WhitelistOnCommand(plugin, whitelistConfig);
        this.whitelistOffCommand = new WhitelistOffCommand(plugin, whitelistConfig);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!source.hasPermission("blox.admin")) {
            source.sendMessage(plugin.getMessagesConfig().getComponent(
                    "general.no_permission",
                    "§cYou do not have permission to use this command.",
                    null));
            return;
        }

        if (args.length == 0) {
            sendHelp(source);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "whitelist":
                handleWhitelistCommand(source, Arrays.copyOfRange(args, 1, args.length));
                break;
            case "config":
                handleConfigCommand(source, Arrays.copyOfRange(args, 1, args.length));
                break;
            default:
                sendHelp(source);
                break;
        }
    }

    private void handleWhitelistCommand(CommandSource source, String[] args) {
        if (args.length == 0) {
            source.sendMessage(plugin.getMessagesConfig().getComponent(
                    "whitelist.usage",
                    "Usage: /network whitelist <add/remove/list/adminonly/on/off>",
                    null));
            return;
        }

        switch (args[0].toLowerCase()) {
            case "add":
                whitelistAddCommand.execute(source, Arrays.copyOfRange(args, 1, args.length));
                break;
            case "remove":
                whitelistRemoveCommand.execute(source, Arrays.copyOfRange(args, 1, args.length));
                break;
            case "list":
                whitelistListCommand.execute(source, Arrays.copyOfRange(args, 1, args.length));
                break;
            case "adminonly":
                whitelistAdminOnlyCommand.execute(source, Arrays.copyOfRange(args, 1, args.length));
                break;
            case "on":
                whitelistOnCommand.execute(source, Arrays.copyOfRange(args, 1, args.length));
                break;
            case "off":
                whitelistOffCommand.execute(source, Arrays.copyOfRange(args, 1, args.length));
                break;
            default:
                source.sendMessage(plugin.getMessagesConfig().getComponent(
                        "whitelist.usage",
                        "Usage: /network whitelist <add/remove/list/adminonly/on/off>",
                        null));
                break;
        }
    }

    private void handleConfigCommand(CommandSource source, String[] args) {
        if (args.length == 0) {
            source.sendMessage(plugin.getMessagesConfig().getComponent(
                    "config.usage",
                    "Usage: /network config <reload>",
                    null));
            return;
        }
        switch (args[0].toLowerCase()) {
            case "reload":
                mainConfig.reload();
                whitelistConfig.reload();
                source.sendMessage(plugin.getMessagesConfig().getComponent(
                        "config.reload",
                        "§f§lᴛʙ ɴᴇᴛᴡᴏʀᴋ §8§l| §7Config reload successful. §a§l✔",
                        null));
                break;
            default:
                source.sendMessage(plugin.getMessagesConfig().getComponent(
                        "config.usage",
                        "Usage: /network config <reload>",
                        null));
                break;
        }
    }

    private void sendHelp(CommandSource source) {
        source.sendMessage(plugin.getMessagesConfig().getComponent(
                "network.help_header",
                "=== Network Command Help ===",
                null));

        source.sendMessage(plugin.getMessagesConfig().getComponent(
                "network.help_whitelist_add",
                "/network whitelist add <player> - Add a player to the whitelist",
                null));

        source.sendMessage(plugin.getMessagesConfig().getComponent(
                "network.help_whitelist_remove",
                "/network whitelist remove <player> - Remove a player from the whitelist",
                null));

        source.sendMessage(plugin.getMessagesConfig().getComponent(
                "network.help_whitelist_list",
                "/network whitelist list - List all whitelisted players",
                null));

        source.sendMessage(plugin.getMessagesConfig().getComponent(
                "network.help_whitelist_adminonly",
                "/network whitelist adminonly - Toggle admin-only mode",
                null));

        source.sendMessage(plugin.getMessagesConfig().getComponent(
                "network.help_whitelist_on",
                "/network whitelist on - Enable the whitelist",
                null));

        source.sendMessage(plugin.getMessagesConfig().getComponent(
                "network.help_whitelist_off",
                "/network whitelist off - Disable the whitelist",
                null));
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        List<String> suggestions = new ArrayList<>();

        if (args.length == 0 || (args.length == 1 && "whitelist".startsWith(args[0].toLowerCase()))) {
            suggestions.add("whitelist");
        } else if (args.length == 1) {
            // No suggestions for unknown first arguments
        } else if (args.length == 2 && args[0].equalsIgnoreCase("whitelist")) {
            String subCommand = args[1].toLowerCase();
            if ("add".startsWith(subCommand)) suggestions.add("add");
            if ("remove".startsWith(subCommand)) suggestions.add("remove");
            if ("list".startsWith(subCommand)) suggestions.add("list");
            if ("adminonly".startsWith(subCommand)) suggestions.add("adminonly");
            if ("on".startsWith(subCommand)) suggestions.add("on");
            if ("off".startsWith(subCommand)) suggestions.add("off");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("whitelist")) {
            if (args[1].equalsIgnoreCase("add")) {
                // Suggest online players not in whitelist
                plugin.getProxy().getAllPlayers().forEach(player -> {
                    if (!whitelistConfig.isWhitelisted(player.getUniqueId())) {
                        if (args.length < 3 || player.getUsername().toLowerCase().startsWith(args[2].toLowerCase())) {
                            suggestions.add(player.getUsername());
                        }
                    }
                });
            } else if (args[1].equalsIgnoreCase("remove")) {
                // Suggest whitelisted players
                whitelistConfig.getWhitelistedPlayers().values().forEach(name -> {
                    if (args.length < 3 || name.toLowerCase().startsWith(args[2].toLowerCase())) {
                        suggestions.add(name);
                    }
                });
            }
        }

        return CompletableFuture.completedFuture(suggestions);
    }
}