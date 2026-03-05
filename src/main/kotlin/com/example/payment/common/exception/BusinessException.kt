package com.example.payment.common.exception

import java.math.BigDecimal

/**
 * 모든 비즈니스 예외의 최상위 클래스.
 *
 * RuntimeException을 상속해 트랜잭션 롤백이 자동으로 적용되게 한다.
 * GlobalExceptionHandler가 이 타입 하나만 잡아도 모든 비즈니스 예외를 처리할 수 있다.
 */
abstract class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
) : RuntimeException(message)

// ── Product ──────────────────────────────────────────────

class ProductNotFoundException : BusinessException(ErrorCode.PRODUCT_NOT_FOUND)

/**
 * 현재 재고 수량을 메시지에 포함해 클라이언트가 상황을 파악할 수 있게 한다.
 */
class OutOfStockException(currentStock: Int) : BusinessException(
    errorCode = ErrorCode.OUT_OF_STOCK,
    message = "재고가 부족합니다. 현재 재고: $currentStock",
)

// ── Order ────────────────────────────────────────────────

class OrderNotFoundException : BusinessException(ErrorCode.ORDER_NOT_FOUND)

class InvalidOrderStatusException : BusinessException(ErrorCode.INVALID_ORDER_STATUS)

// ── Payment ──────────────────────────────────────────────

class PaymentNotFoundException : BusinessException(ErrorCode.PAYMENT_NOT_FOUND)

class AlreadyPaidException : BusinessException(ErrorCode.ALREADY_PAID)

class InvalidPaymentStatusException : BusinessException(ErrorCode.INVALID_PAYMENT_STATUS)

/**
 * 주문 금액과 결제 금액을 모두 메시지에 포함해 불일치 내용을 명확히 전달한다.
 */
class PaymentAmountMismatchException(expected: BigDecimal, actual: BigDecimal) : BusinessException(
    errorCode = ErrorCode.PAYMENT_AMOUNT_MISMATCH,
    message = "금액이 일치하지 않습니다. 주문: $expected, 결제 요청: $actual",
)

// ── Concurrency ──────────────────────────────────────────

class ConcurrentUpdateException : BusinessException(ErrorCode.CONCURRENT_UPDATE)
