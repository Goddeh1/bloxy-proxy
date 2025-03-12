package dev.goddeh.retainlastserver.command.network.whitelist;

import com.velocitypowered.api.command.CommandSource;
import dev.goddeh.retainlastserver.RetainLastServer;
import dev.goddeh.retainlastserver.config.WhitelistConfig;

public class WhitelistOffCommand {

    private final RetainLastServer plugin;
    private final WhitelistConfig whitelistConfig;

    public WhitelistOffCommand(RetainLastServer plugin, WhitelistConfig whitelistConfig) {
        this.plugin = plugin;
        this.whitelistConfig = whitelistConfig;
    }

    public void execute(CommandSource source, String[] args) {
        if (!whitelistConfig.isEnabled()) {
            source.sendMessage(plugin.getMessagesConfig().getComponent(
                    "whitelist.already_disabled",
                    "§eWhitelist is already disabled.",
                    null));
            return;
        }

        whitelistConfig.setEnabled(false);
        source.sendMessage(plugin.getMessagesConfig().getComponent(
                "whitelist.disabled",
                "§aWhitelist has been disabled.",
                null));
    }
}