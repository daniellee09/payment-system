package com.example.payment.product.domain

import com.example.payment.common.exception.OutOfStockException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 상품 엔티티.
 *
 * 재고 차감/복원 로직을 엔티티 내부 메서드로 캡슐화한다.
 * 이렇게 하면 Service에서 직접 stock 필드를 건드릴 수 없어
 * 비즈니스 규칙(재고 부족 검증)이 항상 실행됨을 보장한다.
 */
@Entity
@Table(name = "products")
@EntityListeners(AuditingEntityListener::class)
class Product(
    name: String,
    price: BigDecimal,
    stock: Int,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false)
    var name: String = name
        protected set

    // 금액은 소수점 2자리까지, 최대 15자리로 설정 (가격 * 대량 수량을 고려)
    @Column(nullable = false, precision = 15, scale = 2)
    var price: BigDecimal = price
        protected set

    @Column(nullable = false)
    var stock: Int = stock
        protected set

    /**
     * 낙관적 락용 버전 필드.
     * 동시 요청이 같은 상품의 재고를 동시에 차감하려 할 때 Lost Update를 방지한다.
     * JPA가 UPDATE 시 WHERE version = ? 조건을 추가하고, 불일치 시 예외를 던진다.
     */
    @Version
    var version: Long = 0
        protected set

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null
        protected set

    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: LocalDateTime? = null
        protected set

    /**
     * 재고를 차감한다.
     *
     * 확인(check) 후 차감하는 패턴을 쓰지 않고, 직접 비교 후 예외를 던진다.
     * stock >= quantity 확인 후 차감 사이에 다른 트랜잭션이 끼어드는 TOCTOU 문제는
     * @Version 낙관적 락이 UPDATE 레벨에서 막아준다.
     */
    fun decreaseStock(quantity: Int) {
        if (stock < quantity) {
            throw OutOfStockException(stock)
        }
        stock -= quantity
    }

    /**
     * 재고를 복원한다. 결제 취소 시 호출된다.
     * 복원은 항상 성공해야 하므로 별도 검증 없이 더한다.
     */
    fun increaseStock(quantity: Int) {
        stock += quantity
    }
}
