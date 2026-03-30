package com.jisung.skurimcchat.whitelist;

import com.jisung.skurimcchat.bridge.MinecraftIdentityResolver;
import com.jisung.skurimcchat.restriction.FrozenPlayerManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerVerificationService {
    private final JavaPlugin plugin;
    private final Server server;
    private final WhitelistManager whitelistManager;
    private final FrozenPlayerManager frozenPlayerManager;
    private final MinecraftIdentityResolver identityResolver;

    public PlayerVerificationService(
            JavaPlugin plugin,
            Server server,
            WhitelistManager whitelistManager,
            FrozenPlayerManager frozenPlayerManager,
            MinecraftIdentityResolver identityResolver
    ) {
        this.plugin = plugin;
        this.server = server;
        this.whitelistManager = whitelistManager;
        this.frozenPlayerManager = frozenPlayerManager;
        this.identityResolver = identityResolver;
    }

    public boolean isBedrockPlayer(String playerName) {
        return identityResolver.isBedrockPlayer(playerName);
    }

    public void verifyBedrockPlayer(Player player, Runnable onVerified) {
        if (!whitelistManager.isBridgeReady()) {
            frozenPlayerManager.freezePlayer(player);
            player.sendMessage(Component.text("인증 목록을 불러오는 중입니다. 잠시만 기다려주세요.", NamedTextColor.YELLOW));
            return;
        }

        if (!whitelistManager.hasBedrockAccount(player.getName())) {
            frozenPlayerManager.freezePlayer(player);
            return;
        }

        frozenPlayerManager.unfreezePlayer(player);
        if (onVerified != null) {
            onVerified.run();
        }
    }

    public void recheckOnlineBedrockPlayers() {
        server.getScheduler().runTask(plugin, () -> {
            for (Player player : server.getOnlinePlayers()) {
                if (!isBedrockPlayer(player.getName())) {
                    continue;
                }

                if (whitelistManager.hasBedrockAccount(player.getName())) {
                    frozenPlayerManager.unfreezePlayer(player);
                } else {
                    frozenPlayerManager.freezePlayer(player);
                }
            }
        });
    }

    public Component getKickMessage() {
        return Component.text("당신은 아직 스쿠리 서버 접속 허용이 안 됐어요.\n스쿠리 앱에서 신청 후 접속해주세요!", NamedTextColor.RED)
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("앱스토어에서 '스쿠리'를 검색해서 설치하고 성결대 계정으로 로그인해서 신청할 수 있고, 스쿠리를 사용하고 있는 친구에게 등록을 부탁할 수 있어요! (한 사람당 친구 최대 3명 등록 가능)", NamedTextColor.BLUE));
    }
}
