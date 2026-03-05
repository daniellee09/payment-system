package com.example.payment.product.dto

import com.example.payment.product.domain.Product
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 상품 조회 응답 DTO.
 *
 * 엔티티를 직접 반환하지 않고 DTO로 변환하는 이유:
 * - 엔티티의 내부 구조(version, updatedAt 등)를 외부에 노출하지 않기 위해
 * - 응답 형식이 바뀌어도 엔티티 변경 없이 DTO만 수정하면 된다
 */
data class ProductResponse(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val stock: Int,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(product: Product): ProductResponse = ProductResponse(
            // 저장 완료된 엔티티는 id와 createdAt이 반드시 존재한다.
            // !! 대신 requireNotNull을 사용해 null인 경우 명확한 오류 메시지를 제공한다.
            id = requireNotNull(product.id) { "저장된 상품의 id는 null일 수 없습니다." },
            name = product.name,
            price = product.price,
            stock = product.stock,
            createdAt = requireNotNull(product.createdAt) { "@EnableJpaAuditing이 활성화되어 있어야 합니다." },
        )
    }
}
