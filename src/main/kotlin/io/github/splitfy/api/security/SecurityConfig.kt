package io.github.splitfy.api.security

import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.EnableConfigurationProperties
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

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties::class)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val accessLoggingFilter: AccessLoggingFilter,
    private val userDetailsService: CustomUserDetailsService,
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
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
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
                it.requestMatchers(
                    "/auth/login",
                    "/users",
                    "/docs",
                    "/docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/webjars/**",
                    "/v3/api-docs",
                    "/v3/api-docs.yaml",
                    "/v3/api-docs/**",
                    "/actuator/health",
                    "/h2-console/**",
                    "/",
                ).permitAll()
                it.requestMatchers(HttpMethod.POST, "/auth/logout").authenticated()

                it.requestMatchers(HttpMethod.POST, "/profiles/**").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.PUT, "/profiles/**").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.DELETE, "/profiles/**").hasRole("ADMIN")

                it.requestMatchers(HttpMethod.POST, "/users").permitAll()
                it.requestMatchers(HttpMethod.POST, "/users/**").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.PUT, "/users/**").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.DELETE, "/users/**").hasRole("ADMIN")

                it.requestMatchers(HttpMethod.POST, "/platforms/**").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.PUT, "/platforms/**").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.DELETE, "/platforms/**").hasRole("ADMIN")

                it.requestMatchers(HttpMethod.POST, "/subscribers").hasRole("ADMIN")
                it.requestMatchers(HttpMethod.POST, "/subscribers/*/associate").hasAnyRole("ADMIN", "EDITOR")
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
