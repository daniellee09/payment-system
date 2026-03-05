package com.example.payment.payment.repository

import com.example.payment.payment.domain.Payment
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentRepository : JpaRepository<Payment, Long> {
    // 외부 노출용 paymentKey(UUID)로 결제를 조회한다. 결제 취소 시 사용.
    fun findByPaymentKey(paymentKey: String): Payment?

    // 멱등성 키로 기존 결제를 조회한다. 중복 결제 승인 요청을 감지하는 데 사용.
    fun findByIdempotencyKey(idempotencyKey: String): Payment?
}
