package com.jisung.skurimcchat.restriction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FrozenPlayerManager {
    private final JavaPlugin plugin;
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();

    public FrozenPlayerManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isFrozen(UUID uuid) {
        return frozenPlayers.contains(uuid);
    }

    public void freezePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        frozenPlayers.add(uuid);

        // Apply restrictions
        player.setWalkSpeed(0f);
        player.setFlySpeed(0f);
        player.setInvulnerable(true);
        player.setCollidable(false);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setSilent(true);
        player.setInvisible(true);
        player.setCanPickupItems(false);

        // Show title
        Component title = Component.text("스쿠리 서버 접속 불가", NamedTextColor.RED);
        Component subtitle = Component.text("스쿠리 앱에서 계정 등록 후 접속해주세요.", NamedTextColor.YELLOW);
        player.showTitle(net.kyori.adventure.title.Title.title(title, subtitle));

        // Schedule repeating title every 5 seconds
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!frozenPlayers.contains(uuid) || !player.isOnline()) {
                task.cancel();
                return;
            }
            player.showTitle(net.kyori.adventure.title.Title.title(title, subtitle));
        }, 100L, 100L);

        // Continuous warning message task
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!frozenPlayers.contains(uuid) || !player.isOnline()) {
                task.cancel();
                return;
            }
            player.sendMessage(Component.text("⚠ 스쿠리 서버 계정 등록이 필요합니다!", NamedTextColor.RED));
        }, 0L, 100L);

        // Hide this player from all others
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (!other.getUniqueId().equals(uuid)) {
                other.hidePlayer(plugin, player);
            }
        }
    }

    public void unfreezePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        frozenPlayers.remove(uuid);

        // Restore normal state
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setInvulnerable(false);
        player.setInvisible(false);
        player.setSilent(false);
        player.setCollidable(true);
        player.setCanPickupItems(true);

        // Unhide player
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (!other.getUniqueId().equals(uuid)) {
                other.showPlayer(plugin, player);
            }
        }
    }

    public void removePlayer(UUID uuid) {
        frozenPlayers.remove(uuid);
    }
}

