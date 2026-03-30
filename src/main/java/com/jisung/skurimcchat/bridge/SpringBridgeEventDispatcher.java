package com.jisung.skurimcchat.bridge;

import com.jisung.skurimcchat.service.ChatService;
import com.jisung.skurimcchat.whitelist.WhitelistSyncService;

public class SpringBridgeEventDispatcher implements SpringBridgeEventListener {

    private final ChatService chatService;
    private final WhitelistSyncService whitelistSyncService;

    public SpringBridgeEventDispatcher(
            ChatService chatService,
            WhitelistSyncService whitelistSyncService
    ) {
        this.chatService = chatService;
        this.whitelistSyncService = whitelistSyncService;
    }

    @Override
    public void onChatFromApp(SpringBridgeModels.AppChatMessage message) {
        chatService.broadcastMessageFromApp(message);
    }

    @Override
    public void onWhitelistSnapshot(SpringBridgeModels.WhitelistSnapshot snapshot) {
        whitelistSyncService.applySnapshot(snapshot);
    }

    @Override
    public void onWhitelistUpsert(SpringBridgeModels.WhitelistEntry entry) {
        whitelistSyncService.applyUpsert(entry);
    }

    @Override
    public void onWhitelistRemove(SpringBridgeModels.WhitelistEntry entry) {
        whitelistSyncService.applyRemove(entry);
    }
}
