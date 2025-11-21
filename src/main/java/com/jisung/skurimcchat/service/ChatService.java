package com.jisung.skurimcchat.service;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class ChatService {
    private final JavaPlugin plugin;
    private final Server server;
    private final FirebaseDatabase database;

    public ChatService(JavaPlugin plugin, Server server, FirebaseDatabase database) {
        this.plugin = plugin;
        this.server = server;
        this.database = database;
    }

    public void setupAppToMcListener() {
        try {
            DatabaseReference incomingRef = database.getReference("mc_chat/messages");

            incomingRef.addChildEventListener(new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                    try {
                        if (!snapshot.exists()) return;

                        GenericTypeIndicator<Map<String, Object>> indicator =
                                new GenericTypeIndicator<Map<String, Object>>() {};
                        Map<String, Object> data = snapshot.getValue(indicator);
                        if (data == null) return;

                        String direction = (String) data.get("direction");
                        if (!"app_to_mc".equals(direction)) return;

                        String username = (String) data.get("username");
                        String message = (String) data.get("message");

                        // Broadcast message to Minecraft server
                        Component formatted = Component.text("[스쿠리]", NamedTextColor.GREEN)
                                .append(Component.space())
                                .append(Component.text(username, NamedTextColor.YELLOW))
                                .append(Component.text(": " + message, NamedTextColor.WHITE));

                        server.broadcast(formatted);

                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to process app_to_mc message!", e);
                    }
                }

                @Override
                public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}

                @Override
                public void onChildRemoved(DataSnapshot snapshot) {}

                @Override
                public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}

                @Override
                public void onCancelled(DatabaseError error) {}
            });

            plugin.getLogger().info("Firebase app_to_mc listener registered!");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to set listener for app_to_mc messages!", e);
        }
    }

    public void sendMessageToApp(String username, String message, String uuid) {
        try {
            DatabaseReference ref = database.getReference("mc_chat/messages");

            Map<String, Object> msg = new HashMap<>();
            msg.put("username", username);
            msg.put("message", message);
            msg.put("timestamp", System.currentTimeMillis());
            msg.put("direction", "mc_to_app");
            msg.put("uuid", uuid);

            ref.push().setValueAsync(msg);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to send message to Firebase!", e);
        }
    }

    public void sendSystemMessage(String text) {
        try {
            DatabaseReference ref = database.getReference("mc_chat/messages");

            Map<String, Object> msg = new HashMap<>();
            msg.put("username", "SYSTEM");
            msg.put("message", text);
            msg.put("timestamp", System.currentTimeMillis());
            msg.put("direction", "system");

            ref.push().setValueAsync(msg);
            plugin.getLogger().info("[SystemMessage] " + text);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to send system message to Firebase!", e);
        }
    }
}

