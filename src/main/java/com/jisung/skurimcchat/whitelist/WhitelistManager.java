package com.jisung.skurimcchat.whitelist;

import com.jisung.skurimcchat.bridge.MinecraftIdentityResolver;
import com.jisung.skurimcchat.bridge.SpringBridgeModels;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class WhitelistManager {
    private final JavaPlugin plugin;
    private final Server server;
    private final MinecraftIdentityResolver identityResolver;
    private final Set<UUID> javaPlayers = ConcurrentHashMap.newKeySet();
    private final Map<String, SpringBridgeModels.WhitelistEntry> bedrockPlayers = new ConcurrentHashMap<>();
    private volatile boolean bridgeReady = false;

    public WhitelistManager(JavaPlugin plugin, Server server, MinecraftIdentityResolver identityResolver) {
        this.plugin = plugin;
        this.server = server;
        this.identityResolver = identityResolver;
    }

    public void activateProtection() {
        server.setWhitelist(true);
        plugin.getLogger().info("[Whitelist] Spring bridge protection enabled.");
    }

    public boolean isBridgeReady() {
        return bridgeReady;
    }

    public boolean isWhitelisted(UUID uuid) {
        return bridgeReady && javaPlayers.contains(uuid);
    }

    public boolean hasBedrockAccount(String playerName) {
        return bridgeReady && bedrockPlayers.containsKey(identityResolver.toStoredBedrockName(playerName));
    }

    public void applySnapshot(List<SpringBridgeModels.WhitelistEntry> entries) {
        Map<String, SpringBridgeModels.WhitelistEntry> nextBedrockPlayers = new HashMap<>();
        Set<UUID> nextJavaPlayers = ConcurrentHashMap.newKeySet();

        for (SpringBridgeModels.WhitelistEntry entry : entries) {
            if ("JAVA".equalsIgnoreCase(entry.edition())) {
                try {
                    nextJavaPlayers.add(identityResolver.toJavaUuid(entry.normalizedKey()));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().log(Level.WARNING, "[Whitelist] Invalid Java UUID: " + entry.normalizedKey(), e);
                }
                continue;
            }

            if (entry.storedName() != null && !entry.storedName().isBlank()) {
                nextBedrockPlayers.put(entry.storedName(), entry);
            }
        }

        javaPlayers.clear();
        javaPlayers.addAll(nextJavaPlayers);
        bedrockPlayers.clear();
        bedrockPlayers.putAll(nextBedrockPlayers);
        bridgeReady = true;

        server.getScheduler().runTask(plugin, () -> {
            for (OfflinePlayer offlinePlayer : server.getWhitelistedPlayers()) {
                if (!nextJavaPlayers.contains(offlinePlayer.getUniqueId())) {
                    offlinePlayer.setWhitelisted(false);
                }
            }
            for (UUID uuid : nextJavaPlayers) {
                server.getOfflinePlayer(uuid).setWhitelisted(true);
            }
            kickRemovedJavaPlayers(nextJavaPlayers);
            plugin.getLogger().info("[Whitelist] Snapshot applied. java=" + nextJavaPlayers.size()
                    + ", bedrock=" + nextBedrockPlayers.size());
        });
    }

    public void upsert(SpringBridgeModels.WhitelistEntry entry) {
        bridgeReady = true;

        if ("JAVA".equalsIgnoreCase(entry.edition())) {
            UUID uuid = identityResolver.toJavaUuid(entry.normalizedKey());
            javaPlayers.add(uuid);
            server.getScheduler().runTask(plugin, () -> {
                server.getOfflinePlayer(uuid).setWhitelisted(true);
                plugin.getLogger().info("[Whitelist] Added Java player " + uuid);
            });
            return;
        }

        if (entry.storedName() != null && !entry.storedName().isBlank()) {
            bedrockPlayers.put(entry.storedName(), entry);
            plugin.getLogger().info("[Whitelist] Added Bedrock player " + entry.storedName());
        }
    }

    public void remove(SpringBridgeModels.WhitelistEntry entry) {
        bridgeReady = true;

        if ("JAVA".equalsIgnoreCase(entry.edition())) {
            UUID uuid = identityResolver.toJavaUuid(entry.normalizedKey());
            javaPlayers.remove(uuid);
            server.getScheduler().runTask(plugin, () -> {
                OfflinePlayer offlinePlayer = server.getOfflinePlayer(uuid);
                offlinePlayer.setWhitelisted(false);

                Player onlinePlayer = server.getPlayer(uuid);
                if (onlinePlayer != null && onlinePlayer.isOnline()) {
                    onlinePlayer.kick(Component.text("화이트리스트에서 제외되어 접속이 종료됩니다.", NamedTextColor.RED));
                }

                plugin.getLogger().info("[Whitelist] Removed Java player " + uuid);
            });
            return;
        }

        if (entry.storedName() != null && !entry.storedName().isBlank()) {
            bedrockPlayers.remove(entry.storedName());
            plugin.getLogger().info("[Whitelist] Removed Bedrock player " + entry.storedName());
        }
    }

    public int getWhitelistSize() {
        return javaPlayers.size() + bedrockPlayers.size();
    }

    private void kickRemovedJavaPlayers(Set<UUID> nextJavaPlayers) {
        for (Player player : server.getOnlinePlayers()) {
            if (identityResolver.isBedrockPlayer(player.getName())) {
                continue;
            }
            if (!nextJavaPlayers.contains(player.getUniqueId())) {
                player.kick(Component.text("화이트리스트에서 제외되어 접속이 종료됩니다.", NamedTextColor.RED));
            }
        }
    }
}
