package com.example.payment

import com.example.payment.order.dto.CreateOrderRequest
import com.example.payment.order.repository.OrderRepository
import com.example.payment.order.service.OrderService
import com.example.payment.product.domain.Product
import com.example.payment.product.repository.ProductRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 재고 차감 동시성 통합 테스트.
 *
 * 분산 락(Redisson) + 낙관적 락(@Version)이 함께 동작해
 * 동시 주문 시 정확한 재고 차감이 보장되는지 검증한다.
 *
 * 실행 전 요구사항:
 *   1. Docker Compose 실행: docker-compose up -d
 *   2. MySQL 스키마가 이미 적용되어 있어야 한다
 */
@SpringBootTest
@Disabled("로컬 Docker 환경 필요: docker-compose up -d 후 @Disabled 제거하고 실행")
class ConcurrencyTest {

    @Autowired
    lateinit var orderService: OrderService

    @Autowired
    lateinit var productRepository: ProductRepository

    @Autowired
    lateinit var orderRepository: OrderRepository

    private lateinit var testProduct: Product

    @BeforeEach
    fun setUp() {
        // 재고 10개짜리 테스트 상품을 DB에 저장한다.
        // productRepository.save()는 내부적으로 트랜잭션을 커밋하므로
        // 다른 스레드에서도 즉시 조회 가능하다.
        testProduct = productRepository.save(
            Product(name = "동시성테스트상품", price = BigDecimal("10000"), stock = 10)
        )
    }

    @AfterEach
    fun tearDown() {
        // FK 제약 때문에 주문(orders)을 먼저 삭제한 뒤 상품(products)을 삭제한다.
        orderRepository.deleteAll()
        productRepository.delete(testProduct)
    }

    @Test
    fun `재고 10개 상품에 100개 동시 주문이 들어오면 정확히 10개만 성공한다`() {
        val threadCount = 100
        // 32개 스레드 풀: 실제 서버 환경의 제한된 스레드 수를 시뮬레이션
        val executor = Executors.newFixedThreadPool(32)
        // 모든 스레드가 결과를 보고한 뒤에야 검증을 진행하도록 보장
        val latch = CountDownLatch(threadCount)

        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        repeat(threadCount) {
            executor.submit {
                try {
                    orderService.createOrder(
                        CreateOrderRequest(
                            productId = testProduct.productId,
                            quantity = 1,
                            customerName = "테스트고객",
                        )
                    )
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    // OutOfStockException(재고 부족) 또는 LockAcquisitionFailedException(락 타임아웃)
                    // 두 경우 모두 "실패"로 카운트한다.
                    failCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        // 최대 60초 대기 — 분산 락 + DB 처리 시간을 여유 있게 확보
        latch.await(60, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(successCount.get()).isEqualTo(10)
        assertThat(failCount.get()).isEqualTo(90)
    }
}
