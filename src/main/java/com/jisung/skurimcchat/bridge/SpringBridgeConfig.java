package com.jisung.skurimcchat.bridge;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public record SpringBridgeConfig(
        String baseUrl,
        String sharedSecret,
        String serverAddress,
        String mapUrl,
        String serverVersion,
        int connectTimeoutMillis,
        int requestTimeoutMillis,
        int sseReconnectDelayMillis,
        long heartbeatIntervalTicks
) {

    public static SpringBridgeConfig from(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        String baseUrl = trimToNull(config.getString("springBridge.baseUrl"));
        String sharedSecret = trimToNull(config.getString("springBridge.sharedSecret"));

        if (baseUrl == null) {
            throw new IllegalArgumentException("springBridge.baseUrl is missing in config.yml");
        }
        if (sharedSecret == null) {
            throw new IllegalArgumentException("springBridge.sharedSecret is missing in config.yml");
        }

        return new SpringBridgeConfig(
                normalizeBaseUrl(baseUrl),
                sharedSecret,
                trimToNull(config.getString("springBridge.serverAddress")),
                trimToNull(config.getString("springBridge.mapUrl")),
                trimToNull(config.getString("springBridge.serverVersion")),
                Math.max(1_000, config.getInt("springBridge.connectTimeoutMillis", 5_000)),
                Math.max(1_000, config.getInt("springBridge.requestTimeoutMillis", 10_000)),
                Math.max(1_000, config.getInt("springBridge.sseReconnectDelayMillis", 3_000)),
                Math.max(20L, config.getLong("springBridge.heartbeatIntervalTicks", 200L))
        );
    }

    private static String normalizeBaseUrl(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
