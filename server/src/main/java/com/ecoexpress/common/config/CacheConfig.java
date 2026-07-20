package com.ecoexpress.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Cache configuration.
 *
 * <p>Caffeine (local) is configured declaratively in application.yml. This class only
 * customises Redis, which is active under the {@code prod} profile.
 *
 * <p><b>Key prefix is not cosmetic.</b> The Upstash database is shared with another
 * application (Candor), whose keys — {@code candor:ping}, {@code coach:msg:*} — live
 * in the same keyspace. Every key we write is prefixed {@code ecoexpress:} so the two
 * apps cannot collide. This does NOT protect against a FLUSHDB from either side; see
 * the note in CACHING.md.
 */
@Configuration
@Profile("prod")
public class CacheConfig {

    private static final String KEY_PREFIX = "ecoexpress:";

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                // A cached null is a real answer ("this SKU does not exist") and stops
                // a lookup miss from hammering Postgres. But Redis cannot store a raw
                // null, so Spring wraps it — disabling this would silently turn cache
                // misses into DB hits for every not-found.
                .disableCachingNullValues()
                .prefixCacheNameWith(KEY_PREFIX)
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(cacheObjectMapper())));
    }

    /**
     * JSON, not JDK serialization: cached values stay readable in the Upstash console
     * and survive a class being moved between packages.
     *
     * <p>Polymorphic typing is restricted to our own package. Unrestricted default
     * typing on a deserializer is a remote-code-execution vector if anything untrusted
     * ever lands in the cache — and this database is shared with another app.
     */
    private ObjectMapper cacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType("com.ecoexpress.")
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return mapper;
    }
}
