package dev.goddeh.retainlastserver.command;

import com.velocitypowered.api.proxy.ProxyServer;
import dev.goddeh.retainlastserver.RetainLastServer;
import dev.goddeh.retainlastserver.command.network.NetworkCommand;
import dev.goddeh.retainlastserver.config.WhitelistConfig;

public class CommandManager {

    private final RetainLastServer plugin;
    private final ProxyServer proxy;
    private final WhitelistConfig whitelistConfig;

    public CommandManager(RetainLastServer plugin, ProxyServer proxy, WhitelistConfig whitelistConfig) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.whitelistConfig = whitelistConfig;
        registerCommands();
    }

    private void registerCommands() {
        proxy.getCommandManager().register("network", new NetworkCommand(plugin, whitelistConfig));
    }
}