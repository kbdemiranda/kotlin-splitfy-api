package io.github.splitfy.api.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "splitfy.jwt")
data class JwtProperties(
    var secret: String,
    var expirationMs: Long,
)
