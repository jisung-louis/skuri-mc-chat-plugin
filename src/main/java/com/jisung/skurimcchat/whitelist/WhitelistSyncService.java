package com.jisung.skurimcchat.whitelist;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import org.bukkit.plugin.java.JavaPlugin;

public class WhitelistSyncService {
    private final JavaPlugin plugin;
    private final FirebaseDatabase database;
    private final WhitelistManager whitelistManager;

    public WhitelistSyncService(JavaPlugin plugin, FirebaseDatabase database, WhitelistManager whitelistManager) {
        this.plugin = plugin;
        this.database = database;
        this.whitelistManager = whitelistManager;
    }

    public void setupSync() {
        // Sync whitelist enabled state
        DatabaseReference enabledRef = database.getReference("whitelist/enabled");
        enabledRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Boolean enabled = snapshot.getValue(Boolean.class);
                if (enabled == null) enabled = false;
                whitelistManager.setWhitelistEnabled(enabled);
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });

        // Sync whitelist players
        DatabaseReference playersRef = database.getReference("whitelist/players");
        playersRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String prev) {
                handleWhitelistAdd(snapshot);
            }

            @Override
            public void onChildRemoved(DataSnapshot snapshot) {
                handleWhitelistRemove(snapshot);
            }

            @Override
            public void onChildChanged(DataSnapshot snapshot, String prev) {}

            @Override
            public void onChildMoved(DataSnapshot snapshot, String prev) {}

            @Override
            public void onCancelled(DatabaseError error) {}
        });

        // Initial sync
        playersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                whitelistManager.syncFromSnapshot(snapshot);
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void handleWhitelistAdd(DataSnapshot snapshot) {
        try {
            String key = snapshot.getKey();
            if (key == null) return;

            java.util.UUID uuid = java.util.UUID.fromString(key);
            whitelistManager.addPlayer(uuid);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[Whitelist] Add failed!", e);
        }
    }

    private void handleWhitelistRemove(DataSnapshot snapshot) {
        try {
            String key = snapshot.getKey();
            if (key == null) return;

            java.util.UUID uuid = java.util.UUID.fromString(key);
            whitelistManager.removePlayer(uuid);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[Whitelist] Remove failed!", e);
        }
    }
}

