package com.example.payment.product.repository

import com.example.payment.product.domain.Product
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRepository : JpaRepository<Product, Long> {
    // 외부 노출용 productId(UUID)로 상품을 조회한다. 없으면 null을 반환해 호출부에서 명시적으로 처리한다.
    fun findByProductId(productId: String): Product?
}
