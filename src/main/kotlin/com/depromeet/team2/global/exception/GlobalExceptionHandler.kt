package com.depromeet.team2.global.exception

import com.depromeet.team2.global.payload.ErrorResponse
import com.depromeet.team2.global.payload.FieldErrorResponse
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import tools.jackson.databind.exc.MismatchedInputException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(CodeException::class)
    fun handleCode(e: CodeException): ResponseEntity<ErrorResponse> = respond(e.errorCode)

    @ExceptionHandler(DynamicException::class)
    fun handleDynamic(e: DynamicException) = respond(e.errorCode, detail = e.detail, cause = e)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleNotValid(e: MethodArgumentNotValidException) =
        respond(
            CommonErrorCode.COMMON_INVALID_REQUEST,
            fieldErrors =
                e.bindingResult.fieldErrors.map { FieldErrorResponse(it.field, it.defaultMessage, it.rejectedValue) },
        )

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidation(e: HandlerMethodValidationException) =
        if (e.isForReturnValue) {
            respond(CommonErrorCode.COMMON_INTERNAL_ERROR, cause = e)
        } else {
            respond(
                CommonErrorCode.COMMON_INVALID_REQUEST,
                fieldErrors =
                    e.parameterValidationResults.flatMap { result ->
                        val name = result.methodParameter.parameterName ?: "unknown"
                        result.resolvableErrors.map { FieldErrorResponse(name, it.defaultMessage) }
                    },
            )
        }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(e: ConstraintViolationException) =
        respond(
            CommonErrorCode.COMMON_INVALID_REQUEST,
            fieldErrors =
                e.constraintViolations.map {
                    FieldErrorResponse(it.propertyPath.toString(), it.message, it.invalidValue)
                },
        )

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(e: MissingServletRequestParameterException) =
        respond(
            CommonErrorCode.COMMON_INVALID_REQUEST,
            fieldErrors = listOf(FieldErrorResponse(e.parameterName, "필수 파라미터가 누락되었습니다.")),
        )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException) =
        respond(
            CommonErrorCode.COMMON_INVALID_REQUEST,
            fieldErrors = listOf(FieldErrorResponse(e.name, typeMismatchReason(e.requiredType))),
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        val cause = e.cause as? MismatchedInputException ?: return respond(CommonErrorCode.COMMON_INVALID_JSON)
        val field =
            cause.path
                .mapNotNull { it.propertyName }
                .joinToString(".")
                .ifEmpty { "unknown" }
        return respond(
            CommonErrorCode.COMMON_INVALID_REQUEST,
            fieldErrors = listOf(FieldErrorResponse(field, typeMismatchReason(cause.targetType))),
        )
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity() = respond(CommonErrorCode.COMMON_DATA_CONFLICT)

    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    fun handleNotFound() = respond(CommonErrorCode.COMMON_ENDPOINT_NOT_FOUND)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(e: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> {
        val response = respond(CommonErrorCode.COMMON_METHOD_NOT_ALLOWED)
        return ResponseEntity
            .status(response.statusCode)
            .allow(*e.supportedHttpMethods.orEmpty().toTypedArray())
            .body(response.body)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception) = respond(CommonErrorCode.COMMON_INTERNAL_ERROR, cause = e)

    private fun respond(
        errorCode: ErrorCode,
        detail: String? = null,
        fieldErrors: List<FieldErrorResponse>? = null,
        cause: Throwable? = null,
    ): ResponseEntity<ErrorResponse> {
        val status = errorCode.status
        if (status.is5xxServerError) {
            log.error(">>> [Http] 처리 실패 code={} status={} detail={}", errorCode.code, status.value(), detail, cause)
        } else {
            log.warn(
                ">>> [Http] 처리 실패 code={} status={} detail={} fields={}",
                errorCode.code,
                status.value(),
                detail,
                fieldErrors?.map { it.field },
            )
        }
        val body =
            ErrorResponse.of(
                errorCode,
                detail = detail.takeIf { status.is4xxClientError },
                fieldErrors = fieldErrors,
            )
        return ResponseEntity.status(status).body(body)
    }

    private fun typeMismatchReason(type: Class<*>?): String =
        when {
            type == null -> "값의 형식이 올바르지 않습니다."
            type.isEnum -> "허용되지 않는 값입니다. 허용값: ${type.enumConstants.joinToString(", ")}"
            else -> "${type.simpleName} 형식이 아닙니다."
        }
}
