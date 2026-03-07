package com.example.payment.product.service

import com.example.payment.common.config.CacheConfig
import com.example.payment.common.exception.ProductNotFoundException
import com.example.payment.product.domain.Product
import com.example.payment.product.dto.CreateProductRequest
import com.example.payment.product.dto.ProductResponse
import com.example.payment.product.repository.ProductRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 상품 비즈니스 로직.
 *
 * 클래스 레벨에 readOnly = true를 기본으로 설정하고,
 * 쓰기 작업에만 @Transactional을 개별 선언한다.
 * 이렇게 하면 읽기 쿼리에서 Hibernate의 dirty checking을 생략해 성능이 개선된다.
 *
 * 상품 목록/상세 조회는 Redis 캐싱을 적용해 DB 부하를 줄인다.
 * 상품 등록 시 목록 캐시를 무효화해 다음 조회 시 최신 데이터를 가져오도록 한다.
 */
@Service
@Transactional(readOnly = true)
class ProductService(
    private val productRepository: ProductRepository,
) {
    /**
     * 상품 등록 후 목록 캐시를 무효화한다.
     *
     * 새 상품이 추가되면 캐시된 목록은 오래된 데이터가 되므로 즉시 제거한다.
     * allEntries = true: 하나의 캐시 키만 무효화하는 게 아니라 전체 productList 캐시를 삭제한다.
     */
    @Transactional
    @CacheEvict(cacheNames = [CacheConfig.PRODUCT_LIST], allEntries = true)
    fun createProduct(request: CreateProductRequest): ProductResponse {
        val product = Product(
            name = request.name,
            price = request.price,
            stock = request.stock,
        )
        return ProductResponse.from(productRepository.save(product))
    }

    /**
     * 상품 목록을 조회한다. 결과를 Redis에 캐싱해 반복 조회 시 DB 쿼리를 생략한다.
     *
     * 키를 별도로 지정하지 않으면 Spring이 메서드 시그니처 기반으로 자동 생성한다.
     * (파라미터가 없으므로 캐시 키 = "productList::SimpleKey []")
     */
    @Cacheable(cacheNames = [CacheConfig.PRODUCT_LIST])
    fun getProducts(): List<ProductResponse> =
        productRepository.findAll().map { ProductResponse.from(it) }

    /**
     * 상품 상세를 조회한다. productId를 캐시 키로 사용해 상품별로 독립적으로 캐싱한다.
     *
     * key = "#productId": SpEL로 메서드 파라미터를 캐시 키로 사용한다.
     * 결과 키 형식: "productDetail::550e8400-e29b-41d4-a716-..."
     */
    @Cacheable(cacheNames = [CacheConfig.PRODUCT_DETAIL], key = "#productId")
    fun getProduct(productId: String): ProductResponse {
        val product = productRepository.findByProductId(productId)
            ?: throw ProductNotFoundException()
        return ProductResponse.from(product)
    }
}
