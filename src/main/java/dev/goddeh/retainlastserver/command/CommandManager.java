package dev.goddeh.retainlastserver.command;

import com.velocitypowered.api.proxy.ProxyServer;
import dev.goddeh.retainlastserver.RetainLastServer;
import dev.goddeh.retainlastserver.command.network.NetworkCommand;
import dev.goddeh.retainlastserver.config.MainConfig;
import dev.goddeh.retainlastserver.config.MessagesConfig;
import dev.goddeh.retainlastserver.config.WhitelistConfig;

public class CommandManager {

    private final RetainLastServer plugin;
    private final ProxyServer proxy;
    private final MainConfig mainConfig;
    private final WhitelistConfig whitelistConfig;
    private final MessagesConfig messagesConfig;

    public CommandManager(RetainLastServer plugin, ProxyServer proxy, MainConfig mainConfig, WhitelistConfig whitelistConfig, MessagesConfig messagesConfig) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.whitelistConfig = whitelistConfig;
        this.messagesConfig = messagesConfig;
        this.mainConfig = mainConfig;
        registerCommands();
    }

    private void registerCommands() {
        proxy.getCommandManager().register("network", new NetworkCommand(plugin, mainConfig, whitelistConfig, messagesConfig));
    }
}