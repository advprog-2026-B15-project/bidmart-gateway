package id.ac.ui.cs.advprog.bidmart.gateway.config;

import org.springframework.cloud.gateway.route.RedisRouteDefinitionRepository;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisRouteConfig {

    @Bean
    public ReactiveRedisTemplate<String, RouteDefinition> reactiveRedisRouteDefinitionTemplate(
            ReactiveRedisConnectionFactory factory) {
        Jackson2JsonRedisSerializer<RouteDefinition> serializer = new Jackson2JsonRedisSerializer<>(RouteDefinition.class);
        RedisSerializationContext.RedisSerializationContextBuilder<String, RouteDefinition> builder =
                RedisSerializationContext.newSerializationContext(new StringRedisSerializer());
        RedisSerializationContext<String, RouteDefinition> context = builder.value(serializer).build();
        return new ReactiveRedisTemplate<>(factory, context);
    }

    @Bean
    public RedisRouteDefinitionRepository redisRouteDefinitionRepository(ReactiveRedisTemplate<String, RouteDefinition> reactiveRedisTemplate) {
        return new RedisRouteDefinitionRepository(reactiveRedisTemplate);
    }
}
