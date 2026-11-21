package com.depromeet.team2.global.exception

class DynamicException(
    val errorCode: ErrorCode,
    val detail: String,
    cause: Throwable? = null,
) : RuntimeException(detail, cause)
