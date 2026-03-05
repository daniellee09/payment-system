package com.example.payment.payment.dto

import jakarta.validation.constraints.NotBlank

/**
 * 결제 취소 요청 DTO.
 * 취소 사유를 필수로 받아 이력 관리와 고객 안내에 활용한다.
 */
data class CancelPaymentRequest(
    @field:NotBlank(message = "취소 사유는 필수입니다.")
    val cancelReason: String,
)
