package com.jisung.skurimcchat.whitelist;

import com.jisung.skurimcchat.bridge.SpringBridgeModels;
import org.bukkit.plugin.java.JavaPlugin;

public class WhitelistSyncService {
    private final JavaPlugin plugin;
    private final WhitelistManager whitelistManager;
    private final PlayerVerificationService playerVerificationService;

    public WhitelistSyncService(
            JavaPlugin plugin,
            WhitelistManager whitelistManager,
            PlayerVerificationService playerVerificationService
    ) {
        this.plugin = plugin;
        this.whitelistManager = whitelistManager;
        this.playerVerificationService = playerVerificationService;
    }

    public void applySnapshot(SpringBridgeModels.WhitelistSnapshot snapshot) {
        whitelistManager.applySnapshot(snapshot.players());
        playerVerificationService.recheckOnlineBedrockPlayers();
        plugin.getLogger().info("[Whitelist] Initial snapshot synced.");
    }

    public void applyUpsert(SpringBridgeModels.WhitelistEntry entry) {
        whitelistManager.upsert(entry);
        playerVerificationService.recheckOnlineBedrockPlayers();
    }

    public void applyRemove(SpringBridgeModels.WhitelistEntry entry) {
        whitelistManager.remove(entry);
        playerVerificationService.recheckOnlineBedrockPlayers();
    }
}
