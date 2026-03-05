package com.example.payment.product.service

import com.example.payment.common.exception.ProductNotFoundException
import com.example.payment.product.domain.Product
import com.example.payment.product.dto.CreateProductRequest
import com.example.payment.product.repository.ProductRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.Optional

/**
 * ProductService 단위 테스트.
 * Repository를 MockK로 모킹해 Service의 비즈니스 로직만 검증한다.
 */
class ProductServiceTest {

    private lateinit var productRepository: ProductRepository
    private lateinit var productService: ProductService

    @BeforeEach
    fun setUp() {
        productRepository = mockk()
        productService = ProductService(productRepository)
    }

    @Test
    fun `상품을 정상적으로 등록한다`() {
        // given
        val request = CreateProductRequest(
            name = "테스트 노트북",
            price = BigDecimal("1500000.00"),
            stock = 100,
        )

        val savedProduct = createProduct(name = "테스트 노트북", price = BigDecimal("1500000.00"), stock = 100)

        // save 호출 시 전달된 엔티티를 그대로 반환 (ID/createdAt 세팅된 상태)
        every { productRepository.save(any<Product>()) } returns savedProduct

        // when
        val response = productService.createProduct(request)

        // then
        assertEquals("테스트 노트북", response.name)
        assertEquals(BigDecimal("1500000.00"), response.price)
        assertEquals(100, response.stock)
    }

    @Test
    fun `상품 목록을 조회한다`() {
        // given
        val products = listOf(
            createProduct(name = "노트북", price = BigDecimal("1500000.00"), stock = 10),
            createProduct(name = "마우스", price = BigDecimal("35000.00"), stock = 200),
        )
        every { productRepository.findAll() } returns products

        // when
        val response = productService.getProducts()

        // then
        assertEquals(2, response.size)
        assertEquals("노트북", response[0].name)
        assertEquals("마우스", response[1].name)
    }

    @Test
    fun `상품 상세를 조회한다`() {
        // given
        val product = createProduct(name = "키보드", price = BigDecimal("89000.00"), stock = 50)
        every { productRepository.findById(1L) } returns Optional.of(product)

        // when
        val response = productService.getProduct(1L)

        // then
        assertEquals("키보드", response.name)
        assertEquals(BigDecimal("89000.00"), response.price)
    }

    @Test
    fun `존재하지 않는 상품 조회 시 예외가 발생한다`() {
        // given
        every { productRepository.findById(999L) } returns Optional.empty()

        // when & then
        assertThrows<ProductNotFoundException> {
            productService.getProduct(999L)
        }
    }

    // ── 테스트 헬퍼 ──────────────────────────────────────────

    /**
     * 테스트용 Product 인스턴스를 생성한다.
     * 리플렉션으로 id와 createdAt을 설정해 저장 완료 상태를 시뮬레이션한다.
     */
    private fun createProduct(
        id: Long = 1L,
        name: String,
        price: BigDecimal,
        stock: Int,
    ): Product {
        val product = Product(name = name, price = price, stock = stock)
        // 리플렉션으로 JPA가 자동 설정하는 필드를 직접 채운다
        setField(product, "id", id)
        setField(product, "createdAt", java.time.LocalDateTime.now())
        setField(product, "updatedAt", java.time.LocalDateTime.now())
        return product
    }

    private fun setField(target: Any, fieldName: String, value: Any) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }
}
