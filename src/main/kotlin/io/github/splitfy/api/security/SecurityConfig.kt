package io.github.splitfy.api.security

import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties::class)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val accessLoggingFilter: AccessLoggingFilter,
    private val userDetailsService: CustomUserDetailsService,
    @Value("\${spring.h2.console.enabled:false}") private val h2ConsoleEnabled: Boolean,
    @Value("\${splitfy.security.cors.allowed-origins:http://localhost:4242,http://127.0.0.1:4242}") private val allowedOrigins: String,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationProvider(): DaoAuthenticationProvider {
        val provider = DaoAuthenticationProvider(userDetailsService)
        provider.setPasswordEncoder(passwordEncoder())
        return provider
    }

    @Bean
    fun authenticationManager(configuration: AuthenticationConfiguration): AuthenticationManager {
        return configuration.authenticationManager
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuredOrigins = allowedOrigins
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val config = CorsConfiguration().apply {
            this.allowedOrigins = configuredOrigins
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type", "Accept", "X-Request-Id")
            exposedHeaders = listOf("Authorization", "X-Request-Id")
            allowCredentials = true
            maxAge = 3600
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authenticationProvider(authenticationProvider())
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, ex ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, ex.message)
                }
                it.accessDeniedHandler { _, response, ex ->
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, ex.message)
                }
            }
            .authorizeHttpRequests {
                val publicPaths = mutableListOf(
                    "/auth/login",
                    "/auth/forgot-password",
                    "/auth/reset-password",
                    "/users",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/webjars/**",
                    "/v3/api-docs",
                    "/v3/api-docs.yaml",
                    "/v3/api-docs/**",
                    "/actuator/health",
                    "/",
                )

                if (h2ConsoleEnabled) {
                    publicPaths += "/h2-console/**"
                }

                it.requestMatchers(*publicPaths.toTypedArray()).permitAll()
                it.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                it.requestMatchers(HttpMethod.POST, "/auth/logout").authenticated()

                it.requestMatchers(HttpMethod.POST, "/profiles/**").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.PUT, "/profiles/**").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.DELETE, "/profiles/**").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.GET, "/email-schedules/**").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.PUT, "/email-schedules/**").hasRole("ADMIN")

                it.requestMatchers(HttpMethod.POST, "/users").permitAll()
                it.requestMatchers(HttpMethod.POST, "/users/**").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.PATCH, "/users/**").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.PUT, "/users/**").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.DELETE, "/users/**").hasRole("ADMIN")

                it.requestMatchers(HttpMethod.POST, "/platforms/**").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.PUT, "/platforms/**").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.DELETE, "/platforms/**").hasRole("ADMIN")

                it.requestMatchers(HttpMethod.POST, "/subscribers").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.POST, "/paymnets/subscribers/*").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.GET, "/paymnets/pending").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.POST, "/subscribers/*/associate").hasAnyRole("ADMIN", "EDITOR")
                it.requestMatchers(HttpMethod.POST, "/billing/email-summary").hasAnyRole("ADMIN", "EDITOR")
                it.requestMatchers(HttpMethod.POST, "/subscribers/billing/email-summary").hasAnyRole("ADMIN", "EDITOR")
                it.requestMatchers(HttpMethod.PUT, "/subscribers/*").hasAnyRole("ADMIN", "EDITOR")
                it.requestMatchers(HttpMethod.PUT, "/subscribers/*/disassociate").hasAnyRole("ADMIN", "EDITOR")
                it.requestMatchers(HttpMethod.DELETE, "/subscribers/**").hasRole("ADMIN")

                it.requestMatchers(HttpMethod.GET, "/**").hasAnyRole("VIEWER", "EDITOR", "ADMIN")
                it.anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(accessLoggingFilter, JwtAuthenticationFilter::class.java)

        return http.build()
    }
}
