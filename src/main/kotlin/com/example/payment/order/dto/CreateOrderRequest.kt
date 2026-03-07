package com.example.payment.order.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/**
 * 주문 생성 요청 DTO.
 *
 * productId는 외부 노출용 UUID 문자열을 받는다.
 * 내부 Long PK를 외부에 노출하면 순차 탐색 공격에 취약하므로,
 * Product 엔티티의 productId(UUID)로 조회한다.
 */
data class CreateOrderRequest(
    @field:NotBlank(message = "상품 ID는 필수입니다.")
    val productId: String,

    @field:NotNull(message = "수량은 필수입니다.")
    @field:Min(value = 1, message = "수량은 1 이상이어야 합니다.")
    val quantity: Int,

    @field:NotBlank(message = "주문자 이름은 필수입니다.")
    val customerName: String,
)
