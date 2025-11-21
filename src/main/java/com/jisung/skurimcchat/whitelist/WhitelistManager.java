package com.jisung.skurimcchat.whitelist;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WhitelistManager {
    private final JavaPlugin plugin;
    private final Server server;
    private final Set<UUID> whitelistedPlayers = ConcurrentHashMap.newKeySet();
    private volatile boolean whitelistEnabled = false;

    public WhitelistManager(JavaPlugin plugin, Server server) {
        this.plugin = plugin;
        this.server = server;
    }

    public boolean isWhitelistEnabled() {
        return whitelistEnabled;
    }

    public void setWhitelistEnabled(boolean enabled) {
        this.whitelistEnabled = enabled;
        server.setWhitelist(enabled);
        plugin.getLogger().info("[Whitelist] Enabled = " + enabled);
    }

    public boolean isWhitelisted(UUID uuid) {
        return whitelistedPlayers.contains(uuid);
    }

    public void addPlayer(UUID uuid) {
        whitelistedPlayers.add(uuid);
        server.getScheduler().runTask(plugin, () -> {
            OfflinePlayer offline = server.getOfflinePlayer(uuid);
            offline.setWhitelisted(true);
            plugin.getLogger().info("[Whitelist] Added " + uuid);
        });
    }

    public void removePlayer(UUID uuid) {
        whitelistedPlayers.remove(uuid);
        server.getScheduler().runTask(plugin, () -> {
            OfflinePlayer offline = server.getOfflinePlayer(uuid);
            offline.setWhitelisted(false);

            Player online = server.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                online.kick(net.kyori.adventure.text.Component.text(
                        "화이트리스트에서 제외되어 접속이 종료됩니다.",
                        net.kyori.adventure.text.format.NamedTextColor.RED
                ));
            }

            plugin.getLogger().info("[Whitelist] Removed " + uuid);
        });
    }

    public void clear() {
        whitelistedPlayers.clear();
    }

    public void syncFromSnapshot(com.google.firebase.database.DataSnapshot snapshot) {
        whitelistedPlayers.clear();
        for (com.google.firebase.database.DataSnapshot child : snapshot.getChildren()) {
            try {
                UUID uuid = UUID.fromString(child.getKey());
                whitelistedPlayers.add(uuid);

                OfflinePlayer offline = server.getOfflinePlayer(uuid);
                offline.setWhitelisted(true);
            } catch (Exception e) {
                plugin.getLogger().warning("[Whitelist] Invalid UUID: " + child.getKey());
            }
        }
        plugin.getLogger().info("[Whitelist] Initial sync — " + whitelistedPlayers.size() + " players");
    }

    public int getWhitelistSize() {
        return whitelistedPlayers.size();
    }
}

