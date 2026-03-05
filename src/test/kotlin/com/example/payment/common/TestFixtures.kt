package com.example.payment.common

import com.example.payment.order.domain.Order
import com.example.payment.payment.domain.Payment
import com.example.payment.payment.domain.PaymentMethod
import com.example.payment.payment.domain.PaymentStatus
import com.example.payment.product.domain.Product
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 테스트 전용 공통 헬퍼.
 *
 * 모든 Service 테스트에서 반복되는 엔티티 생성 로직과 리플렉션 유틸리티를 한 곳에 모은다.
 * 중복 코드를 제거하고, 엔티티 구조가 바뀌면 이 파일만 수정하면 된다.
 */
object TestFixtures {

    /**
     * 리플렉션으로 JPA가 자동 설정하는 필드(id, createdAt 등)를 직접 채운다.
     * 단위 테스트에서는 DB가 없으므로 이 방법으로 저장 완료 상태를 시뮬레이션한다.
     */
    fun setField(target: Any, fieldName: String, value: Any) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    fun createProduct(
        id: Long = 1L,
        name: String = "테스트 상품",
        price: BigDecimal = BigDecimal("15000.00"),
        stock: Int,
    ): Product {
        val product = Product(name = name, price = price, stock = stock)
        setField(product, "id", id)
        setField(product, "createdAt", LocalDateTime.now())
        setField(product, "updatedAt", LocalDateTime.now())
        return product
    }

    fun createOrder(
        id: Long = 1L,
        product: Product,
        quantity: Int = 2,
        totalAmount: BigDecimal,
        customerName: String = "테스트 고객",
    ): Order {
        val order = Order(
            product = product,
            quantity = quantity,
            totalAmount = totalAmount,
            customerName = customerName,
        )
        setField(order, "id", id)
        setField(order, "createdAt", LocalDateTime.now())
        setField(order, "updatedAt", LocalDateTime.now())
        return order
    }

    fun createPayment(
        order: Order,
        amount: BigDecimal,
        status: PaymentStatus = PaymentStatus.READY,
        idempotencyKey: String = "test-idempotency-key",
    ): Payment {
        val payment = Payment(
            order = order,
            amount = amount,
            paymentMethod = PaymentMethod.CARD,
            idempotencyKey = idempotencyKey,
        )
        setField(payment, "createdAt", LocalDateTime.now())
        setField(payment, "updatedAt", LocalDateTime.now())

        // 상태를 원하는 값으로 설정 (READY가 아닌 경우)
        when (status) {
            PaymentStatus.DONE -> payment.approve()
            PaymentStatus.CANCELLED -> {
                payment.approve()
                // cancel()은 reason 파라미터가 필요하고 order 상태도 변경되므로
                // 테스트에서는 리플렉션으로 상태만 직접 설정하는 것이 더 명확하다.
                setField(payment, "status", PaymentStatus.CANCELLED)
            }
            PaymentStatus.READY -> { /* 기본값 */ }
        }
        return payment
    }
}
