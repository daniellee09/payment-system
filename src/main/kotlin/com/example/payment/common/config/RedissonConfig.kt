package com.example.payment.common.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Redisson 클라이언트 설정.
 *
 * spring-boot-starter-data-redis(Lettuce)와 Redisson을 함께 사용한다.
 * - Lettuce: Spring Cache(@Cacheable) 에서 사용 — 읽기/쓰기 처리량에 강함
 * - Redisson: 분산 락(RLock) 에서 사용 — Pub/Sub 기반 락 대기로 busy-wait 없음
 *
 * destroyMethod = "shutdown"으로 애플리케이션 종료 시 Redisson 내부 Netty 스레드를 정리한다.
 */
@Configuration
class RedissonConfig(
    @Value("\${spring.data.redis.host}") private val host: String,
    @Value("\${spring.data.redis.port}") private val port: Int,
) {

    @Bean(destroyMethod = "shutdown")
    fun redissonClient(): RedissonClient {
        val config = Config()
        // Redisson은 Redis URI 형식(redis://)을 사용한다
        config.useSingleServer()
            .setAddress("redis://$host:$port")
        return Redisson.create(config)
    }
}
