package com.example.payment.order.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/**
 * 주문 생성 요청 DTO.
 */
data class CreateOrderRequest(
    @field:NotNull(message = "상품 ID는 필수입니다.")
    val productId: Long,

    @field:NotNull(message = "수량은 필수입니다.")
    @field:Min(value = 1, message = "수량은 1 이상이어야 합니다.")
    val quantity: Int,

    @field:NotBlank(message = "주문자 이름은 필수입니다.")
    val customerName: String,
)
