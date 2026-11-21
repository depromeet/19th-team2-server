package com.depromeet.team2.global.exception

abstract class CodeException(
    val errorCode: ErrorCode,
) : RuntimeException(errorCode.message, null, false, false)
