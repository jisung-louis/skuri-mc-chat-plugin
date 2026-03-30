package com.jisung.skurimcchat.bridge;

public interface SpringBridgeEventListener {

    void onChatFromApp(SpringBridgeModels.AppChatMessage message);

    void onWhitelistSnapshot(SpringBridgeModels.WhitelistSnapshot snapshot);

    void onWhitelistUpsert(SpringBridgeModels.WhitelistEntry entry);

    void onWhitelistRemove(SpringBridgeModels.WhitelistEntry entry);
}
