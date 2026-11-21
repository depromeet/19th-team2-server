package com.depromeet.team2.global.payload

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FieldErrorResponse(
    val field: String,
    val reason: String?,
    val rejectedValue: Any? = null,
)
