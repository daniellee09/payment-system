package com.example.payment.order.repository

import com.example.payment.order.domain.Order
import org.springframework.data.jpa.repository.JpaRepository

interface OrderRepository : JpaRepository<Order, Long> {
    // 외부 노출용 orderId(UUID)로 주문을 조회한다. 없으면 null을 반환해 호출부에서 명시적으로 처리한다.
    fun findByOrderId(orderId: String): Order?
}
