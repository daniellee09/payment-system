package com.example.payment.product.service

import com.example.payment.common.exception.ProductNotFoundException
import com.example.payment.product.domain.Product
import com.example.payment.product.dto.CreateProductRequest
import com.example.payment.product.dto.ProductResponse
import com.example.payment.product.repository.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 상품 비즈니스 로직.
 *
 * 클래스 레벨에 readOnly = true를 기본으로 설정하고,
 * 쓰기 작업에만 @Transactional을 개별 선언한다.
 * 이렇게 하면 읽기 쿼리에서 Hibernate의 dirty checking을 생략해 성능이 개선된다.
 */
@Service
@Transactional(readOnly = true)
class ProductService(
    private val productRepository: ProductRepository,
) {
    @Transactional
    fun createProduct(request: CreateProductRequest): ProductResponse {
        val product = Product(
            name = request.name,
            price = request.price,
            stock = request.stock,
        )
        return ProductResponse.from(productRepository.save(product))
    }

    fun getProducts(): List<ProductResponse> =
        productRepository.findAll().map { ProductResponse.from(it) }

    fun getProduct(productId: Long): ProductResponse {
        val product = productRepository.findById(productId)
            .orElseThrow { ProductNotFoundException() }
        return ProductResponse.from(product)
    }
}
