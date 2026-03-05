package com.example.payment.payment.domain

/**
 * 결제 수단 enum.
 * 토스페이먼츠 API에서 사용하는 결제 수단 명칭을 따른다.
 */
enum class PaymentMethod {
    CARD,           // 카드 결제
    VIRTUAL_ACCOUNT, // 가상계좌
    EASY_PAY,       // 간편결제
    TRANSFER,       // 계좌이체
}
