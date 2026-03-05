package com.example.payment.payment.controller

import com.example.payment.common.response.ApiResponse
import com.example.payment.payment.dto.CancelPaymentRequest
import com.example.payment.payment.dto.ConfirmPaymentRequest
import com.example.payment.payment.dto.PaymentResponse
import com.example.payment.payment.service.PaymentService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 결제 API 엔드포인트.
 *
 * 결제 승인(confirm)과 취소(cancel)만 제공한다.
 * 결제 승인은 멱등성 키로 중복 요청을 방지하므로 POST지만 안전하게 재시도 가능하다.
 */
@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val paymentService: PaymentService,
) {
    /**
     * 결제 승인.
     * 200 OK를 반환하는 이유: 멱등성 키로 인해 새 리소스 생성 여부가 불확실하므로
     * 201 Created보다 200 OK가 더 적절하다.
     */
    @PostMapping("/confirm")
    fun confirmPayment(@Valid @RequestBody request: ConfirmPaymentRequest): ApiResponse<PaymentResponse> =
        ApiResponse.success(paymentService.confirmPayment(request))

    @PostMapping("/{paymentKey}/cancel")
    fun cancelPayment(
        @PathVariable paymentKey: String,
        @Valid @RequestBody request: CancelPaymentRequest,
    ): ApiResponse<PaymentResponse> =
        ApiResponse.success(paymentService.cancelPayment(paymentKey, request))
}
