package com.depromeet.team2.global.payload

import com.depromeet.team2.global.exception.ErrorCode
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val isSuccess: Boolean = false,
    val code: String,
    val message: String,
    val detail: String? = null,
    val fieldErrors: List<FieldErrorResponse>? = null,
    val timestamp: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        fun of(
            errorCode: ErrorCode,
            detail: String? = null,
            fieldErrors: List<FieldErrorResponse>? = null,
        ) = ErrorResponse(
            code = errorCode.code,
            message = errorCode.message,
            detail = detail,
            fieldErrors = fieldErrors,
        )
    }
}
