package com.jisung.skurimcchat.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class SpringBridgeClient {

    private final JavaPlugin plugin;
    private final SpringBridgeConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CountDownLatch initialSnapshotLatch = new CountDownLatch(1);

    private volatile SpringBridgeEventListener eventListener;
    private volatile Thread sseThread;
    private volatile InputStream currentSseStream;
    private volatile String lastEventId;

    public SpringBridgeClient(
            JavaPlugin plugin,
            SpringBridgeConfig config,
            SpringBridgeEventListener eventListener
    ) {
        this.plugin = plugin;
        this.config = config;
        this.eventListener = eventListener;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.connectTimeoutMillis()))
                .build();
    }

    public void setEventListener(SpringBridgeEventListener eventListener) {
        this.eventListener = eventListener;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        Thread thread = new Thread(this::runSseLoop, "skuri-spring-bridge-sse");
        thread.setDaemon(true);
        thread.start();
        this.sseThread = thread;
    }

    public void stop() {
        running.set(false);

        InputStream stream = currentSseStream;
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
                // no-op
            }
        }

        Thread thread = sseThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    public boolean awaitInitialWhitelistSnapshot(long timeoutMillis) {
        try {
            return initialSnapshotLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void sendChatMessage(String senderName, String minecraftUuid, String edition, String text) {
        sendInternalChatMessage(new SpringBridgeModels.InternalChatMessageRequest(
                UUID.randomUUID().toString(),
                "CHAT",
                null,
                senderName,
                minecraftUuid,
                edition,
                text,
                Instant.now().toString()
        ));
    }

    public void sendSystemMessage(
            String systemType,
            String senderName,
            String minecraftUuid,
            String edition,
            String text
    ) {
        sendInternalChatMessage(new SpringBridgeModels.InternalChatMessageRequest(
                UUID.randomUUID().toString(),
                "SYSTEM",
                systemType,
                senderName,
                minecraftUuid,
                edition,
                text,
                Instant.now().toString()
        ));
    }

    public void updateServerState(SpringBridgeModels.ServerStateUpsertRequest request) {
        sendJsonAsync("PUT", "/internal/minecraft/server-state", request);
    }

    public void updateOnlinePlayers(SpringBridgeModels.OnlinePlayersUpsertRequest request) {
        sendJsonAsync("PUT", "/internal/minecraft/online-players", request);
    }

    private void sendInternalChatMessage(SpringBridgeModels.InternalChatMessageRequest request) {
        sendJsonAsync("POST", "/internal/minecraft/chat/messages", request);
    }

    private void sendJsonAsync(String method, String path, Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + path))
                    .timeout(Duration.ofMillis(config.requestTimeoutMillis()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-Skuri-Minecraft-Secret", config.sharedSecret())
                    .method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .whenComplete((response, throwable) -> {
                        if (throwable != null) {
                            plugin.getLogger().log(Level.WARNING, "Spring bridge request failed: " + path, throwable);
                            return;
                        }
                        if (response.statusCode() / 100 != 2) {
                            plugin.getLogger().warning("Spring bridge request failed: " + path
                                    + " status=" + response.statusCode()
                                    + " body=" + response.body());
                        }
                    });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Spring bridge payload serialization failed: " + path, e);
        }
    }

    private void runSseLoop() {
        while (running.get()) {
            try {
                connectAndConsumeSse();
            } catch (Exception e) {
                if (running.get()) {
                    plugin.getLogger().log(Level.WARNING, "Spring bridge SSE connection failed", e);
                }
            }

            if (!running.get()) {
                break;
            }

            try {
                Thread.sleep(config.sseReconnectDelayMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void connectAndConsumeSse() throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/internal/minecraft/stream"))
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .header("X-Skuri-Minecraft-Secret", config.sharedSecret())
                .GET();

        if (lastEventId != null && !lastEventId.isBlank()) {
            builder.header("Last-Event-ID", lastEventId);
        }

        HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("Spring bridge SSE status=" + response.statusCode() + " body=" + body);
        }

        plugin.getLogger().info("Connected to Spring bridge SSE.");
        currentSseStream = response.body();

        try (InputStream inputStream = response.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            String eventName = null;
            String eventId = null;
            StringBuilder data = new StringBuilder();

            while (running.get() && (line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    dispatchEvent(eventName, eventId, data.toString());
                    eventName = null;
                    eventId = null;
                    data.setLength(0);
                    continue;
                }

                if (line.startsWith(":")) {
                    continue;
                }
                if (line.startsWith("event:")) {
                    eventName = line.substring("event:".length()).trim();
                    continue;
                }
                if (line.startsWith("id:")) {
                    eventId = line.substring("id:".length()).trim();
                    continue;
                }
                if (line.startsWith("data:")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(line.substring("data:".length()).trim());
                }
            }
        } finally {
            currentSseStream = null;
        }
    }

    private void dispatchEvent(String eventName, String eventId, String data) {
        if (eventName == null || eventName.isBlank()) {
            return;
        }

        SpringBridgeEventListener listener = eventListener;
        if (listener == null) {
            plugin.getLogger().warning("Spring bridge event listener is not configured yet.");
            return;
        }

        if (eventId != null && !eventId.isBlank()) {
            lastEventId = eventId;
        }

        try {
            switch (eventName) {
                case "CHAT_FROM_APP" -> {
                    SpringBridgeModels.AppChatMessage message =
                            objectMapper.readValue(data, SpringBridgeModels.AppChatMessage.class);
                    runOnMainThread(() -> listener.onChatFromApp(message));
                }
                case "WHITELIST_SNAPSHOT" -> {
                    SpringBridgeModels.WhitelistSnapshot snapshot =
                            objectMapper.readValue(data, SpringBridgeModels.WhitelistSnapshot.class);
                    initialSnapshotLatch.countDown();
                    runOnMainThread(() -> listener.onWhitelistSnapshot(snapshot));
                }
                case "WHITELIST_UPSERT" -> {
                    SpringBridgeModels.WhitelistEntry entry =
                            objectMapper.readValue(data, SpringBridgeModels.WhitelistEntry.class);
                    runOnMainThread(() -> listener.onWhitelistUpsert(entry));
                }
                case "WHITELIST_REMOVE" -> {
                    SpringBridgeModels.WhitelistEntry entry =
                            objectMapper.readValue(data, SpringBridgeModels.WhitelistEntry.class);
                    runOnMainThread(() -> listener.onWhitelistRemove(entry));
                }
                case "HEARTBEAT" -> {
                    // no-op
                }
                default -> plugin.getLogger().warning("Unknown Spring bridge SSE event: " + eventName);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to handle Spring bridge SSE event: " + eventName, e);
        }
    }

    private void runOnMainThread(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
}
