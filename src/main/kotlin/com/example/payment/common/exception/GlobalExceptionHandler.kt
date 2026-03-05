package com.example.payment.common.exception

import com.example.payment.common.response.ApiResponse
import com.example.payment.common.response.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 애플리케이션 전역 예외 처리기.
 *
 * 예외 처리 로직을 한 곳에 모아 Controller마다 try-catch를 작성하지 않아도 된다.
 * 계층적으로 처리한다: BusinessException → 낙관적 락 예외 → Validation → 그 외 모든 예외
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 비즈니스 예외: ErrorCode에 정의된 HTTP 상태 코드와 메시지를 그대로 사용한다.
     */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("비즈니스 예외 발생: [{}] {}", e.errorCode.name, e.message)
        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiResponse.error(ErrorResponse(code = e.errorCode.name, message = e.message)))
    }

    /**
     * JPA 낙관적 락 충돌: @Version 충돌 시 Spring ORM이 던지는 예외를 CONCURRENT_UPDATE로 변환한다.
     * 클라이언트는 409를 받고 재시도할 수 있다.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLockingFailure(e: ObjectOptimisticLockingFailureException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("낙관적 락 충돌 발생: {}", e.message)
        val errorCode = ErrorCode.CONCURRENT_UPDATE
        return ResponseEntity
            .status(errorCode.status)
            .body(ApiResponse.error(ErrorResponse(code = errorCode.name, message = errorCode.message)))
    }

    /**
     * 요청 본문 Validation 실패: 모든 필드 에러를 하나의 메시지로 합쳐서 반환한다.
     * 어떤 필드가 왜 잘못됐는지 클라이언트에게 명확히 전달하기 위해 필드명을 포함한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val message = e.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("요청 검증 실패: {}", message)
        return ResponseEntity
            .status(ErrorCode.INVALID_REQUEST.status)
            .body(ApiResponse.error(ErrorResponse(code = ErrorCode.INVALID_REQUEST.name, message = message)))
    }

    /**
     * 예상치 못한 모든 예외: 내부 오류 내용은 외부에 노출하지 않고 로그로만 남긴다.
     * 운영 환경에서 스택 트레이스가 응답에 노출되는 것을 방지한다.
     */
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("예상치 못한 예외 발생", e)
        val errorCode = ErrorCode.INTERNAL_ERROR
        return ResponseEntity
            .status(errorCode.status)
            .body(ApiResponse.error(ErrorResponse(code = errorCode.name, message = errorCode.message)))
    }
}
