package com.jisung.skurimcchat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

import java.util.logging.Level;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class Skurimcchat extends JavaPlugin implements Listener {

    private final Set<UUID> whitelistedPlayers = ConcurrentHashMap.newKeySet();
    private volatile boolean whitelistEnabled = false;
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();

    private void startServerStatusHeartbeat() {
        try {
            DatabaseReference statusRef = FirebaseDatabase.getInstance().getReference("serverStatus");

            // 10초마다 서버 상태 Heartbeat (비동기)
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                try {
                    Map<String, Object> data = new HashMap<>();
                    data.put("online", true);
                    data.put("playerCount", getServer().getOnlinePlayers().size());
                    data.put("maxPlayers", getServer().getMaxPlayers());
                    data.put("updatedAt", System.currentTimeMillis());

                    statusRef.updateChildrenAsync(data);
                    // Also update current online players list every heartbeat
                    DatabaseReference playersRef = FirebaseDatabase.getInstance()
                            .getReference("serverStatus/players");

                    Map<String, Object> players = new HashMap<>();
                    for (Player p : getServer().getOnlinePlayers()) {
                        Map<String, Object> info = new HashMap<>();
                        info.put("name", p.getName());
                        info.put("uuid", p.getUniqueId().toString());
                        players.put(p.getUniqueId().toString(), info);
                    }

                    playersRef.setValueAsync(players);
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "[ServerStatus] Failed to send heartbeat!", e);
                }
            }, 0L, 200L); // 200 tick ~= 10초
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "[ServerStatus] Failed to start heartbeat!", e);
        }
    }

    private void updateServerOnlineFlag(boolean online) {
        try {
            DatabaseReference statusRef = FirebaseDatabase.getInstance().getReference("serverStatus");
            Map<String, Object> data = new HashMap<>();
            data.put("online", online);
            data.put("updatedAt", System.currentTimeMillis());
            if (!online) {
                data.put("playerCount", 0);
            }
            statusRef.updateChildrenAsync(data);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "[ServerStatus] Failed to update online flag!", e);
        }
    }

    private void updatePlayerList() {
        try {
            DatabaseReference playersRef = FirebaseDatabase.getInstance().getReference("serverStatus/players");

            Map<String, Object> players = new HashMap<>();
            for (Player p : getServer().getOnlinePlayers()) {
                Map<String, Object> info = new HashMap<>();
                info.put("name", p.getName());
                info.put("uuid", p.getUniqueId().toString());
                players.put(p.getUniqueId().toString(), info);
            }

            playersRef.setValueAsync(players);

            // playerCount 동기화
            FirebaseDatabase.getInstance()
                    .getReference("serverStatus/playerCount")
                    .setValueAsync(players.size());
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "[ServerStatus] Failed to update player list!", e);
        }
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("스쿠리 플러그인 시작!");
        getServer().getPluginManager().registerEvents(this, this);



        // Ensure plugin data folder exists
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Load config.yml (after data folder is ensured)
        saveDefaultConfig();
        String dbUrl = getConfig().getString("firebase.databaseUrl");
        if (dbUrl == null || dbUrl.isEmpty()) {
            getLogger().severe("firebase.databaseUrl is missing in config.yml!");
            return;
        }

        // Initialize Firebase
        try {
            File serviceAccount = new File(getDataFolder(), "serviceAccount.json");
            FileInputStream fis = new FileInputStream(serviceAccount);

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(fis))
                    .setDatabaseUrl(dbUrl)
                    .build();

            FirebaseApp.initializeApp(options);
            getLogger().info("Firebase initialized!");

            // Listen for messages from app to mc
            try {
                DatabaseReference incomingRef = FirebaseDatabase.getInstance()
                        .getReference("mc_chat/messages");

                incomingRef.addChildEventListener(new com.google.firebase.database.ChildEventListener() {
                    @Override
                    public void onChildAdded(com.google.firebase.database.DataSnapshot snapshot, String previousChildName) {
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

                            getServer().broadcast(formatted);

                        } catch (Exception e) {
                            getLogger().log(Level.SEVERE, "Failed to process app_to_mc message!", e);
                        }
                    }

                    @Override public void onChildChanged(com.google.firebase.database.DataSnapshot snapshot, String previousChildName) {}
                    @Override public void onChildRemoved(com.google.firebase.database.DataSnapshot snapshot) {}
                    @Override public void onChildMoved(com.google.firebase.database.DataSnapshot snapshot, String previousChildName) {}
                    @Override public void onCancelled(com.google.firebase.database.DatabaseError error) {}
                });

                getLogger().info("Firebase app_to_mc listener registered!");
                sendSystemMessage("스쿠리 마인크래프트 서버가 열렸어요.");
                setupWhitelistSync();

                // 서버 상태 초기화 및 Heartbeat 시작
                updateServerOnlineFlag(true);
                updatePlayerList();
                startServerStatusHeartbeat();

            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to set listener for app_to_mc messages!", e);
            }

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize Firebase!", e);
        }
    }

    private void setupWhitelistSync() {
        FirebaseDatabase db = FirebaseDatabase.getInstance();

        DatabaseReference enabledRef = db.getReference("whitelist/enabled");
        enabledRef.addValueEventListener(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                Boolean enabled = snapshot.getValue(Boolean.class);
                if (enabled == null) enabled = false;

                whitelistEnabled = enabled;
                getLogger().info("[Whitelist] Enabled = " + enabled);
                getServer().setWhitelist(enabled);
            }

            @Override public void onCancelled(com.google.firebase.database.DatabaseError error) {}
        });

        DatabaseReference playersRef = db.getReference("whitelist/players");
        playersRef.addChildEventListener(new com.google.firebase.database.ChildEventListener() {
            @Override
            public void onChildAdded(com.google.firebase.database.DataSnapshot snapshot, String prev) {
                handleWhitelistAdd(snapshot);
            }

            @Override
            public void onChildRemoved(com.google.firebase.database.DataSnapshot snapshot) {
                handleWhitelistRemove(snapshot);
            }

            @Override public void onChildChanged(com.google.firebase.database.DataSnapshot snapshot, String prev) {}
            @Override public void onChildMoved(com.google.firebase.database.DataSnapshot snapshot, String prev) {}
            @Override public void onCancelled(com.google.firebase.database.DatabaseError error) {}
        });

        playersRef.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                whitelistedPlayers.clear();
                for (com.google.firebase.database.DataSnapshot child : snapshot.getChildren()) {
                    try {
                        UUID uuid = UUID.fromString(child.getKey());
                        whitelistedPlayers.add(uuid);

                        OfflinePlayer offline = getServer().getOfflinePlayer(uuid);
                        offline.setWhitelisted(true);
                    } catch (Exception e) {
                        getLogger().warning("[Whitelist] Invalid UUID: " + child.getKey());
                    }
                }
                getLogger().info("[Whitelist] Initial sync — " + whitelistedPlayers.size() + " players");
            }

            @Override public void onCancelled(com.google.firebase.database.DatabaseError error) {}
        });
    }

    private void handleWhitelistAdd(com.google.firebase.database.DataSnapshot snapshot) {
        try {
            String key = snapshot.getKey();
            if (key == null) return;

            UUID uuid = UUID.fromString(key);
            whitelistedPlayers.add(uuid);

            // Must run whitelist modifications on the main thread
            getServer().getScheduler().runTask(this, () -> {
                OfflinePlayer offline = getServer().getOfflinePlayer(uuid);
                offline.setWhitelisted(true);
                getLogger().info("[Whitelist] Added " + uuid);
            });

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "[Whitelist] Add failed!", e);
        }
    }

    private void handleWhitelistRemove(com.google.firebase.database.DataSnapshot snapshot) {
        try {
            String key = snapshot.getKey();
            if (key == null) return;

            UUID uuid = UUID.fromString(key);
            whitelistedPlayers.remove(uuid);

            // Must run whitelist modifications on the main thread
            getServer().getScheduler().runTask(this, () -> {
                OfflinePlayer offline = getServer().getOfflinePlayer(uuid);
                offline.setWhitelisted(false);

                Player online = getServer().getPlayer(uuid);
                if (online != null && online.isOnline()) {
                    online.kick(Component.text("화이트리스트에서 제외되어 접속이 종료됩니다.", NamedTextColor.RED));
                }

                getLogger().info("[Whitelist] Removed " + uuid);
            });

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "[Whitelist] Remove failed!", e);
        }
    }

    @EventHandler
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!whitelistEnabled) return;

        UUID uuid = event.getUniqueId();
        // Skip BE players at pre-login; allow join and handle in onPlayerJoin
        String pname = event.getName();
        if (pname != null && pname.startsWith("[BE]")) {
            return;
        }

        if (!whitelistedPlayers.contains(uuid)) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                    Component.text("당신은 아직 스쿠리 서버 접속 허용이 안 됐어요.\n스쿠리 앱에서 신청 후 접속해주세요!", NamedTextColor.RED)
                            .append(Component.newline())
                            .append(Component.newline())
                            .append(Component.text("앱스토어에서 '스쿠리'를 검색해서 설치하고 성결대 계정으로 로그인해서 신청할 수 있고, 스쿠리를 사용하고 있는 친구에게 등록을 부탁할 수 있어요! (한 사람당 친구 최대 3명 등록 가능)",NamedTextColor.BLUE))
            );
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        String name = p.getName();
        boolean isBedrock = name.startsWith("[BE]");

        if (isBedrock) {
            String cleanName = name.substring(4); // remove "[BE]"
            DatabaseReference ref = FirebaseDatabase.getInstance()
                    .getReference("whitelist/BEPlayers")
                    .child(cleanName);

            ref.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                    boolean exists = snapshot.exists();

                    if (!exists) {
                        frozenPlayers.add(p.getUniqueId());
                        p.setWalkSpeed(0f);
                        p.setFlySpeed(0f);
                        p.setInvulnerable(true);
                        // Additional restrictions for frozen BE players
                        p.setCollidable(false);
                        p.setAllowFlight(true);
                        p.setFlying(true);
                        p.setSilent(true);
                        p.setInvisible(true);
                        p.setCanPickupItems(false);
                        p.showTitle(net.kyori.adventure.title.Title.title(
                                Component.text("스쿠리 서버 접속 불가", NamedTextColor.RED),
                                Component.text("스쿠리 앱에서 계정 등록 후 접속해주세요.", NamedTextColor.YELLOW)
                        ));
                        // Schedule repeating title every 5 seconds for frozen player
                        getServer().getScheduler().runTaskTimer(Skurimcchat.this, task -> {
                            if (!frozenPlayers.contains(p.getUniqueId()) || !p.isOnline()) {
                                task.cancel();
                                return;
                            }
                            p.showTitle(net.kyori.adventure.title.Title.title(
                                    Component.text("스쿠리 서버 접속 불가", NamedTextColor.RED),
                                    Component.text("스쿠리 앱에서 계정 등록 후 접속해주세요.", NamedTextColor.YELLOW)
                            ));
                        }, 100L, 100L);
                        // Continuous warning message task for frozen players
                        getServer().getScheduler().runTaskTimer(Skurimcchat.this, task -> {
                            if (!frozenPlayers.contains(p.getUniqueId()) || !p.isOnline()) {
                                task.cancel();
                                return;
                            }
                            p.sendMessage(Component.text("⚠ 스쿠리 서버 계정 등록이 필요합니다!", NamedTextColor.RED));
                        }, 0L, 100L);
                        // Hide this player from all others
                        for (Player other : getServer().getOnlinePlayers()) {
                            if (!other.getUniqueId().equals(p.getUniqueId())) {
                                other.hidePlayer(Skurimcchat.this, p);
                            }
                        }
                    } else {
                        frozenPlayers.remove(p.getUniqueId());
                        p.setWalkSpeed(0.2f);
                        p.setFlySpeed(0.1f);
                        p.setInvulnerable(false);
                        // Unhide and restore player state
                        for (Player other : getServer().getOnlinePlayers()) {
                            if (!other.getUniqueId().equals(p.getUniqueId())) {
                                other.showPlayer(Skurimcchat.this, p);
                            }
                        }
                        p.setInvisible(false);
                        p.setSilent(false);
                        p.setCollidable(true);
                        p.setCanPickupItems(true);

                        sendSystemMessage(p.getName() + "님이 서버에 접속했어요.");
                        updatePlayerList();
                    }
                }

                @Override
                public void onCancelled(com.google.firebase.database.DatabaseError error) {
                    getLogger().warning("[Whitelist] BE lookup failed: " + error.getMessage());
                }
            });

            return; // BE branch complete
        }

        // JE users follow standard whitelist handling
        sendSystemMessage(p.getName() + "님이 서버에 접속했어요.");
        updatePlayerList();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (frozenPlayers.contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (frozenPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("스쿠리 서버에서 계정 등록이 필요합니다.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (frozenPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("스쿠리 서버에서 계정 등록이 필요합니다.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (frozenPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("스쿠리 서버에서 계정 등록이 필요합니다.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && frozenPlayers.contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }


    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (frozenPlayers.contains(player.getUniqueId())) {
            player.sendMessage(Component.text("스쿠리 서버에서 계정 등록이 필요합니다.", NamedTextColor.RED));
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCommandPreprocess(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (frozenPlayers.contains(p.getUniqueId())) {
            event.setCancelled(true);
            p.sendMessage(Component.text("화이트리스트 승인이 필요합니다.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player p && frozenPlayers.contains(p.getUniqueId())) {
            event.setCancelled(true);
            p.sendMessage(Component.text("화이트리스트 승인이 필요합니다.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && frozenPlayers.contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && frozenPlayers.contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHungerChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && frozenPlayers.contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("스쿠리 플러그인 종료!");
        sendSystemMessage("스쿠리 마인크래프트 서버가 닫혔어요.");

        // 서버 상태를 offline 으로 표시
        updateServerOnlineFlag(false);

        try {
            // 플레이어 목록도 비워주기 (선택적이지만 명시적으로 처리)
            FirebaseDatabase.getInstance()
                    .getReference("serverStatus/players")
                    .setValueAsync(null);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "[ServerStatus] Failed to clear players on shutdown!", e);
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (frozenPlayers.contains(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage(Component.text("화이트리스트 승인이 필요합니다.", NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }

        String playerName = event.getPlayer().getName();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        try {
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("mc_chat/messages");
            Map<String, Object> msg = new HashMap<>();
            msg.put("username", playerName);
            msg.put("message", message);
            msg.put("timestamp", System.currentTimeMillis());
            msg.put("direction", "mc_to_app");
            msg.put("uuid", event.getPlayer().getUniqueId().toString());

            ref.push().setValueAsync(msg);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to send message to Firebase!", e);
        }
    }

    private void sendSystemMessage(String text) {
        try {
            DatabaseReference ref = FirebaseDatabase.getInstance()
                    .getReference("mc_chat/messages");

            Map<String, Object> msg = new HashMap<>();
            msg.put("username", "SYSTEM");
            msg.put("message", text);
            msg.put("timestamp", System.currentTimeMillis());
            msg.put("direction", "system");

            ref.push().setValueAsync(msg);
            getLogger().info("[SystemMessage] " + text);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to send system message to Firebase!", e);
        }
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String playerName = event.getPlayer().getName();
        if (!frozenPlayers.contains(event.getPlayer().getUniqueId())) {
            sendSystemMessage(playerName + "님이 서버에서 나갔어요.");
        }

        // 접속 중인 플레이어 목록 & 카운트 갱신
        updatePlayerList();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        String rawDeathMessage;

        Component deathComp = event.deathMessage(); // 미리 변수로 받기

        if (deathComp != null) {
            rawDeathMessage = PlainTextComponentSerializer.plainText().serialize(deathComp);
        } else {
            rawDeathMessage = event.getEntity().getName() + " died";
        }

        String translated = DeathMessageMapper.translate(rawDeathMessage);
        sendSystemMessage(translated);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        String msg = EntityDeathMessageFormatter.format(event);
        if (msg != null && !msg.isEmpty()) {
            sendSystemMessage(msg);
        }
    }
}
