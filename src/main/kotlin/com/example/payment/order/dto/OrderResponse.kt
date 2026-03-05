package com.example.payment.order.dto

import com.example.payment.order.domain.Order
import com.example.payment.order.domain.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 주문 조회 응답 DTO.
 * productName은 엔티티 내 Lazy 연관에서 가져오므로, 반드시 트랜잭션 범위 안에서 변환해야 한다.
 */
data class OrderResponse(
    val orderId: String,
    val productName: String,
    val quantity: Int,
    val totalAmount: BigDecimal,
    val status: OrderStatus,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(order: Order): OrderResponse = OrderResponse(
            orderId = order.orderId,
            // order.product는 LAZY이므로 이 시점에 트랜잭션이 열려 있어야 한다.
            productName = order.product.name,
            quantity = order.quantity,
            totalAmount = order.totalAmount,
            status = order.status,
            createdAt = requireNotNull(order.createdAt) { "@EnableJpaAuditing이 활성화되어 있어야 합니다." },
        )
    }
}
