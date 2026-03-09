package com.example.payment.order.dto

import com.example.payment.order.domain.Order
import com.example.payment.order.domain.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 주문 조회 응답 DTO.
 * productName은 엔티티 내 Lazy 연관에서 가져오므로, 반드시 트랜잭션 범위 안에서 변환해야 한다.
 * orderNumber는 카페 POS 스타일의 3자리 순번(001~999+)으로, 고객에게 표시하는 주문 번호다.
 * orderId(UUID)는 API 내부 식별자로 계속 사용한다.
 */
data class OrderResponse(
    val orderId: String,
    val orderNumber: String,
    val productName: String,
    val quantity: Int,
    val totalAmount: BigDecimal,
    val status: OrderStatus,
    val customerName: String,
    val createdAt: LocalDateTime,
    val paymentKey: String? = null,
) {
    companion object {
        fun from(order: Order, paymentKey: String? = null): OrderResponse = OrderResponse(
            orderId = order.orderId,
            // DB 자동 증가 PK를 3자리 제로패딩으로 포맷한다. 저장 전에는 id가 null이므로 반드시 저장 후 호출해야 한다.
            orderNumber = "%03d".format(requireNotNull(order.id) { "저장된 Order만 변환 가능합니다." }),
            // order.product는 LAZY이므로 이 시점에 트랜잭션이 열려 있어야 한다.
            productName = order.product.name,
            quantity = order.quantity,
            totalAmount = order.totalAmount,
            status = order.status,
            customerName = order.customerName,
            createdAt = requireNotNull(order.createdAt) { "@EnableJpaAuditing이 활성화되어 있어야 합니다." },
            paymentKey = paymentKey,
        )
    }
}
