package com.depromeet.team2.global.payload

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val isSuccess: Boolean = true,
    val code: String,
    val message: String,
    val result: T? = null,
    val timestamp: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        fun <T> of(
            successCode: SuccessCode,
            result: T? = null,
        ): ResponseEntity<ApiResponse<T>> =
            ResponseEntity
                .status(successCode.status)
                .body(ApiResponse(code = successCode.code, message = successCode.message, result = result))

        fun <T> ok(result: T? = null) = of(SuccessCode.OK, result)

        fun <T> created(result: T? = null) = of(SuccessCode.CREATED, result)
    }
}
