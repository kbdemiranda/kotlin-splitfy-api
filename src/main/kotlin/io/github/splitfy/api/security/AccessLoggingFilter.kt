package io.github.splitfy.api.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class AccessLoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(AccessLoggingFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        filterChain.doFilter(request, response)

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

        log.info(
            "Access: method={} resource={} status={} email={} profile={}",
            request.method,
            resource,
            response.status,
            email,
            profile,
        )
    }
}
