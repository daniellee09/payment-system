package com.example.payment.common.config

import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

/**
 * Redis 캐시 설정.
 *
 * @EnableCaching을 별도 Config에 분리한 이유:
 * @SpringBootApplication에 붙이면 @WebMvcTest 등 슬라이스 테스트에서
 * CacheManager 빈을 찾지 못해 컨텍스트 로딩이 실패할 수 있다.
 *
 * 캐시별 TTL을 개별 설정해 데이터 특성에 맞는 만료 전략을 적용한다.
 * - productList: 5분 (상품 목록은 변경이 잦으므로 짧게)
 * - productDetail: 10분 (개별 상품은 상대적으로 안정적)
 */
@Configuration
@EnableCaching
class CacheConfig(private val redisConnectionFactory: RedisConnectionFactory) {

    companion object {
        const val PRODUCT_LIST = "productList"
        const val PRODUCT_DETAIL = "productDetail"

        private val PRODUCT_LIST_TTL = Duration.ofMinutes(5)
        private val PRODUCT_DETAIL_TTL = Duration.ofMinutes(10)
    }

    @Bean
    fun cacheManager(): CacheManager {
        // 키는 String으로 직렬화해 Redis CLI에서 사람이 읽을 수 있게 한다.
        // 값은 JDK 직렬화(기본값) 사용 — ProductResponse가 Serializable을 구현해야 한다.
        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            .entryTtl(PRODUCT_LIST_TTL)

        val cacheConfigurations = mapOf(
            PRODUCT_LIST to defaultConfig.entryTtl(PRODUCT_LIST_TTL),
            PRODUCT_DETAIL to defaultConfig.entryTtl(PRODUCT_DETAIL_TTL),
        )

        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build()
    }
}
