package com.jisung.skurimcchat;

import com.jisung.skurimcchat.bridge.MinecraftIdentityResolver;
import com.jisung.skurimcchat.bridge.SpringBridgeClient;
import com.jisung.skurimcchat.bridge.SpringBridgeConfig;
import com.jisung.skurimcchat.bridge.SpringBridgeEventDispatcher;
import com.jisung.skurimcchat.listener.ChatListener;
import com.jisung.skurimcchat.listener.DeathListener;
import com.jisung.skurimcchat.listener.FrozenPlayerChatListener;
import com.jisung.skurimcchat.listener.PlayerConnectionListener;
import com.jisung.skurimcchat.listener.TerrorPreventionListener;
import com.jisung.skurimcchat.restriction.FrozenPlayerManager;
import com.jisung.skurimcchat.restriction.FrozenPlayerRestrictionListener;
import com.jisung.skurimcchat.service.ChatService;
import com.jisung.skurimcchat.service.ServerStatusService;
import com.jisung.skurimcchat.whitelist.PlayerVerificationService;
import com.jisung.skurimcchat.whitelist.WhitelistManager;
import com.jisung.skurimcchat.whitelist.WhitelistSyncService;
import org.bukkit.plugin.java.JavaPlugin;

public final class Skurimcchat extends JavaPlugin {

    // Services
    private SpringBridgeClient springBridgeClient;
    private ChatService chatService;
    private ServerStatusService serverStatusService;
    private SpringBridgeConfig springBridgeConfig;
    private MinecraftIdentityResolver identityResolver;
    
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
    private TerrorPreventionListener terrorPreventionListener;

    @Override
    public void onEnable() {
        getLogger().info("스쿠리 플러그인 시작!");

        // Ensure plugin data folder exists
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Load config.yml
        saveDefaultConfig();
        try {
            springBridgeConfig = SpringBridgeConfig.from(this);
        } catch (IllegalArgumentException e) {
            getLogger().severe(e.getMessage());
            return;
        }

        identityResolver = new MinecraftIdentityResolver();
        frozenPlayerManager = new FrozenPlayerManager(this);
        whitelistManager = new WhitelistManager(this, getServer(), identityResolver);
        whitelistManager.activateProtection();

        playerVerificationService = new PlayerVerificationService(
                this, getServer(), whitelistManager, frozenPlayerManager, identityResolver);
        whitelistSyncService = new WhitelistSyncService(this, whitelistManager, playerVerificationService);

        springBridgeClient = new SpringBridgeClient(
                this,
                springBridgeConfig,
                null
        );
        chatService = new ChatService(this, getServer(), springBridgeClient, identityResolver);
        springBridgeClient.setEventListener(new SpringBridgeEventDispatcher(chatService, whitelistSyncService));
        serverStatusService = new ServerStatusService(
                this,
                getServer(),
                springBridgeClient,
                springBridgeConfig,
                identityResolver
        );

        springBridgeClient.start();
        boolean initialSnapshotReady = springBridgeClient.awaitInitialWhitelistSnapshot(10_000L);
        if (initialSnapshotReady) {
            getLogger().info("초기 화이트리스트 스냅샷을 수신했어요.");
        } else {
            getLogger().warning("초기 화이트리스트 스냅샷 대기 시간이 초과되었어요. 연결이 복구되면 자동 동기화됩니다.");
        }

        // Initialize and register event listeners
        chatListener = new ChatListener(chatService, frozenPlayerManager);
        playerConnectionListener = new PlayerConnectionListener(
                whitelistManager, playerVerificationService, chatService,
                serverStatusService, frozenPlayerManager);
        deathListener = new DeathListener(chatService);
        frozenPlayerChatListener = new FrozenPlayerChatListener(frozenPlayerManager);
        frozenPlayerRestrictionListener = new FrozenPlayerRestrictionListener(frozenPlayerManager);
        terrorPreventionListener = new TerrorPreventionListener(this);

        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(playerConnectionListener, this);
        getServer().getPluginManager().registerEvents(deathListener, this);
        getServer().getPluginManager().registerEvents(frozenPlayerChatListener, this);
        getServer().getPluginManager().registerEvents(frozenPlayerRestrictionListener, this);
        getServer().getPluginManager().registerEvents(terrorPreventionListener, this);

        // Send startup message
        chatService.sendServerSystemMessage("스쿠리 마인크래프트 서버가 열렸어요.", "STARTUP");

        // Initialize server status
        serverStatusService.updateServerOnlineFlag(true);
        serverStatusService.updatePlayerList();
        serverStatusService.startHeartbeat();
    }

    @Override
    public void onDisable() {
        getLogger().info("스쿠리 플러그인 종료!");
        
        if (chatService != null) {
            chatService.sendServerSystemMessage("스쿠리 마인크래프트 서버가 닫혔어요.", "SHUTDOWN");
        }

        if (serverStatusService != null) {
            serverStatusService.updateServerOnlineFlag(false);
            serverStatusService.clearPlayersOnShutdown();
            serverStatusService.stopHeartbeat();
        }

        if (springBridgeClient != null) {
            springBridgeClient.stop();
        }
    }
}
