package com.depromeet.team2.global.payload

import org.springframework.http.HttpStatus

enum class SuccessCode(
    val status: HttpStatus,
    val message: String,
) {
    OK(HttpStatus.OK, "요청에 성공했습니다."),
    CREATED(HttpStatus.CREATED, "생성에 성공했습니다."),
    ;

    val code: String get() = name
}
