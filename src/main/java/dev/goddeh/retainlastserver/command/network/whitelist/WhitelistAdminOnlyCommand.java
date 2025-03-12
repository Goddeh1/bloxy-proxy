package dev.goddeh.retainlastserver.command.network.whitelist;

import com.velocitypowered.api.command.CommandSource;
import dev.goddeh.retainlastserver.RetainLastServer;
import dev.goddeh.retainlastserver.config.WhitelistConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class WhitelistAdminOnlyCommand {

    private final RetainLastServer plugin;
    private final WhitelistConfig whitelistConfig;

    public WhitelistAdminOnlyCommand(RetainLastServer plugin, WhitelistConfig whitelistConfig) {
        this.plugin = plugin;
        this.whitelistConfig = whitelistConfig;
    }

    public void execute(CommandSource source, String[] args) {
        boolean currentState = whitelistConfig.isAdminOnly();
        boolean newState = !currentState;

        whitelistConfig.setAdminOnly(newState);

        // Enable whitelist if admin-only is being enabled and whitelist was disabled
        if (newState && !whitelistConfig.isEnabled()) {
            whitelistConfig.setEnabled(true);
            source.sendMessage(plugin.getMessagesConfig().getComponent(
                            "whitelist.enabled",
                            "Whitelist has been automatically enabled.",
                            null)
                    .color(NamedTextColor.GREEN));
        }

        source.sendMessage(plugin.getMessagesConfig().getComponent(
                        newState ? "whitelist.admin_only_enabled" : "whitelist.admin_only_disabled",
                        newState ? "Admin-only mode has been enabled." : "Admin-only mode has been disabled.",
                        null)
                .color(newState ? NamedTextColor.GREEN : NamedTextColor.YELLOW));

        if (newState) {
            source.sendMessage(plugin.getMessagesConfig().getComponent(
                            "whitelist.admin_only_info",
                            "Only players with the 'blox.admin' permission can join the server now.",
                            null)
                    .color(NamedTextColor.YELLOW));
        }
    }
}