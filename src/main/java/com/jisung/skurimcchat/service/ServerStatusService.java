package com.jisung.skurimcchat.service;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class ServerStatusService {
    private final JavaPlugin plugin;
    private final Server server;
    private final FirebaseDatabase database;
    private BukkitTask heartbeatTask;

    public ServerStatusService(JavaPlugin plugin, Server server, FirebaseDatabase database) {
        this.plugin = plugin;
        this.server = server;
        this.database = database;
    }

    public void startHeartbeat() {
        try {
            DatabaseReference statusRef = database.getReference("serverStatus");

            // 10초마다 서버 상태 Heartbeat (비동기)
            heartbeatTask = server.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                try {
                    Map<String, Object> data = new HashMap<>();
                    data.put("online", true);
                    data.put("playerCount", server.getOnlinePlayers().size());
                    data.put("maxPlayers", server.getMaxPlayers());
                    data.put("updatedAt", System.currentTimeMillis());

                    statusRef.updateChildrenAsync(data);
                    // Also update current online players list every heartbeat
                    DatabaseReference playersRef = database.getReference("serverStatus/players");

                    Map<String, Object> players = new HashMap<>();
                    for (Player p : server.getOnlinePlayers()) {
                        Map<String, Object> info = new HashMap<>();
                        info.put("name", p.getName());
                        info.put("uuid", p.getUniqueId().toString());
                        players.put(p.getUniqueId().toString(), info);
                    }

                    playersRef.setValueAsync(players);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "[ServerStatus] Failed to send heartbeat!", e);
                }
            }, 0L, 200L); // 200 tick ~= 10초
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[ServerStatus] Failed to start heartbeat!", e);
        }
    }

    public void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
    }

    public void updateServerOnlineFlag(boolean online) {
        try {
            DatabaseReference statusRef = database.getReference("serverStatus");
            Map<String, Object> data = new HashMap<>();
            data.put("online", online);
            data.put("updatedAt", System.currentTimeMillis());
            if (!online) {
                data.put("playerCount", 0);
            }
            statusRef.updateChildrenAsync(data);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[ServerStatus] Failed to update online flag!", e);
        }
    }

    public void updatePlayerList() {
        try {
            DatabaseReference playersRef = database.getReference("serverStatus/players");

            Map<String, Object> players = new HashMap<>();
            for (Player p : server.getOnlinePlayers()) {
                Map<String, Object> info = new HashMap<>();
                info.put("name", p.getName());
                info.put("uuid", p.getUniqueId().toString());
                players.put(p.getUniqueId().toString(), info);
            }

            playersRef.setValueAsync(players);

            // playerCount 동기화
            database.getReference("serverStatus/playerCount")
                    .setValueAsync(players.size());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[ServerStatus] Failed to update player list!", e);
        }
    }

    public void clearPlayersOnShutdown() {
        try {
            database.getReference("serverStatus/players")
                    .setValueAsync(null);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[ServerStatus] Failed to clear players on shutdown!", e);
        }
    }
}

