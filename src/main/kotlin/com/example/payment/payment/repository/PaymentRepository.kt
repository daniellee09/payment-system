package com.example.payment.payment.repository

import com.example.payment.payment.domain.Payment
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentRepository : JpaRepository<Payment, Long> {
    /**
     * 외부 노출용 paymentKey(UUID)로 결제를 조회한다. 결제 취소 시 사용.
     * 취소 로직에서 payment.order.product까지 접근하므로 연관 엔티티를 함께 로딩한다.
     * EntityGraph 없이는 LAZY 연관에 대해 추가 SELECT가 발생한다 (N+1 문제).
     */
    @EntityGraph(attributePaths = ["order", "order.product"])
    fun findByPaymentKey(paymentKey: String): Payment?

    /**
     * 멱등성 키로 기존 결제를 조회한다. 중복 결제 승인 요청을 감지하는 데 사용.
     * 멱등성 검증 시 payment.order.orderId에 접근하므로 order를 함께 로딩한다.
     */
    @EntityGraph(attributePaths = ["order"])
    fun findByIdempotencyKey(idempotencyKey: String): Payment?

    // 주문에 연결된 결제를 조회한다. 주문 관리에서 결제 취소 시 사용.
    fun findByOrderId(orderId: Long): Payment?
}
