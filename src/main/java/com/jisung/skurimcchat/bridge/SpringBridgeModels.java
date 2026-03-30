package com.jisung.skurimcchat.bridge;

import java.util.List;

public final class SpringBridgeModels {

    private SpringBridgeModels() {
    }

    public record InternalChatMessageRequest(
            String eventId,
            String eventType,
            String systemType,
            String senderName,
            String minecraftUuid,
            String edition,
            String text,
            String occurredAt
    ) {
    }

    public record ServerStateUpsertRequest(
            Boolean online,
            Integer currentPlayers,
            Integer maxPlayers,
            String version,
            String serverAddress,
            String mapUrl,
            String heartbeatAt
    ) {
    }

    public record OnlinePlayersUpsertRequest(
            String capturedAt,
            List<OnlinePlayer> players
    ) {
    }

    public record OnlinePlayer(
            String gameName,
            String edition,
            String minecraftUuid
    ) {
    }

    public record AppChatMessage(
            String messageId,
            String chatRoomId,
            String senderName,
            String type,
            String text
    ) {
    }

    public record WhitelistSnapshot(
            List<WhitelistEntry> players
    ) {
    }

    public record WhitelistEntry(
            String accountId,
            String normalizedKey,
            String edition,
            String gameName,
            String avatarUuid,
            String storedName
    ) {
    }
}
