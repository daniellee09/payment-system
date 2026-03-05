package com.example.payment.payment.dto

import com.example.payment.payment.domain.Payment
import com.example.payment.payment.domain.PaymentMethod
import com.example.payment.payment.domain.PaymentStatus
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 결제 조회/승인/취소 응답 DTO.
 * 결제 엔티티의 핵심 정보만 외부에 노출한다.
 */
data class PaymentResponse(
    val paymentKey: String,
    val orderId: String,
    val amount: BigDecimal,
    val status: PaymentStatus,
    val paymentMethod: PaymentMethod,
    val createdAt: LocalDateTime,
) {
    companion object {
        /**
         * Payment 엔티티를 응답 DTO로 변환한다.
         * order.orderId 접근이 LAZY 프록시를 통하므로 트랜잭션 내에서 호출해야 한다.
         */
        fun from(payment: Payment): PaymentResponse = PaymentResponse(
            paymentKey = payment.paymentKey,
            orderId = payment.order.orderId,
            amount = payment.amount,
            status = payment.status,
            paymentMethod = payment.paymentMethod,
            createdAt = requireNotNull(payment.createdAt) { "@EnableJpaAuditing이 활성화되어 있어야 합니다." },
        )
    }
}
