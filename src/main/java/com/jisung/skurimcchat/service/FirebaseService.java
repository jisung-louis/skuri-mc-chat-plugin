package com.jisung.skurimcchat.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.FirebaseDatabase;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.util.logging.Level;

public class FirebaseService {
    private final JavaPlugin plugin;

    public FirebaseService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean initialize(String databaseUrl) {
        try {
            File serviceAccount = new File(plugin.getDataFolder(), "serviceAccount.json");
            FileInputStream fis = new FileInputStream(serviceAccount);

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(fis))
                    .setDatabaseUrl(databaseUrl)
                    .build();

            FirebaseApp.initializeApp(options);
            plugin.getLogger().info("Firebase initialized!");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize Firebase!", e);
            return false;
        }
    }

    public FirebaseDatabase getDatabase() {
        return FirebaseDatabase.getInstance();
    }
}

