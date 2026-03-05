package com.example.payment.product.controller

import com.example.payment.common.response.ApiResponse
import com.example.payment.product.dto.CreateProductRequest
import com.example.payment.product.dto.ProductResponse
import com.example.payment.product.service.ProductService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 상품 API 엔드포인트.
 * Controller는 요청 검증(@Valid)과 응답 변환만 담당한다. 비즈니스 로직은 Service에 위임한다.
 */
@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val productService: ProductService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createProduct(@Valid @RequestBody request: CreateProductRequest): ApiResponse<ProductResponse> =
        ApiResponse.success(productService.createProduct(request))

    @GetMapping
    fun getProducts(): ApiResponse<List<ProductResponse>> =
        ApiResponse.success(productService.getProducts())

    @GetMapping("/{productId}")
    fun getProduct(@PathVariable productId: Long): ApiResponse<ProductResponse> =
        ApiResponse.success(productService.getProduct(productId))
}
