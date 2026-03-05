package com.example.payment.payment.domain

/**
 * 결제 상태 전이 규칙: READY -> DONE -> CANCELLED
 * READY 상태에서만 DONE으로, DONE 상태에서만 CANCELLED로 전이 가능하다.
 * 상태 전이 검증은 Payment 엔티티 내부 메서드에서 처리한다.
 */
enum class PaymentStatus {
    READY,      // 결제 대기 (승인 전)
    DONE,       // 결제 완료
    CANCELLED,  // 결제 취소
}
