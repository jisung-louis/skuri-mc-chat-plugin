package com.jisung.skurimcchat.service;

import com.jisung.skurimcchat.bridge.MinecraftIdentityResolver;
import com.jisung.skurimcchat.bridge.SpringBridgeClient;
import com.jisung.skurimcchat.bridge.SpringBridgeModels;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class ChatService {
    private final JavaPlugin plugin;
    private final Server server;
    private final SpringBridgeClient springBridgeClient;
    private final MinecraftIdentityResolver identityResolver;

    public ChatService(
            JavaPlugin plugin,
            Server server,
            SpringBridgeClient springBridgeClient,
            MinecraftIdentityResolver identityResolver
    ) {
        this.plugin = plugin;
        this.server = server;
        this.springBridgeClient = springBridgeClient;
        this.identityResolver = identityResolver;
    }

    public void broadcastMessageFromApp(SpringBridgeModels.AppChatMessage message) {
        try {
            Component formatted = Component.text("[스쿠리]", NamedTextColor.GREEN)
                    .append(Component.space())
                    .append(Component.text(message.senderName(), NamedTextColor.YELLOW))
                    .append(Component.text(": " + message.text(), NamedTextColor.WHITE));

            server.broadcast(formatted);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to broadcast app_to_mc message!", e);
        }
    }

    public void sendPlayerMessage(Player player, String message) {
        try {
            springBridgeClient.sendChatMessage(
                    player.getName(),
                    identityResolver.resolveIdentity(player),
                    identityResolver.resolveEdition(player),
                    message
            );
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to send player chat to Spring bridge!", e);
        }
    }

    public void sendPlayerSystemMessage(Player player, String text, String systemType) {
        sendSystemMessage(
                systemType,
                player.getName(),
                identityResolver.resolveIdentity(player),
                identityResolver.resolveEdition(player),
                text
        );
    }

    public void sendServerSystemMessage(String text, String systemType) {
        sendSystemMessage(systemType, "SYSTEM", "server", "BEDROCK", text);
    }

    private void sendSystemMessage(
            String systemType,
            String senderName,
            String minecraftUuid,
            String edition,
            String text
    ) {
        try {
            springBridgeClient.sendSystemMessage(systemType, senderName, minecraftUuid, edition, text);
            plugin.getLogger().info("[SystemMessage] " + text);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to send system message to Spring bridge!", e);
        }
    }
}
