package com.depromeet.team2.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
)
