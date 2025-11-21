package com.jisung.skurimcchat.whitelist;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.jisung.skurimcchat.restriction.FrozenPlayerManager;

public class PlayerVerificationService {
    private final JavaPlugin plugin;
    private final FirebaseDatabase database;
    private final FrozenPlayerManager frozenPlayerManager;

    public PlayerVerificationService(JavaPlugin plugin, FirebaseDatabase database,
                                     WhitelistManager whitelistManager,
                                     FrozenPlayerManager frozenPlayerManager) {
        this.plugin = plugin;
        this.database = database;
        this.frozenPlayerManager = frozenPlayerManager;
    }

    public boolean isBedrockPlayer(String playerName) {
        return playerName != null && playerName.startsWith("[BE]");
    }

    public void verifyBedrockPlayer(Player player, Runnable onVerified) {
        String name = player.getName();
        String cleanName = name.substring(4); // remove "[BE]"
        DatabaseReference ref = database.getReference("whitelist/BEPlayers").child(cleanName);

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                boolean exists = snapshot.exists();

                if (!exists) {
                    frozenPlayerManager.freezePlayer(player);
                } else {
                    frozenPlayerManager.unfreezePlayer(player);
                    if (onVerified != null) {
                        onVerified.run();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                plugin.getLogger().warning("[Whitelist] BE lookup failed: " + error.getMessage());
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

