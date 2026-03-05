package com.example.payment.payment.dto

import com.example.payment.payment.domain.PaymentMethod
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

/**
 * 결제 승인 요청 DTO.
 *
 * idempotencyKey는 클라이언트가 생성한 UUID로, 동일한 결제를 중복 요청했을 때
 * 서버가 이를 감지하고 기존 결과를 반환할 수 있게 한다.
 */
data class ConfirmPaymentRequest(
    @field:NotBlank(message = "주문 ID는 필수입니다.")
    val orderId: String,

    @field:NotNull(message = "결제 금액은 필수입니다.")
    @field:DecimalMin(value = "0.01", message = "결제 금액은 0보다 커야 합니다.")
    val amount: BigDecimal,

    @field:NotNull(message = "결제 수단은 필수입니다.")
    val paymentMethod: PaymentMethod,

    @field:NotBlank(message = "멱등성 키는 필수입니다.")
    val idempotencyKey: String,
)
