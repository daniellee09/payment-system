package com.example.payment.order.service

import com.example.payment.common.config.CacheConfig
import com.example.payment.common.exception.OrderNotFoundException
import com.example.payment.common.exception.ProductNotFoundException
import com.example.payment.common.lock.DistributedLockManager
import com.example.payment.order.domain.Order
import com.example.payment.order.dto.CreateOrderRequest
import com.example.payment.order.dto.OrderResponse
import com.example.payment.order.repository.OrderRepository
import com.example.payment.payment.repository.PaymentRepository
import com.example.payment.product.repository.ProductRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 주문 비즈니스 로직.
 */
@Service
@Transactional(readOnly = true)
class OrderService(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val paymentRepository: PaymentRepository,
    private val distributedLockManager: DistributedLockManager,
) {
    /**
     * 주문을 생성하고 재고를 차감한다.
     *
     * 동시성 전략: 분산 락(1차) + 낙관적 락 @Version(2차)
     *
     * 1차 — 분산 락: 같은 상품에 대한 동시 주문 요청을 직렬화한다.
     *   → 대부분의 동시 충돌을 미리 차단해 불필요한 DB 롤백을 줄인다.
     *
     * 2차 — 낙관적 락: 락과 트랜잭션 커밋 사이의 극히 짧은 레이스 윈도우에서
     *   동시 수정이 발생한 경우 JPA가 ObjectOptimisticLockingFailureException을 던진다.
     *   GlobalExceptionHandler가 이를 CONCURRENT_UPDATE(409)로 변환한다.
     *
     * 락 키를 productId로 설정해 서로 다른 상품의 주문은 동시에 처리 가능하도록 한다.
     */
    /**
     * 재고 차감 후 상품 캐시를 무효화한다.
     * productList와 productDetail 캐시를 모두 제거해야
     * 다음 조회 시 변경된 재고가 DB에서 다시 로딩된다.
     */
    @Transactional
    @Caching(evict = [
        CacheEvict(cacheNames = [CacheConfig.PRODUCT_LIST], allEntries = true),
        CacheEvict(cacheNames = [CacheConfig.PRODUCT_DETAIL], allEntries = true),
    ])
    fun createOrder(request: CreateOrderRequest): OrderResponse {
        return distributedLockManager.withLock("product:${request.productId}") {
            val product = productRepository.findByProductId(request.productId)
                ?: throw ProductNotFoundException()

            // 재고 부족 시 OutOfStockException(409) 발생 — 트랜잭션 롤백
            product.decreaseStock(request.quantity)

            // 총 금액은 Service에서 계산해 Order에 전달한다.
            // 클라이언트가 보낸 금액을 신뢰하지 않기 위해 서버에서 직접 계산한다.
            val totalAmount = product.price.multiply(request.quantity.toBigDecimal())

            val order = Order(
                product = product,
                quantity = request.quantity,
                totalAmount = totalAmount,
                customerName = request.customerName,
            )

            OrderResponse.from(orderRepository.save(order))
        }
    }

    fun getOrder(orderId: String): OrderResponse {
        val order = orderRepository.findByOrderId(orderId)
            ?: throw OrderNotFoundException()
        return OrderResponse.from(order)
    }

    /**
     * 전체 주문 목록을 최신순으로 조회한다.
     * 대시보드와 POS에서 주문 이력 확인용으로 사용한다.
     * 각 주문에 연결된 결제의 paymentKey를 함께 반환해 프론트에서 직접 취소할 수 있게 한다.
     */
    fun getOrders(): List<OrderResponse> {
        val orders = orderRepository.findAllWithProduct()
        return orders.map { order ->
            val payment = paymentRepository.findByOrderId(requireNotNull(order.id))
            OrderResponse.from(order, paymentKey = payment?.paymentKey)
        }
    }
}
