package com.example.payment.order.domain

/**
 * 주문 상태 전이 규칙: CREATED → PAID → CANCELLED
 * CREATED 상태에서만 PAID로, PAID 상태에서만 CANCELLED로 전이 가능하다.
 * 상태 전이 검증은 Order 엔티티 내부 메서드에서 처리한다.
 */
enum class OrderStatus {
    CREATED,    // 주문 생성됨 (결제 대기)
    PAID,       // 결제 완료
    CANCELLED,  // 취소됨
}
