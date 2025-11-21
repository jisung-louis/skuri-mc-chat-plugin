package com.jisung.skurimcchat;

import com.jisung.skurimcchat.listener.ChatListener;
import com.jisung.skurimcchat.listener.DeathListener;
import com.jisung.skurimcchat.listener.FrozenPlayerChatListener;
import com.jisung.skurimcchat.listener.PlayerConnectionListener;
import com.jisung.skurimcchat.restriction.FrozenPlayerManager;
import com.jisung.skurimcchat.restriction.FrozenPlayerRestrictionListener;
import com.jisung.skurimcchat.service.ChatService;
import com.jisung.skurimcchat.service.FirebaseService;
import com.jisung.skurimcchat.service.ServerStatusService;
import com.jisung.skurimcchat.whitelist.PlayerVerificationService;
import com.jisung.skurimcchat.whitelist.WhitelistManager;
import com.jisung.skurimcchat.whitelist.WhitelistSyncService;
import org.bukkit.plugin.java.JavaPlugin;

public final class Skurimcchat extends JavaPlugin {

    // Services
    private FirebaseService firebaseService;
    private ChatService chatService;
    private ServerStatusService serverStatusService;
    
    // Whitelist
    private WhitelistManager whitelistManager;
    private WhitelistSyncService whitelistSyncService;
    private PlayerVerificationService playerVerificationService;
    
    // Frozen Player Management
    private FrozenPlayerManager frozenPlayerManager;
    
    // Listeners
    private ChatListener chatListener;
    private PlayerConnectionListener playerConnectionListener;
    private DeathListener deathListener;
    private FrozenPlayerChatListener frozenPlayerChatListener;
    private FrozenPlayerRestrictionListener frozenPlayerRestrictionListener;

    @Override
    public void onEnable() {
        getLogger().info("스쿠리 플러그인 시작!");

        // Ensure plugin data folder exists
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Load config.yml
        saveDefaultConfig();
        String dbUrl = getConfig().getString("firebase.databaseUrl");
        if (dbUrl == null || dbUrl.isEmpty()) {
            getLogger().severe("firebase.databaseUrl is missing in config.yml!");
            return;
        }

        // Initialize Firebase
        firebaseService = new FirebaseService(this);
        if (!firebaseService.initialize(dbUrl)) {
            getLogger().severe("Failed to initialize Firebase! Plugin will not function correctly.");
            return;
        }

        var database = firebaseService.getDatabase();

        // Initialize services
        chatService = new ChatService(this, getServer(), database);
        serverStatusService = new ServerStatusService(this, getServer(), database);

        // Initialize whitelist components
        whitelistManager = new WhitelistManager(this, getServer());
        whitelistSyncService = new WhitelistSyncService(this, database, whitelistManager);
        
        // Initialize frozen player manager
        frozenPlayerManager = new FrozenPlayerManager(this);
        
        // Initialize player verification service
        playerVerificationService = new PlayerVerificationService(
                this, database, whitelistManager, frozenPlayerManager);

        // Setup Firebase listeners
        chatService.setupAppToMcListener();
        whitelistSyncService.setupSync();

        // Initialize and register event listeners
        chatListener = new ChatListener(chatService, frozenPlayerManager);
        playerConnectionListener = new PlayerConnectionListener(
                whitelistManager, playerVerificationService, chatService,
                serverStatusService, frozenPlayerManager);
        deathListener = new DeathListener(chatService);
        frozenPlayerChatListener = new FrozenPlayerChatListener(frozenPlayerManager);
        frozenPlayerRestrictionListener = new FrozenPlayerRestrictionListener(frozenPlayerManager);

        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(playerConnectionListener, this);
        getServer().getPluginManager().registerEvents(deathListener, this);
        getServer().getPluginManager().registerEvents(frozenPlayerChatListener, this);
        getServer().getPluginManager().registerEvents(frozenPlayerRestrictionListener, this);

        // Send startup message
        chatService.sendSystemMessage("스쿠리 마인크래프트 서버가 열렸어요.");

        // Initialize server status
        serverStatusService.updateServerOnlineFlag(true);
        serverStatusService.updatePlayerList();
        serverStatusService.startHeartbeat();
    }

    @Override
    public void onDisable() {
        getLogger().info("스쿠리 플러그인 종료!");
        
        if (chatService != null) {
            chatService.sendSystemMessage("스쿠리 마인크래프트 서버가 닫혔어요.");
        }

        if (serverStatusService != null) {
            serverStatusService.updateServerOnlineFlag(false);
            serverStatusService.clearPlayersOnShutdown();
            serverStatusService.stopHeartbeat();
        }
    }
}
