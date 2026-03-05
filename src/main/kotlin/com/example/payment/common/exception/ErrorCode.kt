package com.example.payment.common.exception

import org.springframework.http.HttpStatus

/**
 * 비즈니스 에러 코드 목록.
 *
 * HTTP 상태 코드와 기본 메시지를 에러 코드와 함께 한 곳에서 관리한다.
 * 새로운 에러를 추가할 때 이 파일만 수정하면 GlobalExceptionHandler가 자동으로 처리한다.
 */
enum class ErrorCode(val status: HttpStatus, val message: String) {
    // 400 Bad Request
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "주문 금액과 결제 금액이 일치하지 않습니다."),

    // 404 Not Found
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다."),

    // 409 Conflict
    OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
    ALREADY_PAID(HttpStatus.CONFLICT, "이미 결제된 주문입니다."),
    INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "현재 주문 상태에서는 해당 작업을 수행할 수 없습니다."),
    INVALID_PAYMENT_STATUS(HttpStatus.CONFLICT, "현재 결제 상태에서는 해당 작업을 수행할 수 없습니다."),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "동시 요청이 충돌했습니다. 잠시 후 다시 시도해주세요."),

    // 500 Internal Server Error
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
}
