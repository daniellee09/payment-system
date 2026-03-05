package com.example.payment.order.domain

import com.example.payment.common.exception.InvalidOrderStatusException
import com.example.payment.product.domain.Product
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 엔티티.
 *
 * orderId는 외부에 노출하는 UUID 기반 식별자이고, id는 DB 내부 PK다.
 * 외부 API에서 Long 타입 PK를 직접 노출하면 순차 탐색 공격에 취약하므로 분리한다.
 *
 * 상태 전이(markAsPaid, markAsCancelled)를 엔티티 내부에 캡슐화해
 * 어디서 호출하든 전이 가능 여부를 항상 검증하게 한다.
 */
@Entity
@Table(name = "orders")
@EntityListeners(AuditingEntityListener::class)
class Order(
    product: Product,
    quantity: Int,
    totalAmount: BigDecimal,
    customerName: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    // 외부 노출용 식별자. 생성 시 UUID를 즉시 할당해 DB 저장 전에도 참조 가능하다.
    @Column(nullable = false, unique = true, length = 36)
    val orderId: String = UUID.randomUUID().toString()

    // LAZY: 주문을 조회할 때마다 상품을 자동으로 JOIN하면 불필요한 쿼리가 발생한다.
    // 상품 정보가 필요한 시점(트랜잭션 내)에만 로드하도록 지연 로딩을 사용한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product = product

    @Column(nullable = false)
    val quantity: Int = quantity

    @Column(nullable = false, precision = 15, scale = 2)
    val totalAmount: BigDecimal = totalAmount

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus = OrderStatus.CREATED
        protected set

    @Column(nullable = false)
    val customerName: String = customerName

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null
        protected set

    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: LocalDateTime? = null
        protected set

    /**
     * 결제 완료 처리. CREATED 상태에서만 호출 가능하다.
     * 다른 상태(PAID, CANCELLED)에서 호출하면 409 Conflict를 응답한다.
     */
    fun markAsPaid() {
        if (status != OrderStatus.CREATED) {
            throw InvalidOrderStatusException()
        }
        status = OrderStatus.PAID
    }

    /**
     * 주문 취소 처리. PAID 상태에서만 호출 가능하다.
     * 결제가 승인된 주문만 취소할 수 있고, 결제 취소와 반드시 같은 트랜잭션에서 호출해야 한다.
     */
    fun markAsCancelled() {
        if (status != OrderStatus.PAID) {
            throw InvalidOrderStatusException()
        }
        status = OrderStatus.CANCELLED
    }
}
