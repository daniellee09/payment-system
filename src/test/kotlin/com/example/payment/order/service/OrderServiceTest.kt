package com.example.payment.order.service

import com.example.payment.common.TestFixtures.createOrder
import com.example.payment.common.TestFixtures.createProduct
import com.example.payment.common.TestFixtures.setField
import com.example.payment.common.exception.OrderNotFoundException
import com.example.payment.common.exception.OutOfStockException
import com.example.payment.common.exception.ProductNotFoundException
import com.example.payment.common.lock.DistributedLockManager
import com.example.payment.order.domain.Order
import com.example.payment.order.domain.OrderStatus
import com.example.payment.order.dto.CreateOrderRequest
import com.example.payment.order.repository.OrderRepository
import com.example.payment.payment.repository.PaymentRepository
import com.example.payment.product.repository.ProductRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * OrderService 단위 테스트.
 * ProductRepository, OrderRepository, DistributedLockManager를 MockK로 모킹해 비즈니스 로직만 검증한다.
 */
class OrderServiceTest {

    private lateinit var orderRepository: OrderRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var distributedLockManager: DistributedLockManager
    private lateinit var orderService: OrderService

    @BeforeEach
    fun setUp() {
        orderRepository = mockk()
        productRepository = mockk()
        paymentRepository = mockk()
        distributedLockManager = mockk()

        // 단위 테스트에서는 실제 Redis 락 없이 block을 그대로 실행한다.
        // 분산 락 동작은 ConcurrencyTest(통합 테스트)에서 검증한다.
        // any<() -> Any>()로 타입을 명시해야 Kotlin 컴파일러가 withLock<T>의 T를 추론할 수 있다.
        @Suppress("UNCHECKED_CAST")
        every { distributedLockManager.withLock(any<String>(), any<() -> Any>()) } answers {
            (args[1] as () -> Any).invoke()
        }

        orderService = OrderService(orderRepository, productRepository, paymentRepository, distributedLockManager)
    }

    @Test
    fun `주문을 정상적으로 생성하고 재고를 차감한다`() {
        // given
        val product = createProduct(id = 1L, name = "노트북", price = BigDecimal("1500000.00"), stock = 10)
        val request = CreateOrderRequest(productId = product.productId, quantity = 2, customerName = "홍길동")

        every { productRepository.findByProductId(product.productId) } returns product
        every { orderRepository.save(any<Order>()) } answers {
            val order = firstArg<Order>()
            setField(order, "id", 1L)
            setField(order, "createdAt", LocalDateTime.now())
            setField(order, "updatedAt", LocalDateTime.now())
            order
        }

        // when
        val response = orderService.createOrder(request)

        // then
        assertEquals("노트북", response.productName)
        assertEquals(2, response.quantity)
        // 1500000.00 * 2 = 3000000.00
        assertEquals(BigDecimal("3000000.00"), response.totalAmount)
        assertEquals(OrderStatus.CREATED, response.status)
        // 재고 차감 확인: 10 - 2 = 8
        assertEquals(8, product.stock)
    }

    @Test
    fun `존재하지 않는 상품으로 주문 시 예외가 발생한다`() {
        // given
        val request = CreateOrderRequest(productId = "non-existent-product-id", quantity = 1, customerName = "홍길동")
        every { productRepository.findByProductId("non-existent-product-id") } returns null

        // when & then
        assertThrows<ProductNotFoundException> {
            orderService.createOrder(request)
        }
    }

    @Test
    fun `재고 부족 시 예외가 발생한다`() {
        // given: 재고 3개인 상품에 5개 주문
        val product = createProduct(id = 1L, name = "마우스", price = BigDecimal("35000.00"), stock = 3)
        val request = CreateOrderRequest(productId = product.productId, quantity = 5, customerName = "홍길동")

        every { productRepository.findByProductId(product.productId) } returns product

        // when & then
        val exception = assertThrows<OutOfStockException> {
            orderService.createOrder(request)
        }
        // 재고 수량이 에러 메시지에 포함되는지 확인
        assert(exception.message?.contains("3") == true)
    }

    @Test
    fun `주문을 orderId로 조회한다`() {
        // given
        val product = createProduct(id = 1L, name = "키보드", price = BigDecimal("89000.00"), stock = 50)
        val order = createOrder(product = product, quantity = 1, totalAmount = BigDecimal("89000.00"))

        every { orderRepository.findByOrderId(order.orderId) } returns order

        // when
        val response = orderService.getOrder(order.orderId)

        // then
        assertEquals(order.orderId, response.orderId)
        assertEquals("키보드", response.productName)
    }

    @Test
    fun `존재하지 않는 orderId로 조회 시 예외가 발생한다`() {
        // given
        every { orderRepository.findByOrderId("non-existent-id") } returns null

        // when & then
        assertThrows<OrderNotFoundException> {
            orderService.getOrder("non-existent-id")
        }
    }

    @Test
    fun `서버에서 총 금액을 계산하므로 클라이언트 금액은 무시된다`() {
        // given: 가격 10000원 * 수량 3 = 30000원이 서버에서 계산되어야 한다
        val product = createProduct(id = 1L, name = "테스트 상품", price = BigDecimal("10000.00"), stock = 100)
        val request = CreateOrderRequest(productId = product.productId, quantity = 3, customerName = "홍길동")

        every { productRepository.findByProductId(product.productId) } returns product
        every { orderRepository.save(any<Order>()) } answers {
            val order = firstArg<Order>()
            setField(order, "id", 1L)
            setField(order, "createdAt", LocalDateTime.now())
            setField(order, "updatedAt", LocalDateTime.now())
            order
        }

        // when
        val response = orderService.createOrder(request)

        // then: 서버가 계산한 금액 검증
        assertEquals(BigDecimal("30000.00"), response.totalAmount)
    }
}
