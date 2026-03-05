package com.example.payment.order.repository

import com.example.payment.order.domain.Order
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface OrderRepository : JpaRepository<Order, Long> {
    /**
     * 외부 노출용 orderId(UUID)로 주문을 조회한다.
     *
     * @EntityGraph로 product를 즉시 로딩한다.
     * OSIV가 꺼져 있으므로 트랜잭션 밖에서 order.product에 접근하면
     * LazyInitializationException이 발생한다.
     * EntityGraph로 조회 시점에 product를 함께 가져와 이 문제를 방지한다.
     */
    @EntityGraph(attributePaths = ["product"])
    fun findByOrderId(orderId: String): Order?
}
