package com.example.payment.payment.domain

import com.example.payment.common.exception.AlreadyPaidException
import com.example.payment.common.exception.InvalidPaymentStatusException
import com.example.payment.order.domain.Order
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
 * 결제 엔티티.
 *
 * paymentKey는 외부 노출용 UUID 식별자 (Order의 orderId와 동일한 전략).
 * idempotencyKey로 동일한 결제 승인 요청의 중복 처리를 방지한다.
 *
 * 상태 전이(approve, cancel)를 엔티티 내부에 캡슐화해
 * 어디서 호출하든 전이 가능 여부를 항상 검증하게 한다.
 */
@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener::class)
class Payment(
    order: Order,
    amount: BigDecimal,
    paymentMethod: PaymentMethod,
    idempotencyKey: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    // 외부 노출용 식별자. 결제 취소 등에서 이 키로 결제를 식별한다.
    @Column(nullable = false, unique = true, length = 36)
    val paymentKey: String = UUID.randomUUID().toString()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    val order: Order = order

    // 결제 금액. 주문 금액과 일치하는지 Service에서 검증 후 여기에 저장한다.
    @Column(nullable = false, precision = 15, scale = 2)
    val amount: BigDecimal = amount

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentStatus = PaymentStatus.READY
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val paymentMethod: PaymentMethod = paymentMethod

    /**
     * 멱등성 키. 클라이언트가 생성한 UUID로, 동일한 키의 결제 승인이 중복 실행되는 것을 방지한다.
     * UNIQUE 제약조건으로 DB 레벨에서도 중복을 차단한다.
     */
    @Column(nullable = false, unique = true)
    val idempotencyKey: String = idempotencyKey

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null
        protected set

    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: LocalDateTime? = null
        protected set

    /**
     * 결제를 승인한다. READY 상태에서만 호출 가능하다.
     *
     * DONE이나 CANCELLED 상태에서 다시 승인하려 하면 AlreadyPaidException(409)을 던진다.
     * 멱등성 처리는 Service 레이어에서 idempotencyKey로 기존 결제를 조회해 처리하므로,
     * 이 메서드가 호출되는 시점에는 항상 새 결제건이다.
     */
    fun approve() {
        if (status != PaymentStatus.READY) {
            throw AlreadyPaidException()
        }
        status = PaymentStatus.DONE
    }

    /**
     * 결제를 취소한다. DONE 상태에서만 호출 가능하다.
     * READY나 이미 CANCELLED인 상태에서 취소하려 하면 InvalidPaymentStatusException(409)을 던진다.
     * 재고 복원과 주문 상태 변경은 Service 레이어에서 같은 트랜잭션 내에서 처리한다.
     */
    fun cancel() {
        if (status != PaymentStatus.DONE) {
            throw InvalidPaymentStatusException()
        }
        status = PaymentStatus.CANCELLED
    }
}
