package com.jisung.skurimcchat.listener;

import com.jisung.skurimcchat.restriction.FrozenPlayerManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class FrozenPlayerChatListener implements Listener {
    private final FrozenPlayerManager frozenPlayerManager;

    public FrozenPlayerChatListener(FrozenPlayerManager frozenPlayerManager) {
        this.frozenPlayerManager = frozenPlayerManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncChatEvent event) {
        if (frozenPlayerManager.isFrozen(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage(Component.text("스쿠리 서버에서 계정 등록이 필요합니다.", NamedTextColor.RED));
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (frozenPlayerManager.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("화이트리스트 승인이 필요합니다.", NamedTextColor.RED));
        }
    }
}

