package com.jisung.skurimcchat.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringBridgeClientTest {

    @Mock
    private JavaPlugin plugin;

    @Mock
    private HttpClient httpClient;

    @Mock
    private SpringBridgeEventListener eventListener;

    @Mock
    private HttpResponse<InputStream> response;

    private SpringBridgeClient springBridgeClient;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger("SpringBridgeClientTest");
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);

        lenient().when(plugin.getLogger()).thenReturn(logger);
        lenient().when(plugin.getServer()).thenReturn(server);
        lenient().when(server.getScheduler()).thenReturn(scheduler);
        lenient().doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(scheduler).runTask(any(Plugin.class), any(Runnable.class));

        springBridgeClient = new SpringBridgeClient(
                plugin,
                new SpringBridgeConfig(
                        "http://127.0.0.1:8080",
                        "test-secret",
                        "mc.skuri.app",
                        "https://map.skuri.app",
                        null,
                        5_000,
                        10_000,
                        3_000,
                        200L
                ),
                eventListener,
                httpClient,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void connectAndConsumeSse_non2xx응답이면_스트림을닫고예외를던진다() throws Exception {
        TrackingInputStream inputStream = new TrackingInputStream("{\"message\":\"forbidden\"}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(403);
        when(response.body()).thenReturn(inputStream);

        IOException exception = assertThrows(IOException.class, () -> springBridgeClient.connectAndConsumeSse());
        assertTrue(exception.getMessage().contains("status=403"));

        assertTrue(inputStream.closed);
    }

    @Test
    void dispatchEvent_처리실패하면_lastEventId를올리지않는다() {
        doThrow(new IllegalStateException("apply failed"))
                .when(eventListener)
                .onWhitelistUpsert(any(SpringBridgeModels.WhitelistEntry.class));

        assertThrows(IOException.class, () -> springBridgeClient.dispatchEvent(
                "WHITELIST_UPSERT",
                "event-1",
                "{\"accountId\":\"account-1\",\"normalizedKey\":\"key-1\",\"edition\":\"JAVA\",\"gameName\":\"skuriPlayer\",\"avatarUuid\":\"8667ba71b85a4004af54457a9734eed7\",\"storedName\":null}"
        ));

        assertNull(getLastEventId());
    }

    @Test
    void dispatchEvent_메인스레드처리성공후_lastEventId를올린다() throws Exception {
        springBridgeClient.dispatchEvent(
                "WHITELIST_UPSERT",
                "event-1",
                "{\"accountId\":\"account-1\",\"normalizedKey\":\"key-1\",\"edition\":\"JAVA\",\"gameName\":\"skuriPlayer\",\"avatarUuid\":\"8667ba71b85a4004af54457a9734eed7\",\"storedName\":null}"
        );

        assertEquals("event-1", getLastEventId());
    }

    private String getLastEventId() {
        try {
            Field field = SpringBridgeClient.class.getDeclaredField("lastEventId");
            field.setAccessible(true);
            return (String) field.get(springBridgeClient);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {

        private boolean closed;

        private TrackingInputStream(String body) {
            super(body.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
