package br.com.vanroute.backend.config;

import br.com.vanroute.backend.websocket.TripWebSocketHandler;
import br.com.vanroute.backend.websocket.NotificationWebSocketHandler;
import java.util.Collections;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.PatternTopic;
import br.com.vanroute.backend.websocket.ChatWebSocketHandler;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(
            RedisConnectionFactory connectionFactory
    ) {
        RedisTemplate<String, String> template = new RedisTemplate<>();

        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());

        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());

        return template;
    }
    
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            ChatWebSocketHandler chatWebSocketHandler,
            TripWebSocketHandler tripWebSocketHandler,
            NotificationWebSocketHandler notificationWebSocketHandler) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(chatWebSocketHandler, Collections.singletonList(new PatternTopic("chat-*")));
        container.addMessageListener(tripWebSocketHandler, Collections.singletonList(new PatternTopic("trip-*")));
        container.addMessageListener(notificationWebSocketHandler, Collections.singletonList(new PatternTopic("notification-*")));
        return container;
    }
}