package cn.iocoder.yudao.module.campus.framework.esp32;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideModelClientTest {

    @Test
    void shouldSendResponsesRequestAndNormalizeStreamEvents() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startSseServer(requestBody,
                "data: {\"type\":\"response.output_text.delta\",\"delta\":\"你好\"}\n\n"
                        + "data: {\"type\":\"response.output_text.delta\",\"delta\":\"，校园\"}\n\n"
                        + "data: {\"type\":\"response.completed\"}\n\n");

        GuideModelClient client = createClient(server);
        List<JsonNode> events = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        try (GuideModelClient.ModelSession session = client.connect(listener(events, done))) {
            assertTrue(session.send(Map.of(
                    "type", "chat",
                    "request_id", "req-1",
                    "audio", "UklGRg==",
                    "images", List.of("https://example.com/camera.jpg"))));

            assertTrue(done.await(5, TimeUnit.SECONDS));
        } finally {
            client.destroy();
            server.stop(0);
        }

        JsonNode body = JsonUtils.getObjectMapper().readTree(requestBody.get());
        assertEquals("ep-test", body.path("model").asText());
        assertTrue(body.path("stream").asBoolean());
        assertEquals(512, body.path("max_output_tokens").asInt());
        JsonNode content = body.path("input").get(0).path("content");
        assertEquals("input_audio", content.get(0).path("type").asText());
        assertEquals("data:audio/wav;base64,UklGRg==", content.get(0).path("audio_url").asText());
        assertEquals("input_image", content.get(1).path("type").asText());
        assertEquals("https://example.com/camera.jpg", content.get(1).path("image_url").asText());
        assertEquals("input_text", content.get(2).path("type").asText());

        assertEquals("text_delta", events.get(1).path("type").asText());
        assertEquals("你好", events.get(1).path("text").asText());
        JsonNode doneEvent = events.get(events.size() - 1);
        assertEquals("text_done", doneEvent.path("type").asText());
        assertEquals("你好，校园", doneEvent.path("text").asText());
    }

    @Test
    void shouldNotEmitDoneAfterResponsesFailure() throws Exception {
        HttpServer server = startSseServer(new AtomicReference<>(),
                "data: {\"type\":\"response.failed\",\"error\":{\"message\":\"bad request\"}}\n\n");

        GuideModelClient client = createClient(server);
        List<JsonNode> events = new ArrayList<>();
        CountDownLatch finished = new CountDownLatch(1);
        try (GuideModelClient.ModelSession session = client.connect(listener(events, finished))) {
            assertTrue(session.send(Map.of("type", "chat", "request_id", "req-2")));
            assertTrue(finished.await(5, TimeUnit.SECONDS));
        } finally {
            client.destroy();
            server.stop(0);
        }

        JsonNode last = events.get(events.size() - 1);
        assertEquals("error", last.path("type").asText());
        assertEquals("bad request", last.path("message").asText());
        assertTrue(events.stream().noneMatch(event -> "text_done".equals(event.path("type").asText())));
    }

    private static HttpServer startSseServer(AtomicReference<String> requestBody, String sseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/responses", exchange -> respond(exchange, requestBody, sseBody));
        server.start();
        return server;
    }

    private static void respond(HttpExchange exchange, AtomicReference<String> requestBody, String sseBody)
            throws IOException {
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = sseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static GuideModelClient createClient(HttpServer server) throws Exception {
        CampusEsp32AssistantProperties properties = new CampusEsp32AssistantProperties();
        properties.setModelUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/responses");
        properties.setModelName("ep-test");
        properties.setModelToken("test-token");

        GuideModelClient client = new GuideModelClient();
        Field field = GuideModelClient.class.getDeclaredField("properties");
        field.setAccessible(true);
        field.set(client, properties);
        return client;
    }

    private static GuideModelClient.ModelEventListener listener(List<JsonNode> events, CountDownLatch finished) {
        return new GuideModelClient.ModelEventListener() {
            @Override
            public void onConnected(JsonNode event) {
                events.add(event);
            }

            @Override
            public void onEvent(JsonNode event) {
                events.add(event);
                String type = event.path("type").asText();
                if ("text_done".equals(type) || "error".equals(type)) {
                    finished.countDown();
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                ObjectNode event = JsonUtils.getObjectMapper().createObjectNode();
                event.put("type", "failure");
                event.put("message", throwable.getMessage());
                events.add(event);
                finished.countDown();
            }
        };
    }
}
