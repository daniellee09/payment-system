package com.example.payment.common.response

import java.time.LocalDateTime

/**
 * 모든 API 응답을 감싸는 공통 래퍼.
 *
 * 성공/실패 여부를 success 필드 하나로 구분하고,
 * 나머지 구조를 항상 동일하게 유지해 클라이언트가 일관된 방식으로 응답을 처리할 수 있게 한다.
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ErrorResponse? = null,
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> =
            ApiResponse(success = true, data = data)

        fun error(error: ErrorResponse): ApiResponse<Nothing> =
            ApiResponse(success = false, error = error)
    }
}

/**
 * 에러 응답 본문.
 * code는 클라이언트가 에러 종류를 코드로 분기할 수 있도록 ErrorCode enum 이름을 그대로 사용한다.
 */
data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
)
