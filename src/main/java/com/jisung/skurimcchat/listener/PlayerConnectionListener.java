package com.jisung.skurimcchat.listener;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.jisung.skurimcchat.restriction.FrozenPlayerManager;
import com.jisung.skurimcchat.service.ChatService;
import com.jisung.skurimcchat.service.ServerStatusService;
import com.jisung.skurimcchat.whitelist.PlayerVerificationService;
import com.jisung.skurimcchat.whitelist.WhitelistManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {
    private final WhitelistManager whitelistManager;
    private final PlayerVerificationService verificationService;
    private final ChatService chatService;
    private final ServerStatusService serverStatusService;
    private final FrozenPlayerManager frozenPlayerManager;
    private final FirebaseDatabase database;

    public PlayerConnectionListener(WhitelistManager whitelistManager,
                                    PlayerVerificationService verificationService,
                                    ChatService chatService,
                                    ServerStatusService serverStatusService,
                                    FrozenPlayerManager frozenPlayerManager,
                                    FirebaseDatabase database) {
        this.whitelistManager = whitelistManager;
        this.verificationService = verificationService;
        this.chatService = chatService;
        this.serverStatusService = serverStatusService;
        this.frozenPlayerManager = frozenPlayerManager;
        this.database = database;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!whitelistManager.isWhitelistEnabled()) return;

        String pname = event.getName();
        if (verificationService.isBedrockPlayer(pname)) {
            // Skip BE players at pre-login; allow join and handle in onPlayerJoin
            return;
        }

        if (!whitelistManager.isWhitelisted(event.getUniqueId())) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                    verificationService.getKickMessage()
            );
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        String name = player.getName();

        // 화이트리스트에 등록된 JE 플레이어라면 마지막 접속 시각 기록
        if (whitelistManager.isWhitelisted(player.getUniqueId())) {
            long now = System.currentTimeMillis();
            database.getReference("whitelist/players")
                    .child(player.getUniqueId().toString())
                    .child("lastSeenAt")
                    .setValueAsync(now);
        }

        if (verificationService.isBedrockPlayer(name)) {
            verificationService.verifyBedrockPlayer(player, () -> {
                chatService.sendSystemMessage(player.getName() + "님이 서버에 접속했어요.");
                serverStatusService.updatePlayerList();
            });
            return;
        }

        // JE users follow standard whitelist handling
        chatService.sendSystemMessage(player.getName() + "님이 서버에 접속했어요.");
        serverStatusService.updatePlayerList();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        String playerName = event.getPlayer().getName();
        if (!frozenPlayerManager.isFrozen(event.getPlayer().getUniqueId())) {
            chatService.sendSystemMessage(playerName + "님이 서버에서 나갔어요.");
        }

        // 화이트리스트에 등록된 JE 플레이어의 마지막 접속 시각 갱신
        if (whitelistManager.isWhitelisted(event.getPlayer().getUniqueId())) {
            long now = System.currentTimeMillis();
            database.getReference("whitelist/players")
                    .child(event.getPlayer().getUniqueId().toString())
                    .child("lastSeenAt")
                    .setValueAsync(now);
        }

        // BE 유저(이름이 [BE]로 시작)도 퇴장 시 lastSeenAt 갱신 (화이트리스트 노드가 존재할 때만)
        if (verificationService.isBedrockPlayer(playerName)) {
            String cleanName = playerName.substring(4); // remove "[BE]"
            DatabaseReference ref = database.getReference("whitelist/BEPlayers").child(cleanName);
            
            // 노드 존재 여부 확인 후, 존재할 때만 lastSeenAt 기록
            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        // 화이트리스트에 등록된 BE 유저만 lastSeenAt 갱신
                        long now = System.currentTimeMillis();
                        database.getReference("whitelist/BEPlayers")
                                .child(cleanName)
                                .child("lastSeenAt")
                                .setValueAsync(now);
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    // 에러 발생 시 무시 (로그만 남기지 않음)
                }
            });
        }

        serverStatusService.updatePlayerList();
    }
}

