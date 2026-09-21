package cn.iocoder.yudao.module.campus.framework.esp32;

import cn.iocoder.yudao.framework.security.config.AuthorizeRequestsCustomizer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * ESP32-S3 独立 WebSocket 入口。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "campus.esp32-assistant", name = "enabled", havingValue = "true")
public class CampusEsp32WebSocketConfiguration implements WebSocketConfigurer {

    private final CampusEsp32AssistantProperties properties;
    private final Esp32AssistantWebSocketHandler handler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, properties.getPath())
                .addInterceptors(new Esp32DeviceHandshakeInterceptor(properties))
                .setAllowedOriginPatterns("*");
        if (properties.getLegacyPath() != null
                && !properties.getLegacyPath().trim().isEmpty()
                && !properties.getLegacyPath().equals(properties.getPath())) {
            registry.addHandler(handler, properties.getLegacyPath())
                    .addInterceptors(new Esp32DeviceHandshakeInterceptor(properties))
                    .setAllowedOriginPatterns("*");
        }
    }

    @Bean
    public ServletServerContainerFactoryBean esp32WebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(64 * 1024);
        container.setMaxBinaryMessageBufferSize(properties.getMaxImageBytes() + 1);
        container.setMaxSessionIdleTimeout(15 * 60 * 1000L);
        return container;
    }

    @Bean
    public AuthorizeRequestsCustomizer esp32AuthorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {
            @Override
                public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>
                                           .AuthorizationManagerRequestMatcherRegistry registry) {
                registry.requestMatchers(properties.getPath()).permitAll();
                if (properties.getLegacyPath() != null
                        && !properties.getLegacyPath().trim().isEmpty()) {
                    registry.requestMatchers(properties.getLegacyPath()).permitAll();
                }
            }
        };
    }
}
