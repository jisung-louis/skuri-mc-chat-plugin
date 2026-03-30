package com.jisung.skurimcchat.bridge;

import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

public class MinecraftIdentityResolver {

    private static final String BEDROCK_PREFIX = "[BE]";

    public boolean isBedrockPlayer(String playerName) {
        return playerName != null && playerName.startsWith(BEDROCK_PREFIX);
    }

    public String resolveEdition(Player player) {
        return resolveEdition(player.getName());
    }

    public String resolveEdition(String playerName) {
        return isBedrockPlayer(playerName) ? "BEDROCK" : "JAVA";
    }

    public String resolveIdentity(Player player) {
        if (isBedrockPlayer(player.getName())) {
            return toStoredBedrockName(player.getName());
        }
        return normalizeJavaUuid(player.getUniqueId());
    }

    public String toStoredBedrockName(String playerName) {
        String cleanName = extractBedrockName(playerName).trim().replaceAll("\\s+", "_");
        return cleanName.length() > 12 ? cleanName.substring(0, 12) : cleanName;
    }

    public String extractBedrockName(String playerName) {
        if (!isBedrockPlayer(playerName)) {
            return playerName;
        }
        return playerName.substring(BEDROCK_PREFIX.length()).trim();
    }

    public String normalizeJavaUuid(UUID uuid) {
        return uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
    }

    public UUID toJavaUuid(String normalizedKey) {
        String raw = normalizedKey == null ? "" : normalizedKey.replace("-", "").toLowerCase(Locale.ROOT);
        if (raw.length() != 32) {
            throw new IllegalArgumentException("Invalid Java UUID: " + normalizedKey);
        }
        return UUID.fromString(
                raw.substring(0, 8) + "-"
                        + raw.substring(8, 12) + "-"
                        + raw.substring(12, 16) + "-"
                        + raw.substring(16, 20) + "-"
                        + raw.substring(20)
        );
    }
}
