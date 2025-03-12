package dev.goddeh.retainlastserver.command.network.whitelist;

import com.velocitypowered.api.command.CommandSource;
import dev.goddeh.retainlastserver.RetainLastServer;
import dev.goddeh.retainlastserver.config.WhitelistConfig;

public class WhitelistOnCommand {

    private final RetainLastServer plugin;
    private final WhitelistConfig whitelistConfig;

    public WhitelistOnCommand(RetainLastServer plugin, WhitelistConfig whitelistConfig) {
        this.plugin = plugin;
        this.whitelistConfig = whitelistConfig;
    }

    public void execute(CommandSource source, String[] args) {
        if (whitelistConfig.isEnabled()) {
            source.sendMessage(plugin.getMessagesConfig().getComponent(
                    "whitelist.already_enabled",
                    "§eWhitelist is already enabled.",
                    null));
            return;
        }

        whitelistConfig.setEnabled(true);
        source.sendMessage(plugin.getMessagesConfig().getComponent(
                "whitelist.enabled",
                "§aWhitelist has been enabled.",
                null));
    }
}