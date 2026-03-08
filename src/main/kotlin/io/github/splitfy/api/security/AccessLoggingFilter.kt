package io.github.splitfy.api.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class AccessLoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(AccessLoggingFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val startedAt = System.currentTimeMillis()
        val requestId = request.getHeader(REQUEST_ID_HEADER)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

        MDC.put(REQUEST_ID_MDC_KEY, requestId)
        response.setHeader(REQUEST_ID_HEADER, requestId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            val authentication = SecurityContextHolder.getContext().authentication
            val isAnonymous = authentication == null ||
                !authentication.isAuthenticated ||
                authentication is AnonymousAuthenticationToken

            val email = if (isAnonymous) {
                "anonymous"
            } else {
                authentication.name
            }

            val profile = if (isAnonymous) {
                "ANONYMOUS"
            } else {
                authentication.authorities
                    .firstOrNull()
                    ?.authority
                    ?.removePrefix("ROLE_")
                    ?: "UNKNOWN"
            }

            val query = request.queryString?.let { "?$it" } ?: ""
            val resource = "${request.requestURI}$query"
            val durationMs = System.currentTimeMillis() - startedAt

            log.info(
                "event=http.request.completed requestId={} method={} resource={} status={} durationMs={} actorEmail={} actorProfile={}",
                requestId,
                request.method,
                resource,
                response.status,
                durationMs,
                email,
                profile,
            )

            MDC.remove(REQUEST_ID_MDC_KEY)
        }
    }

    companion object {
        private const val REQUEST_ID_HEADER = "X-Request-Id"
        private const val REQUEST_ID_MDC_KEY = "requestId"
    }
}
