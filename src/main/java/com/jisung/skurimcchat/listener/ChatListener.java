package com.jisung.skurimcchat.listener;

import com.jisung.skurimcchat.restriction.FrozenPlayerManager;
import com.jisung.skurimcchat.service.ChatService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {
    private final ChatService chatService;
    private final FrozenPlayerManager frozenPlayerManager;

    public ChatListener(ChatService chatService, FrozenPlayerManager frozenPlayerManager) {
        this.chatService = chatService;
        this.frozenPlayerManager = frozenPlayerManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChat(AsyncChatEvent event) {
        // Frozen 플레이어는 Firebase로 전송하지 않음
        // 이벤트 취소 체크와 함께 직접 Frozen 체크도 수행 (이중 안전장치)
        if (event.isCancelled() || frozenPlayerManager.isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }

        String playerName = event.getPlayer().getName();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        String uuid = event.getPlayer().getUniqueId().toString();

        chatService.sendMessageToApp(playerName, message, uuid);
    }
}

