package com.example.payment.payment.service

import com.example.payment.common.TestFixtures.createOrder
import com.example.payment.common.TestFixtures.createPayment
import com.example.payment.common.TestFixtures.createProduct
import com.example.payment.common.TestFixtures.setField
import com.example.payment.common.exception.AlreadyPaidException
import com.example.payment.common.exception.InvalidPaymentStatusException
import com.example.payment.common.exception.OrderNotFoundException
import com.example.payment.common.exception.PaymentAmountMismatchException
import com.example.payment.common.exception.PaymentNotFoundException
import com.example.payment.order.domain.OrderStatus
import com.example.payment.order.repository.OrderRepository
import com.example.payment.payment.domain.Payment
import com.example.payment.payment.domain.PaymentMethod
import com.example.payment.payment.domain.PaymentStatus
import com.example.payment.payment.dto.CancelPaymentRequest
import com.example.payment.payment.dto.ConfirmPaymentRequest
import com.example.payment.payment.repository.PaymentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * PaymentService 단위 테스트.
 *
 * 결제 승인(멱등성 검증, 금액 검증, 이미 결제된 주문 차단),
 * 결제 취소(상태 전이, 재고 복원, 취소 사유 기록)를 검증한다.
 */
class PaymentServiceTest {

    private lateinit var paymentRepository: PaymentRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var paymentService: PaymentService

    @BeforeEach
    fun setUp() {
        paymentRepository = mockk()
        orderRepository = mockk()
        paymentService = PaymentService(paymentRepository, orderRepository)
    }

    // ── 결제 승인 ──────────────────────────────────────────

    @Test
    fun `결제를 정상적으로 승인한다`() {
        // given
        val product = createProduct(stock = 10)
        val order = createOrder(product = product, totalAmount = BigDecimal("30000.00"))
        val request = ConfirmPaymentRequest(
            orderId = order.orderId,
            amount = BigDecimal("30000.00"),
            paymentMethod = PaymentMethod.CARD,
            idempotencyKey = "idempotency-key-1",
        )

        every { paymentRepository.findByIdempotencyKey("idempotency-key-1") } returns null
        every { orderRepository.findByOrderId(order.orderId) } returns order
        every { paymentRepository.saveAndFlush(any<Payment>()) } answers {
            val payment = firstArg<Payment>()
            setField(payment, "createdAt", LocalDateTime.now())
            setField(payment, "updatedAt", LocalDateTime.now())
            payment
        }

        // when
        val response = paymentService.confirmPayment(request)

        // then
        assertEquals(BigDecimal("30000.00"), response.amount)
        assertEquals(PaymentStatus.DONE, response.status)
        assertEquals(PaymentMethod.CARD, response.paymentMethod)
        // 주문 상태도 PAID로 전이되었는지 확인
        assertEquals(OrderStatus.PAID, order.status)
    }

    @Test
    fun `동일한 멱등성 키로 재요청하면 기존 결과를 반환한다`() {
        // given: 이미 처리된 결제가 존재
        val product = createProduct(stock = 10)
        val order = createOrder(product = product, totalAmount = BigDecimal("30000.00"))
        val existingPayment = createPayment(order = order, amount = BigDecimal("30000.00"), status = PaymentStatus.DONE)

        every { paymentRepository.findByIdempotencyKey("duplicate-key") } returns existingPayment

        val request = ConfirmPaymentRequest(
            orderId = order.orderId,
            amount = BigDecimal("30000.00"),
            paymentMethod = PaymentMethod.CARD,
            idempotencyKey = "duplicate-key",
        )

        // when
        val response = paymentService.confirmPayment(request)

        // then: 새 결제를 생성하지 않고 기존 결과를 반환
        assertEquals(PaymentStatus.DONE, response.status)
        // saveAndFlush가 호출되지 않았는지 확인
        verify(exactly = 0) { paymentRepository.saveAndFlush(any<Payment>()) }
    }

    @Test
    fun `존재하지 않는 주문으로 결제 승인 시 예외가 발생한다`() {
        // given
        every { paymentRepository.findByIdempotencyKey(any()) } returns null
        every { orderRepository.findByOrderId("non-existent") } returns null

        val request = ConfirmPaymentRequest(
            orderId = "non-existent",
            amount = BigDecimal("10000.00"),
            paymentMethod = PaymentMethod.CARD,
            idempotencyKey = "key-1",
        )

        // when & then
        assertThrows<OrderNotFoundException> {
            paymentService.confirmPayment(request)
        }
    }

    @Test
    fun `결제 금액이 주문 금액과 다르면 예외가 발생한다`() {
        // given: 주문 금액 30000, 결제 요청 금액 25000
        val product = createProduct(stock = 10)
        val order = createOrder(product = product, totalAmount = BigDecimal("30000.00"))

        every { paymentRepository.findByIdempotencyKey(any()) } returns null
        every { orderRepository.findByOrderId(order.orderId) } returns order

        val request = ConfirmPaymentRequest(
            orderId = order.orderId,
            amount = BigDecimal("25000.00"),
            paymentMethod = PaymentMethod.CARD,
            idempotencyKey = "key-2",
        )

        // when & then
        val exception = assertThrows<PaymentAmountMismatchException> {
            paymentService.confirmPayment(request)
        }
        // 에러 메시지에 주문 금액과 결제 요청 금액이 모두 포함되는지 확인
        assert(exception.message?.contains("30000") == true)
        assert(exception.message?.contains("25000") == true)
    }

    @Test
    fun `이미 결제된 주문에 재결제 시도하면 AlreadyPaidException이 발생한다`() {
        // given: PAID 상태의 주문
        val product = createProduct(stock = 8)
        val order = createOrder(product = product, totalAmount = BigDecimal("30000.00"))
        order.markAsPaid()

        every { paymentRepository.findByIdempotencyKey(any()) } returns null
        every { orderRepository.findByOrderId(order.orderId) } returns order

        val request = ConfirmPaymentRequest(
            orderId = order.orderId,
            amount = BigDecimal("30000.00"),
            paymentMethod = PaymentMethod.CARD,
            idempotencyKey = "new-key",
        )

        // when & then
        assertThrows<AlreadyPaidException> {
            paymentService.confirmPayment(request)
        }
    }

    @Test
    fun `멱등성 재요청 시 금액이 다르면 예외가 발생한다`() {
        // given: 기존 결제는 30000원인데 재요청에서 25000원으로 보낸 경우
        val product = createProduct(stock = 10)
        val order = createOrder(product = product, totalAmount = BigDecimal("30000.00"))
        val existingPayment = createPayment(order = order, amount = BigDecimal("30000.00"), status = PaymentStatus.DONE)

        every { paymentRepository.findByIdempotencyKey("same-key") } returns existingPayment

        val request = ConfirmPaymentRequest(
            orderId = order.orderId,
            amount = BigDecimal("25000.00"),
            paymentMethod = PaymentMethod.CARD,
            idempotencyKey = "same-key",
        )

        // when & then
        assertThrows<PaymentAmountMismatchException> {
            paymentService.confirmPayment(request)
        }
    }

    // ── 결제 취소 ──────────────────────────────────────────

    @Test
    fun `결제를 정상적으로 취소하고 재고를 복원한다`() {
        // given
        val product = createProduct(stock = 8) // 주문으로 2개 차감된 상태
        val order = createOrder(product = product, totalAmount = BigDecimal("30000.00"), quantity = 2)
        // 주문을 PAID 상태로 만든다
        order.markAsPaid()
        val payment = createPayment(order = order, amount = BigDecimal("30000.00"), status = PaymentStatus.DONE)

        every { paymentRepository.findByPaymentKey(payment.paymentKey) } returns payment

        val request = CancelPaymentRequest(cancelReason = "단순 변심")

        // when
        val response = paymentService.cancelPayment(payment.paymentKey, request)

        // then
        assertEquals(PaymentStatus.CANCELLED, response.status)
        assertEquals(OrderStatus.CANCELLED, order.status)
        // 재고 복원 확인: 8 + 2 = 10
        assertEquals(10, product.stock)
        // 취소 사유 기록 확인
        assertEquals("단순 변심", payment.cancelReason)
    }

    @Test
    fun `존재하지 않는 paymentKey로 취소 시 예외가 발생한다`() {
        // given
        every { paymentRepository.findByPaymentKey("non-existent") } returns null

        val request = CancelPaymentRequest(cancelReason = "취소 테스트")

        // when & then
        assertThrows<PaymentNotFoundException> {
            paymentService.cancelPayment("non-existent", request)
        }
    }

    @Test
    fun `이미 취소된 결제를 다시 취소하면 예외가 발생한다`() {
        // given: CANCELLED 상태인 결제
        val product = createProduct(stock = 10)
        val order = createOrder(product = product, totalAmount = BigDecimal("30000.00"))
        val payment = createPayment(order = order, amount = BigDecimal("30000.00"), status = PaymentStatus.CANCELLED)

        every { paymentRepository.findByPaymentKey(payment.paymentKey) } returns payment

        val request = CancelPaymentRequest(cancelReason = "중복 취소 시도")

        // when & then
        assertThrows<InvalidPaymentStatusException> {
            paymentService.cancelPayment(payment.paymentKey, request)
        }
    }

    @Test
    fun `READY 상태의 결제를 취소하면 예외가 발생한다`() {
        // given: 아직 승인되지 않은 READY 상태 결제
        val product = createProduct(stock = 10)
        val order = createOrder(product = product, totalAmount = BigDecimal("30000.00"))
        val payment = createPayment(order = order, amount = BigDecimal("30000.00"), status = PaymentStatus.READY)

        every { paymentRepository.findByPaymentKey(payment.paymentKey) } returns payment

        val request = CancelPaymentRequest(cancelReason = "승인 전 취소 시도")

        // when & then
        assertThrows<InvalidPaymentStatusException> {
            paymentService.cancelPayment(payment.paymentKey, request)
        }
    }
}
