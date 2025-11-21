package com.jisung.skurimcchat.listener;

import com.jisung.skurimcchat.DeathMessageMapper;
import com.jisung.skurimcchat.EntityDeathMessageFormatter;
import com.jisung.skurimcchat.service.ChatService;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class DeathListener implements Listener {
    private final ChatService chatService;

    public DeathListener(ChatService chatService) {
        this.chatService = chatService;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        String rawDeathMessage;

        var deathComp = event.deathMessage();
        if (deathComp != null) {
            rawDeathMessage = PlainTextComponentSerializer.plainText().serialize(deathComp);
        } else {
            rawDeathMessage = event.getEntity().getName() + " died";
        }

        String translated = DeathMessageMapper.translate(rawDeathMessage);
        chatService.sendSystemMessage(translated);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        String msg = EntityDeathMessageFormatter.format(event);
        if (msg != null && !msg.isEmpty()) {
            chatService.sendSystemMessage(msg);
        }
    }
}

