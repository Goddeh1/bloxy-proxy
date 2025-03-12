package dev.goddeh.retainlastserver;

import com.google.inject.Inject;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.scheduler.ScheduledTask;
import dev.goddeh.retainlastserver.command.CommandManager;
import dev.goddeh.retainlastserver.config.MainConfig;
import dev.goddeh.retainlastserver.config.MessagesConfig;
import dev.goddeh.retainlastserver.config.PlayerDataConfig;
import dev.goddeh.retainlastserver.config.WhitelistConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Plugin(
        id = "bloxyproxy",
        name = "BloxyProxy",
        version = "2.0"
)
public class RetainLastServer {
    private final ProxyServer proxy;
    private final Path dataDirectory;
    private final Map<RegisteredServer, Boolean> serverStatus;
    private ScheduledTask task;

    @Inject
    private Logger logger;

    //Config stuff
    private MainConfig mainConfig;
    private MessagesConfig messagesConfig;
    private PlayerDataConfig playerDataConfig;
    private WhitelistConfig whitelistConfig;
    private CommandManager commandManager;

    @Inject
    public RetainLastServer(ProxyServer proxy, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
        this.serverStatus = new HashMap<>();
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.mainConfig = new MainConfig(proxy, dataDirectory, logger);
        this.messagesConfig = new MessagesConfig(proxy, dataDirectory, logger);
        this.playerDataConfig = new PlayerDataConfig(proxy, dataDirectory, logger);
        this.whitelistConfig = new WhitelistConfig(proxy, dataDirectory, logger, playerDataConfig);

        this.commandManager = new CommandManager(this, proxy, whitelistConfig);

        playerDataConfig.startAutosaveTask(this, 60);

        task = proxy.getScheduler()
                .buildTask(this, this::checkServerStatus)
                .repeat(mainConfig.getServerCheckIntervalSeconds(), TimeUnit.SECONDS)
                .schedule();
    }

    private void checkServerStatus() {
        Collection<RegisteredServer> servers = proxy.getAllServers();
        for (RegisteredServer server : servers) {
            if (server.getServerInfo().getName().equals(mainConfig.getLimboServer())) continue;

            boolean currentStatus = isServerOnline(server);
            Boolean previousStatus = serverStatus.get(server);

            if (previousStatus == null) {
                serverStatus.put(server, currentStatus);
            } else if (currentStatus != previousStatus) {
                String serverName = server.getServerInfo().getName();
                if (currentStatus) {
                    // Server came online
                    Component message = messagesConfig.getServerOnlineMessage(serverName);
                    broadcastMessage(message);

                    // Attempt to reconnect players who were disconnected from this server
                    reconnectPlayers(server);
                } else {
                    // Server went offline
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("server", serverName);
                    broadcastMessage(messagesConfig.getComponent(
                            "server.went_offline",
                            "§c[NETWORK] Server '%%server%%' is now offline.",
                            placeholders));

                    handleServerOffline(server);
                }
                serverStatus.put(server, currentStatus);
            }
        }
    }

    private void handleServerOffline(RegisteredServer server) {
        String serverName = server.getServerInfo().getName();
        proxy.getAllPlayers().forEach(player -> {
            Optional<ServerInfo> currentServer = player.getCurrentServer().map(conn -> conn.getServerInfo());
            if (currentServer.isPresent() && currentServer.get().getName().equals(serverName)) {
                // Save that this player was on this server
                playerDataConfig.setAwaitingReconnect(player.getUniqueId(), serverName);
                // Connect them to limbo
                connectToLimbo(player);
            }
        });
    }

    private void reconnectPlayers(RegisteredServer server) {
        String serverName = server.getServerInfo().getName();

        // Get all players
        for (Player player : proxy.getAllPlayers()) {
            String awaitingServer = playerDataConfig.getAwaitingReconnect(player.getUniqueId());

            // If player is waiting for this server and they're currently in limbo
            if (serverName.equals(awaitingServer) &&
                    player.getCurrentServer()
                            .map(conn -> conn.getServerInfo().getName().equals(mainConfig.getLimboServer()))
                            .orElse(false)) {

                player.createConnectionRequest(server).fireAndForget();
                playerDataConfig.setAwaitingReconnect(player.getUniqueId(), null);
            }
        }
    }

    private void connectToLimbo(Player player) {
        Optional<RegisteredServer> limboServer = proxy.getServer(mainConfig.getLimboServer());
        if (limboServer.isPresent() && isServerOnline(limboServer.get())) {
            player.createConnectionRequest(limboServer.get()).fireAndForget();
        } else {
            // Only disconnect if absolutely no servers are available
            boolean anyServerAvailable = proxy.getAllServers().stream()
                    .anyMatch(this::isServerOnline);

            if (!anyServerAvailable) {
                player.disconnect(messagesConfig.getComponent(
                        "server.all_offline",
                        "All servers are currently offline. Please try again later.",
                        null));
            } else {
                // Try to find any online server if limbo is not available
                proxy.getAllServers().stream()
                        .filter(this::isServerOnline)
                        .findFirst()
                        .ifPresent(server ->
                                player.createConnectionRequest(server).fireAndForget());
            }
        }
    }

    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        // Save original server before redirecting to limbo
        Player player = event.getPlayer();
        if (!event.kickedDuringServerConnect() && event.getServer() != null) {
            String serverName = event.getServer().getServerInfo().getName();
            if (!serverName.equals(mainConfig.getLimboServer())) {
                playerDataConfig.setAwaitingReconnect(player.getUniqueId(), serverName);
            }
        }

        // If player was kicked from a server, send them to limbo unless they're disconnecting from the proxy
        if (!event.kickedDuringServerConnect()) {
            event.setResult(KickedFromServerEvent.RedirectPlayer.create(
                    proxy.getServer(mainConfig.getLimboServer()).orElse(null)
            ));
        }
    }

    @Subscribe(order = PostOrder.LAST)
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        RegisteredServer previousServer = event.getPreviousServer().orElse(null);
        ServerInfo nextServer = event.getServer().getServerInfo();
        String nextServerName = nextServer.getName();

        // If player manually connects to a non-limbo server while waiting for reconnect,
        // remove them from the reconnect list
        if (!nextServerName.equals(mainConfig.getLimboServer())) {
            playerDataConfig.setAwaitingReconnect(player.getUniqueId(), null);
        } else {
            // Player connected to limbo - handle priority redirect
            proxy.getScheduler()
                    .buildTask(this, () -> handlePriorityRedirect(player))
                    .delay(1, TimeUnit.SECONDS)
                    .schedule();
        }

        // Save last server if it's not limbo
        if (!nextServerName.equals(mainConfig.getLimboServer())) {
            playerDataConfig.setLastServer(player.getUniqueId(), nextServerName);
            logger.debug("Saved {}'s last connection as '{}'", player.getUsername(), nextServerName);
        }

        // Only broadcast switch message if it's not involving limbo
        if (!nextServerName.equals(mainConfig.getLimboServer()) &&
                (previousServer == null || !previousServer.getServerInfo().getName().equals(mainConfig.getLimboServer()))) {

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getUsername());
            placeholders.put("from", previousServer != null ? previousServer.getServerInfo().getName() : "Unknown");
            placeholders.put("to", nextServerName);

            proxy.getAllPlayers().stream()
                    .filter(p -> p.hasPermission("blox.admin"))
                    .forEach(admin -> {
                        Component message = messagesConfig.getComponent(
                                "player.switch",
                                "§6[SWITCH] §e%%player%%: %%from%% §f->§e %%to%%",
                                placeholders);
                        admin.sendMessage(message);
                    });
        }
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();

        // Send disconnect notification
        if (player.hasPermission("blox.admin")) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getUsername());

            proxy.getAllPlayers().stream()
                    .filter(p -> p.hasPermission("blox.admin"))
                    .forEach(admin -> {
                        Component message = messagesConfig.getComponent(
                                "player.disconnect",
                                "§c[DISCONNECT] %%player%%",
                                placeholders);
                        admin.sendMessage(message);
                    });
        }

        // Only clean up reconnect tracking if they're not in limbo
        if (!player.getCurrentServer()
                .map(conn -> conn.getServerInfo().getName().equals(mainConfig.getLimboServer()))
                .orElse(false)) {
            playerDataConfig.setAwaitingReconnect(player.getUniqueId(), null);
        }
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        Player player = event.getPlayer();

        // Record connection in player data
        playerDataConfig.playerConnected(player);

        // Check whitelist status
        if (whitelistConfig.isEnabled()) {
            // If admin-only mode is on, only let players with blox.admin join
            if (whitelistConfig.isAdminOnly()) {
                if (!player.hasPermission("blox.admin")) {
                    player.disconnect(messagesConfig.getComponent(
                            "player.admin_only",
                            "§f§lᴛʙ ɴᴇᴛᴡᴏʀᴋ §8§l| §7This server is in admin-only mode.",
                            null));
                    return;
                }
            } else {
                // Otherwise check if player is whitelisted or has admin permission
                if (!whitelistConfig.isWhitelisted(player.getUniqueId()) && !player.hasPermission("blox.admin")) {
                    player.disconnect(messagesConfig.getComponent(
                            "player.not_whitelisted",
                            "§f§lᴛʙ ɴᴇᴛᴡᴏʀᴋ §8§l| §7You are not whitelisted on this network.",
                            null));
                    return;
                }
            }
        } else if (!player.hasPermission("blox.connect")) {
            // If whitelist is disabled, fall back to the original permission check
            player.disconnect(messagesConfig.getComponent(
                    "player.not_authorized",
                    "§f§lᴛʙ ɴᴇᴛᴡᴏʀᴋ §8§l| §7You're not authorised to connect to this network.",
                    null));
            return;
        }

        // Initialize server status if not already done
        if (serverStatus.isEmpty()) {
            proxy.getAllServers().forEach(server ->
                    serverStatus.put(server, isServerOnline(server)));
        }

        // Connect to limbo first - priority redirect will happen after limbo connection
        connectToLimbo(player);

        // Send connect notification
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getUsername());

        proxy.getAllPlayers().stream()
                .filter(p -> p.hasPermission("blox.admin"))
                .forEach(admin -> {
                    Component message = messagesConfig.getComponent(
                            "player.connect",
                            "§a[CONNECT] %%player%%",
                            placeholders);
                    admin.sendMessage(message);
                });
    }

    private boolean isServerOnline(RegisteredServer server) {
        for (int i = 0; i < 3; i++) {
            CompletableFuture<ServerPing> ping = server.ping();
            try {
                ServerPing serverPing = ping.get(3, TimeUnit.SECONDS);
                if (serverPing != null) {
                    return true;
                }
            } catch (InterruptedException | ExecutionException | TimeoutException ignored) {
            }
        }
        return false;
    }

    private void broadcastMessage(Component message) {
        proxy.getAllPlayers().stream()
                .filter(p -> p.hasPermission("blox.admin"))
                .forEach(admin -> admin.sendMessage(message));
    }

    private void broadcastMessage(String message) {
        broadcastMessage(Component.text(message));
    }

    private Optional<RegisteredServer> findPriorityServer(Player player) {
        Map<Integer, String> priorityServers = new TreeMap<>(); // TreeMap to maintain priority order

        // Check all servers for matching priority permissions
        for (RegisteredServer server : proxy.getAllServers()) {
            String serverName = server.getServerInfo().getName();
            // Skip limbo server from priority checks
            if (serverName.equals(mainConfig.getLimboServer())) continue;

            // Check for priority permissions (up to priority 100 for safety)
            for (int priority = 1; priority <= 100; priority++) {
                String permission = "server.prioritise." + priority + "." + serverName;
                if (player.hasPermission(permission)) {
                    priorityServers.put(priority, serverName);
                    break; // Found priority for this server, move to next server
                }
            }
        }

        // Try servers in priority order
        for (Map.Entry<Integer, String> entry : priorityServers.entrySet()) {
            Optional<RegisteredServer> server = proxy.getServer(entry.getValue());
            if (server.isPresent() && isServerOnline(server.get())) {
                return server;
            }
        }

        return Optional.empty();
    }

    private void handlePriorityRedirect(Player player) {
        // Check if player is awaiting reconnect to a server
        String awaitingServer = playerDataConfig.getAwaitingReconnect(player.getUniqueId());
        if (awaitingServer != null) {
            Optional<RegisteredServer> awaitingServerOpt = proxy.getServer(awaitingServer);
            if (awaitingServerOpt.isPresent() && isServerOnline(awaitingServerOpt.get())) {
                // If the original server is back online, reconnect to it
                player.createConnectionRequest(awaitingServerOpt.get()).fireAndForget();
                logger.info("Reconnecting {} to previously disconnected server '{}'",
                        player.getUsername(), awaitingServer);
                return;
            } else {
                // If player is awaiting reconnect to a server that's still offline,
                // keep them in limbo and don't redirect them elsewhere
                logger.info("Keeping {} in limbo until '{}' comes back online",
                        player.getUsername(), awaitingServer);
                return;
            }
        }

        // ONLY if the player is NOT awaiting reconnect, follow the normal priority chain

        // Check for priority servers
        Optional<RegisteredServer> priorityServer = findPriorityServer(player);
        if (priorityServer.isPresent()) {
            RegisteredServer server = priorityServer.get();
            player.createConnectionRequest(server).fireAndForget();
            logger.info("Redirecting {} to priority server '{}'",
                    player.getUsername(),
                    server.getServerInfo().getName());
            return;
        }

        // If no priority server is available, try last server only if player has permission
        if (player.hasPermission("server.last_server")) {
            String lastServer = playerDataConfig.getLastServer(player.getUniqueId());
            if (lastServer != null && !lastServer.equals(mainConfig.getLimboServer())) {
                Optional<RegisteredServer> lastServerOpt = proxy.getServer(lastServer);
                if (lastServerOpt.isPresent() && isServerOnline(lastServerOpt.get())) {
                    player.createConnectionRequest(lastServerOpt.get()).fireAndForget();
                    logger.info("Redirecting {} to last server '{}' (has server.last_server permission)",
                            player.getUsername(), lastServer);
                    return;
                }
            }
        }

        // If no priority or last server available, try to find any available server
        // But only if the player is not awaiting reconnect to a specific server
        proxy.getAllServers().stream()
                .filter(server -> !server.getServerInfo().getName().equals(mainConfig.getLimboServer()))
                .filter(this::isServerOnline)
                .findFirst()
                .ifPresent(server -> {
                    player.createConnectionRequest(server).fireAndForget();
                    logger.info("Redirecting {} to fallback server '{}'",
                            player.getUsername(),
                            server.getServerInfo().getName());
                });
    }



    /**
     * Gets the proxy server instance
     * @return The proxy server
     */
    public ProxyServer getProxy() {
        return proxy;
    }

    /**
     * Gets the messages config
     * @return The messages config
     */
    public MessagesConfig getMessagesConfig() {
        return messagesConfig;
    }

    /**
     * Gets the main config
     * @return The main config
     */
    public MainConfig getMainConfig() {
        return mainConfig;
    }

    /**
     * Gets the player data config
     * @return The player data config
     */
    public PlayerDataConfig getPlayerDataConfig() {
        return playerDataConfig;
    }

}