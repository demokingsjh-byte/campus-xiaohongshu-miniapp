package cn.iocoder.yudao.module.campus.framework.esp32;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    void shouldMigrateLegacyChatAndTurboConfiguration() {
        CampusEsp32AssistantProperties properties = new CampusEsp32AssistantProperties();
        properties.setModelUrl("https://ark.cn-beijing.volces.com/api/v3/chat/completions");
        properties.setModelName("doubao-seed-2-1-turbo-260628");

        assertEquals("https://ark.cn-beijing.volces.com/api/v3/responses", properties.getModelUrl());
        assertEquals("doubao-seed-2-1-pro-260915", properties.getModelName());
    }

    @Test
    void shouldSendResponsesImageAndAsrTextWithoutAudio() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startSseServer(requestBody,
                "data: {\"type\":\"response.output_text.delta\",\"delta\":\"你好\"}\n\n"
                        + "data: {\"type\":\"response.output_text.delta\",\"delta\":\"，校园\"}\n\n"
                        + "data: {\"type\":\"response.completed\"}\n\n");

        GuideModelClient client = createClient(server);
        List<JsonNode> events = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        try (GuideModelClient.ModelSession session = client.connect(listener(events, done))) {
            Map<String, Object> chat = new LinkedHashMap<>();
            chat.put("type", "chat");
            chat.put("request_id", "req-1");
            chat.put("question", "这里是哪里？");
            chat.put("images", Collections.singletonList("https://example.com/camera.jpg"));
            assertTrue(session.send(chat));

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
        assertEquals(2, content.size());
        assertEquals("input_image", content.get(0).path("type").asText());
        assertEquals("https://example.com/camera.jpg", content.get(0).path("image_url").asText());
        assertEquals("input_text", content.get(1).path("type").asText());
        assertTrue(content.get(1).path("text").asText().contains("这里是哪里？"));
        assertTrue(requestBody.get().indexOf("input_audio") < 0);

        assertEquals("text_delta", events.get(1).path("type").asText());
        assertEquals("你好", events.get(1).path("text").asText());
        JsonNode doneEvent = events.get(events.size() - 1);
        assertEquals("text_done", doneEvent.path("type").asText());
        assertEquals("你好，校园", doneEvent.path("text").asText());
    }

    @Test
    void shouldSwitchToChatProtocolForThirdPartyEndpoint() throws Exception {
        CampusEsp32AssistantProperties properties = new CampusEsp32AssistantProperties();
        properties.setModelUrl("http://127.0.0.1:8000/api/predict/glm/v1/chat/completions");
        properties.setModelProtocol("auto");
        assertEquals("chat", properties.getResolvedModelProtocol());

        properties.setModelUrl("https://ark.cn-beijing.volces.com/api/v3/responses");
        assertEquals("responses", properties.getResolvedModelProtocol());

        properties.setModelProtocol("chat");
        assertEquals("chat", properties.getResolvedModelProtocol());
    }

    @Test
    void shouldSendChatCompletionsPayloadWithDataUrlImage() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startSseServer("/v1/chat/completions", requestBody,
                "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n"
                        + "data: {\"choices\":[{\"delta\":{\"content\":\"，同学\"}}]}\n\n"
                        + "data: [DONE]\n\n");

        GuideModelClient client = createClient(server, "http://127.0.0.1:"
                + server.getAddress().getPort() + "/v1/chat/completions");
        List<JsonNode> events = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        try (GuideModelClient.ModelSession session = client.connect(listener(events, done))) {
            assertEquals("chat", events.get(0).path("protocol").asText());
            Map<String, Object> chat = new LinkedHashMap<>();
            chat.put("type", "chat");
            chat.put("request_id", "req-chat");
            chat.put("question", "这是哪里？");
            chat.put("images", Collections.singletonList("data:image/jpeg;base64,AAAA"));
            assertTrue(session.send(chat));

            assertTrue(done.await(5, TimeUnit.SECONDS));
        } finally {
            client.destroy();
            server.stop(0);
        }

        JsonNode body = JsonUtils.getObjectMapper().readTree(requestBody.get());
        assertEquals("GLM-5.3-flash", body.path("model").asText());
        assertTrue(body.path("stream").asBoolean());
        assertEquals(512, body.path("max_tokens").asInt());
        assertEquals("system", body.path("messages").get(0).path("role").asText());
        JsonNode userContent = body.path("messages").get(1).path("content");
        assertEquals("image_url", userContent.get(0).path("type").asText());
        assertEquals("data:image/jpeg;base64,AAAA",
                userContent.get(0).path("image_url").path("url").asText());
        assertEquals("text", userContent.get(1).path("type").asText());
        assertTrue(userContent.get(1).path("text").asText().contains("这是哪里？"));
        assertTrue(requestBody.get().indexOf("instructions") < 0);
        assertTrue(requestBody.get().indexOf("input_image") < 0);

        JsonNode doneEvent = events.get(events.size() - 1);
        assertEquals("text_done", doneEvent.path("type").asText());
        assertEquals("你好，同学", doneEvent.path("text").asText());
    }

    @Test
    void shouldNotEmitDoneAfterResponsesFailure() throws Exception {
        HttpServer server = startSseServer(new AtomicReference<>(),
                "data: {\"type\":\"response.failed\",\"error\":{\"message\":\"bad request\"}}\n\n");

        GuideModelClient client = createClient(server);
        List<JsonNode> events = new ArrayList<>();
        CountDownLatch finished = new CountDownLatch(1);
        try (GuideModelClient.ModelSession session = client.connect(listener(events, finished))) {
            Map<String, Object> chat = new LinkedHashMap<>();
            chat.put("type", "chat");
            chat.put("request_id", "req-2");
            assertTrue(session.send(chat));
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
        return startSseServer("/responses", requestBody, sseBody);
    }

    private static HttpServer startSseServer(String path, AtomicReference<String> requestBody, String sseBody)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> respond(exchange, requestBody, sseBody));
        server.start();
        return server;
    }

    private static void respond(HttpExchange exchange, AtomicReference<String> requestBody, String sseBody)
            throws IOException {
        requestBody.set(new String(readAllBytes(exchange.getRequestBody()), StandardCharsets.UTF_8));
        byte[] response = sseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static GuideModelClient createClient(HttpServer server) throws Exception {
        return createClient(server, "http://127.0.0.1:" + server.getAddress().getPort() + "/responses", "ep-test");
    }

    private static GuideModelClient createClient(HttpServer server, String modelUrl) throws Exception {
        return createClient(server, modelUrl, "GLM-5.3-flash");
    }

    private static GuideModelClient createClient(HttpServer server, String modelUrl, String modelName)
            throws Exception {
        CampusEsp32AssistantProperties properties = new CampusEsp32AssistantProperties();
        properties.setModelUrl(modelUrl);
        properties.setModelName(modelName);
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
