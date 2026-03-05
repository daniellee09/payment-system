package com.example.payment.order.service

import com.example.payment.common.exception.OrderNotFoundException
import com.example.payment.common.exception.ProductNotFoundException
import com.example.payment.order.domain.Order
import com.example.payment.order.dto.CreateOrderRequest
import com.example.payment.order.dto.OrderResponse
import com.example.payment.order.repository.OrderRepository
import com.example.payment.product.repository.ProductRepository
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
) {
    /**
     * 주문을 생성하고 재고를 차감한다.
     *
     * 재고 차감과 주문 저장을 하나의 트랜잭션으로 묶는다.
     * 중간에 실패하면 둘 다 롤백되어 재고만 빠지거나 주문만 생기는 상황을 방지한다.
     *
     * 동시 요청으로 @Version 충돌이 발생하면 JPA가 ObjectOptimisticLockingFailureException을 던지고,
     * GlobalExceptionHandler가 이를 잡아 CONCURRENT_UPDATE(409)로 변환한다.
     */
    @Transactional
    fun createOrder(request: CreateOrderRequest): OrderResponse {
        val product = productRepository.findById(request.productId)
            .orElseThrow { ProductNotFoundException() }

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

        return OrderResponse.from(orderRepository.save(order))
    }

    fun getOrder(orderId: String): OrderResponse {
        val order = orderRepository.findByOrderId(orderId)
            ?: throw OrderNotFoundException()
        return OrderResponse.from(order)
    }
}
