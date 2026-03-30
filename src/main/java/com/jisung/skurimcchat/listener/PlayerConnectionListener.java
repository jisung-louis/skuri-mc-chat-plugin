package com.jisung.skurimcchat.listener;

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

    public PlayerConnectionListener(
            WhitelistManager whitelistManager,
            PlayerVerificationService verificationService,
            ChatService chatService,
            ServerStatusService serverStatusService,
            FrozenPlayerManager frozenPlayerManager
    ) {
        this.whitelistManager = whitelistManager;
        this.verificationService = verificationService;
        this.chatService = chatService;
        this.serverStatusService = serverStatusService;
        this.frozenPlayerManager = frozenPlayerManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (verificationService.isBedrockPlayer(event.getName())) {
            return;
        }

        if (!whitelistManager.isBridgeReady()) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                    verificationService.getKickMessage()
            );
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

        if (verificationService.isBedrockPlayer(player.getName())) {
            verificationService.verifyBedrockPlayer(player, () -> {
                chatService.sendPlayerSystemMessage(player, player.getName() + "님이 서버에 접속했어요.", "JOIN");
                serverStatusService.updatePlayerList();
            });
            return;
        }

        chatService.sendPlayerSystemMessage(player, player.getName() + "님이 서버에 접속했어요.", "JOIN");
        serverStatusService.updatePlayerList();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        if (!frozenPlayerManager.isFrozen(player.getUniqueId())) {
            chatService.sendPlayerSystemMessage(player, player.getName() + "님이 서버에서 나갔어요.", "LEAVE");
        }

        frozenPlayerManager.removePlayer(player.getUniqueId());
        serverStatusService.updatePlayerList();
    }
}
