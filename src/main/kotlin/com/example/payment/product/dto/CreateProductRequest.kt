package com.example.payment.product.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

/**
 * 상품 등록 요청 DTO.
 * Controller에서 @Valid로 검증되며, 통과한 값만 Service로 전달된다.
 */
data class CreateProductRequest(
    @field:NotBlank(message = "상품명은 필수입니다.")
    val name: String,

    @field:NotNull(message = "가격은 필수입니다.")
    @field:DecimalMin(value = "0.01", message = "가격은 0보다 커야 합니다.")
    val price: BigDecimal,

    @field:NotNull(message = "재고는 필수입니다.")
    @field:Min(value = 1, message = "재고는 1 이상이어야 합니다.")
    val stock: Int,
)
