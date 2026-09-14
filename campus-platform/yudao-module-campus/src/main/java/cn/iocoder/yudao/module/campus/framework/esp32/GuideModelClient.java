package cn.iocoder.yudao.module.campus.framework.esp32;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 多模态导览模型 WebSocket 客户端。
 */
@Component
public class GuideModelClient {

    @Resource
    private CampusEsp32AssistantProperties properties;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build();

    public ModelSession connect(ModelEventListener eventListener) {
        Request request = new Request.Builder()
                .url(properties.getModelUrl())
                .header("Authorization", "Bearer " + properties.getModelToken())
                .build();
        ModelSession session = new ModelSession(eventListener);
        session.webSocket = httpClient.newWebSocket(request, session.listener);
        return session;
    }

    public static class ModelSession implements AutoCloseable {
        private final ModelEventListener eventListener;
        private final AtomicBoolean terminal = new AtomicBoolean();
        private volatile WebSocket webSocket;
        private volatile boolean connected;

        private final WebSocketListener listener = new WebSocketListener() {
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                JsonNode event;
                try {
                    event = JsonUtils.getObjectMapper().readTree(text);
                } catch (Exception exception) {
                    eventListener.onFailure(new IllegalStateException("模型返回了无效 JSON", exception));
                    return;
                }
                if (!connected) {
                    if (!"connected".equals(event.path("type").asText())) {
                        eventListener.onFailure(new IllegalStateException(
                                event.path("msg").asText("模型拒绝连接")));
                        close();
                        return;
                    }
                    connected = true;
                    eventListener.onConnected(event);
                    return;
                }
                eventListener.onEvent(event);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
                if (terminal.compareAndSet(false, true)) {
                    eventListener.onFailure(throwable);
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                if (terminal.compareAndSet(false, true)) {
                    eventListener.onFailure(new IllegalStateException(
                            "模型连接已关闭：" + code + " " + reason));
                }
            }
        };

        private ModelSession(ModelEventListener eventListener) {
            this.eventListener = eventListener;
        }

        public boolean send(Map<String, Object> event) {
            WebSocket socket = webSocket;
            return connected && socket != null && socket.send(JsonUtils.toJsonString(event));
        }

        @Override
        public void close() {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            WebSocket socket = webSocket;
            if (socket != null) {
                socket.close(1000, "device closed");
            }
        }
    }

    public interface ModelEventListener {
        void onConnected(JsonNode event);

        void onEvent(JsonNode event);

        void onFailure(Throwable throwable);
    }

    @PreDestroy
    public void destroy() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
