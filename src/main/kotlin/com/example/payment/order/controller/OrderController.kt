package com.example.payment.order.controller

import com.example.payment.common.response.ApiResponse
import com.example.payment.order.dto.CreateOrderRequest
import com.example.payment.order.dto.OrderResponse
import com.example.payment.order.service.OrderService
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
 * 주문 API 엔드포인트.
 */
@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderService: OrderService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createOrder(@Valid @RequestBody request: CreateOrderRequest): ApiResponse<OrderResponse> =
        ApiResponse.success(orderService.createOrder(request))

    @GetMapping
    fun getOrders(): ApiResponse<List<OrderResponse>> =
        ApiResponse.success(orderService.getOrders())

    @GetMapping("/{orderId}")
    fun getOrder(@PathVariable orderId: String): ApiResponse<OrderResponse> =
        ApiResponse.success(orderService.getOrder(orderId))
}
