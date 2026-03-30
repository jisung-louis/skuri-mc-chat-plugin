package com.jisung.skurimcchat.service;

import com.jisung.skurimcchat.bridge.MinecraftIdentityResolver;
import com.jisung.skurimcchat.bridge.SpringBridgeClient;
import com.jisung.skurimcchat.bridge.SpringBridgeConfig;
import com.jisung.skurimcchat.bridge.SpringBridgeModels;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class ServerStatusService {
    private final JavaPlugin plugin;
    private final Server server;
    private final SpringBridgeClient springBridgeClient;
    private final SpringBridgeConfig springBridgeConfig;
    private final MinecraftIdentityResolver identityResolver;
    private BukkitTask heartbeatTask;

    public ServerStatusService(
            JavaPlugin plugin,
            Server server,
            SpringBridgeClient springBridgeClient,
            SpringBridgeConfig springBridgeConfig,
            MinecraftIdentityResolver identityResolver
    ) {
        this.plugin = plugin;
        this.server = server;
        this.springBridgeClient = springBridgeClient;
        this.springBridgeConfig = springBridgeConfig;
        this.identityResolver = identityResolver;
    }

    public void startHeartbeat() {
        stopHeartbeat();
        heartbeatTask = server.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                pushServerState(true);
                updatePlayerList();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[ServerStatus] Failed to send heartbeat!", e);
            }
        }, 0L, springBridgeConfig.heartbeatIntervalTicks());
    }

    public void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
    }

    public void updateServerOnlineFlag(boolean online) {
        try {
            pushServerState(online);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[ServerStatus] Failed to update online flag!", e);
        }
    }

    public void updatePlayerList() {
        try {
            List<SpringBridgeModels.OnlinePlayer> players = new ArrayList<>();
            for (Player player : server.getOnlinePlayers()) {
                players.add(new SpringBridgeModels.OnlinePlayer(
                        player.getName(),
                        identityResolver.resolveEdition(player),
                        identityResolver.resolveIdentity(player)
                ));
            }

            springBridgeClient.updateOnlinePlayers(new SpringBridgeModels.OnlinePlayersUpsertRequest(
                    Instant.now().toString(),
                    players
            ));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[ServerStatus] Failed to update player list!", e);
        }
    }

    public void clearPlayersOnShutdown() {
        try {
            springBridgeClient.updateOnlinePlayers(new SpringBridgeModels.OnlinePlayersUpsertRequest(
                    Instant.now().toString(),
                    List.of()
            ));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[ServerStatus] Failed to clear players on shutdown!", e);
        }
    }

    private void pushServerState(boolean online) {
        springBridgeClient.updateServerState(new SpringBridgeModels.ServerStateUpsertRequest(
                online,
                online ? server.getOnlinePlayers().size() : 0,
                server.getMaxPlayers(),
                resolveServerVersion(),
                springBridgeConfig.serverAddress(),
                springBridgeConfig.mapUrl(),
                Instant.now().toString()
        ));
    }

    private String resolveServerVersion() {
        if (springBridgeConfig.serverVersion() != null && !springBridgeConfig.serverVersion().isBlank()) {
            return springBridgeConfig.serverVersion();
        }
        return server.getBukkitVersion();
    }
}
