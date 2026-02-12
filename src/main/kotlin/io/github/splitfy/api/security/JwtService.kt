package io.github.splitfy.api.security

import io.github.splitfy.api.domain.entity.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import org.springframework.security.core.userdetails.UserDetails
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date

@Service
class JwtService(private val jwtProperties: JwtProperties) {

    private val key = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray(StandardCharsets.UTF_8))

    fun generateToken(user: User): String {
        val now = Instant.now()
        val expiration = now.plusMillis(jwtProperties.expirationMs)

        return Jwts.builder()
            .subject(user.email)
            .claim("role", user.profile?.name?.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(key)
            .compact()
    }

    fun extractEmail(token: String): String = extractAllClaims(token).subject

    fun extractExpiration(token: String): Instant = extractAllClaims(token).expiration.toInstant()

    fun isTokenValid(token: String, userDetails: UserDetails): Boolean {
        val email = extractEmail(token)
        return email.equals(userDetails.username, ignoreCase = true) && extractExpiration(token).isAfter(Instant.now())
    }

    private fun extractAllClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
