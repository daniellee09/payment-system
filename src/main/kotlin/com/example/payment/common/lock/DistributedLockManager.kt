package com.example.payment.common.lock

import com.example.payment.common.exception.LockAcquisitionFailedException
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Redisson 기반 분산 락 유틸리티.
 *
 * 낙관적 락(@Version)만 사용할 경우, 충돌이 발생하면 예외를 던져 클라이언트가 재시도해야 한다.
 * 분산 락을 추가하면 충돌 자체를 줄여 불필요한 재시도를 방지한다.
 *
 * Redisson의 RLock은 Redis Pub/Sub을 활용해 락 해제를 구독한다.
 * busy-wait 없이 락을 대기하므로 스레드 자원을 낭비하지 않는다.
 */
@Component
class DistributedLockManager(private val redissonClient: RedissonClient) {

    private val log = LoggerFactory.getLogger(DistributedLockManager::class.java)

    companion object {
        private const val LOCK_PREFIX = "lock:"

        // 락 획득을 기다리는 최대 시간. 이 시간이 지나면 LockAcquisitionFailedException을 던진다.
        private const val WAIT_TIME_SECONDS = 5L

        // 락을 보유하는 최대 시간. 이 시간이 지나면 Redis가 자동으로 락을 해제한다.
        // 비즈니스 로직이 비정상 종료돼 unlock이 호출되지 않아도 데드락을 방지한다.
        private const val LEASE_TIME_SECONDS = 10L
    }

    /**
     * 분산 락을 획득한 뒤 block을 실행하고 결과를 반환한다.
     *
     * @param lockKey 락 식별자. 같은 key를 사용하는 요청끼리만 직렬화된다.
     * @param block 락 보호가 필요한 임계 구역 로직
     * @throws LockAcquisitionFailedException WAIT_TIME 내에 락을 획득하지 못한 경우
     */
    fun <T> withLock(lockKey: String, block: () -> T): T {
        val lock = redissonClient.getLock("$LOCK_PREFIX$lockKey")
        val acquired = lock.tryLock(WAIT_TIME_SECONDS, LEASE_TIME_SECONDS, TimeUnit.SECONDS)

        if (!acquired) {
            log.warn("분산 락 획득 실패 — key: {}", lockKey)
            throw LockAcquisitionFailedException()
        }

        try {
            return block()
        } finally {
            // isHeldByCurrentThread: LEASE_TIME 만료 후 다른 스레드가 이미 락을 가진 경우
            // 현재 스레드가 아닌 락을 해제하면 IllegalMonitorStateException이 발생하므로 반드시 검사
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }
}
