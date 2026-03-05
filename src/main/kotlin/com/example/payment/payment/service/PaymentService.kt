package com.example.payment.payment.service

import com.example.payment.common.exception.AlreadyPaidException
import com.example.payment.common.exception.OrderNotFoundException
import com.example.payment.common.exception.PaymentAmountMismatchException
import com.example.payment.common.exception.PaymentNotFoundException
import com.example.payment.order.repository.OrderRepository
import com.example.payment.payment.domain.Payment
import com.example.payment.payment.dto.CancelPaymentRequest
import com.example.payment.payment.dto.ConfirmPaymentRequest
import com.example.payment.payment.dto.PaymentResponse
import com.example.payment.payment.repository.PaymentRepository
import org.springframework.dao.DataIntegrityViolationException
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
) {
    /**
     * 결제를 승인한다.
     *
     * 처리 순서:
     * 1. 멱등성 키로 기존 결제가 있는지 확인 → 있으면 요청 값 검증 후 기존 결과 반환
     * 2. 주문 존재 여부 및 상태(이미 결제된 주문인지) 확인
     * 3. 요청 금액과 주문 금액 비교 검증 (변조 방지)
     * 4. Payment 생성 → saveAndFlush → approve() → 주문도 PAID로 전이
     *
     * Race Condition 방어:
     * 동일한 멱등성 키로 두 스레드가 동시에 진입하면, findByIdempotencyKey에서 둘 다 null을 받는다.
     * 이후 saveAndFlush에서 UNIQUE 제약조건에 의해 하나만 성공하고, 실패한 쪽은
     * DataIntegrityViolationException을 catch해 재조회 후 기존 결과를 반환한다.
     */
    @Transactional
    fun confirmPayment(request: ConfirmPaymentRequest): PaymentResponse {
        // 1. 멱등성 키 중복 확인: 이미 같은 키로 결제가 존재하면 요청 값을 검증한 뒤 기존 결과를 반환한다.
        val existingPayment = paymentRepository.findByIdempotencyKey(request.idempotencyKey)
        if (existingPayment != null) {
            validateIdempotencyRequest(existingPayment, request)
            return PaymentResponse.from(existingPayment)
        }

        // 2. 주문 조회
        val order = orderRepository.findByOrderId(request.orderId)
            ?: throw OrderNotFoundException()

        // 2-1. 이미 결제된 주문에 대한 재결제 시도를 사전에 차단한다.
        // markAsPaid()에서도 검증하지만, 명확한 에러 메시지를 위해 별도로 먼저 확인한다.
        if (order.status == com.example.payment.order.domain.OrderStatus.PAID) {
            throw AlreadyPaidException()
        }

        // 3. 금액 불일치 검증: 클라이언트가 보낸 금액이 주문 금액과 다르면 변조 가능성이 있다.
        // compareTo를 사용하는 이유: BigDecimal의 equals는 스케일까지 비교하므로
        // 10000.00과 10000이 다르게 판단된다. compareTo는 수치만 비교한다.
        if (order.totalAmount.compareTo(request.amount) != 0) {
            throw PaymentAmountMismatchException(expected = order.totalAmount, actual = request.amount)
        }

        // 4. 결제 생성 및 저장
        val payment = Payment(
            order = order,
            amount = request.amount,
            paymentMethod = request.paymentMethod,
            idempotencyKey = request.idempotencyKey,
        )

        // saveAndFlush로 즉시 DB에 반영해 UNIQUE 제약조건 위반을 이 시점에서 감지한다.
        // save만 사용하면 트랜잭션 커밋 시점까지 INSERT가 지연되어 Race Condition을 놓칠 수 있다.
        val savedPayment = try {
            paymentRepository.saveAndFlush(payment)
        } catch (e: DataIntegrityViolationException) {
            // 동시 요청으로 다른 스레드가 먼저 동일 멱등성 키로 저장한 경우
            // 재조회해서 기존 결과를 반환한다.
            val concurrentPayment = paymentRepository.findByIdempotencyKey(request.idempotencyKey)
                ?: throw e // 멱등성 키가 아닌 다른 제약조건 위반이면 원래 예외를 던진다
            return PaymentResponse.from(concurrentPayment)
        }

        savedPayment.approve()

        // 주문 상태도 PAID로 전이 — 결제 승인과 주문 상태 변경을 같은 트랜잭션에서 처리
        order.markAsPaid()

        return PaymentResponse.from(savedPayment)
    }

    /**
     * 멱등성 재요청 시 orderId와 amount가 기존 결제와 일치하는지 검증한다.
     *
     * 같은 멱등성 키인데 다른 주문/금액으로 요청하는 것은 클라이언트 구현 오류이므로
     * 금액 불일치 예외로 거부한다.
     */
    private fun validateIdempotencyRequest(existingPayment: Payment, request: ConfirmPaymentRequest) {
        if (existingPayment.order.orderId != request.orderId) {
            throw PaymentAmountMismatchException(
                expected = existingPayment.amount,
                actual = request.amount,
            )
        }
        if (existingPayment.amount.compareTo(request.amount) != 0) {
            throw PaymentAmountMismatchException(
                expected = existingPayment.amount,
                actual = request.amount,
            )
        }
    }

    /**
     * 결제를 취소한다.
     *
     * 처리 순서:
     * 1. paymentKey로 결제 조회
     * 2. 결제 상태를 CANCELLED로 전이 (DONE에서만 가능) + 취소 사유 기록
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

        // 결제 취소 (DONE -> CANCELLED) + 취소 사유 기록
        payment.cancel(request.cancelReason)

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
