package com.example.payment.payment.service

import com.example.payment.common.exception.OrderNotFoundException
import com.example.payment.common.exception.PaymentAmountMismatchException
import com.example.payment.common.exception.PaymentNotFoundException
import com.example.payment.order.repository.OrderRepository
import com.example.payment.payment.domain.Payment
import com.example.payment.payment.dto.CancelPaymentRequest
import com.example.payment.payment.dto.ConfirmPaymentRequest
import com.example.payment.payment.dto.PaymentResponse
import com.example.payment.payment.repository.PaymentRepository
import com.example.payment.product.repository.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 결제 비즈니스 로직.
 *
 * 결제 승인 시 멱등성 키로 중복 요청을 방지하고, 금액 불일치를 검증한다.
 * 결제 취소 시 주문 상태와 재고를 원래대로 복원해 데이터 일관성을 보장한다.
 */
@Service
@Transactional(readOnly = true)
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
) {
    /**
     * 결제를 승인한다.
     *
     * 처리 순서:
     * 1. 멱등성 키로 기존 결제가 있는지 확인 → 있으면 기존 결과 반환 (이중 결제 방지)
     * 2. 주문 존재 여부 확인
     * 3. 요청 금액과 주문 금액 비교 검증 (변조 방지)
     * 4. Payment 생성 → 즉시 approve() → 주문도 PAID로 전이
     *
     * 멱등성 키 조회를 가장 먼저 수행하는 이유:
     * 네트워크 타임아웃 등으로 클라이언트가 재시도할 때 동일한 결제가 두 번 생성되는 것을 막기 위함이다.
     * 이미 처리된 키가 있으면 나머지 로직을 실행하지 않고 기존 결과를 반환한다.
     */
    @Transactional
    fun confirmPayment(request: ConfirmPaymentRequest): PaymentResponse {
        // 1. 멱등성 키 중복 확인: 이미 같은 키로 결제가 존재하면 기존 결과를 그대로 반환한다.
        val existingPayment = paymentRepository.findByIdempotencyKey(request.idempotencyKey)
        if (existingPayment != null) {
            return PaymentResponse.from(existingPayment)
        }

        // 2. 주문 조회
        val order = orderRepository.findByOrderId(request.orderId)
            ?: throw OrderNotFoundException()

        // 3. 금액 불일치 검증: 클라이언트가 보낸 금액이 주문 금액과 다르면 변조 가능성이 있다.
        // compareTo를 사용하는 이유: BigDecimal의 equals는 스케일까지 비교하므로
        // 10000.00과 10000이 다르게 판단된다. compareTo는 수치만 비교한다.
        if (order.totalAmount.compareTo(request.amount) != 0) {
            throw PaymentAmountMismatchException(expected = order.totalAmount, actual = request.amount)
        }

        // 4. 결제 생성 및 승인
        val payment = Payment(
            order = order,
            amount = request.amount,
            paymentMethod = request.paymentMethod,
            idempotencyKey = request.idempotencyKey,
        )
        payment.approve()

        // 주문 상태도 PAID로 전이 — 결제 승인과 주문 상태 변경을 같은 트랜잭션에서 처리
        order.markAsPaid()

        return PaymentResponse.from(paymentRepository.save(payment))
    }

    /**
     * 결제를 취소한다.
     *
     * 처리 순서:
     * 1. paymentKey로 결제 조회
     * 2. 결제 상태를 CANCELLED로 전이 (DONE에서만 가능)
     * 3. 주문 상태를 CANCELLED로 전이 (PAID에서만 가능)
     * 4. 재고 복원
     *
     * 이 세 가지(결제 취소 + 주문 취소 + 재고 복원)를 하나의 트랜잭션으로 묶는다.
     * 하나라도 실패하면 전체 롤백되어 데이터 불일치를 방지한다.
     */
    @Transactional
    fun cancelPayment(paymentKey: String, request: CancelPaymentRequest): PaymentResponse {
        val payment = paymentRepository.findByPaymentKey(paymentKey)
            ?: throw PaymentNotFoundException()

        // 결제 취소 (DONE -> CANCELLED)
        payment.cancel()

        // 주문 취소 (PAID -> CANCELLED)
        val order = payment.order
        order.markAsCancelled()

        // 재고 복원: 주문 시 차감한 수량만큼 다시 더한다.
        // product는 order의 LAZY 연관이므로 이 시점에 프록시가 초기화된다.
        val product = order.product
        product.increaseStock(order.quantity)

        return PaymentResponse.from(payment)
    }
}
