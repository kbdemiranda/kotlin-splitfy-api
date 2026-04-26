package io.github.splitfy.api.web

import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.domain.enums.Currency
import io.github.splitfy.api.domain.enums.PaymentConfirmationStatus
import io.github.splitfy.api.domain.enums.ProfileName
import io.github.splitfy.api.domain.enums.ServiceType
import io.github.splitfy.api.security.CustomUserDetailsService
import io.github.splitfy.api.security.JwtService
import io.github.splitfy.api.security.TokenBlacklistService
import io.github.splitfy.api.service.auth.AuthRateLimitService
import io.github.splitfy.api.service.auth.AuthService
import io.github.splitfy.api.service.billing.BillingService
import io.github.splitfy.api.service.dashboard.DashboardService
import io.github.splitfy.api.service.email.EmailScheduleSettingsService
import io.github.splitfy.api.service.payment.PaymentConfirmationService
import io.github.splitfy.api.service.platform.PlatformService
import io.github.splitfy.api.service.profile.ProfileService
import io.github.splitfy.api.service.subscriber.SubscriberService
import io.github.splitfy.api.service.user.UserService
import io.github.splitfy.api.web.auth.AuthController
import io.github.splitfy.api.web.auth.dto.LoginResponse
import io.github.splitfy.api.web.auth.dto.SimpleMessageResponse
import io.github.splitfy.api.web.billing.BillingController
import io.github.splitfy.api.web.billing.dto.BillingResponse
import io.github.splitfy.api.web.dashboard.DashboardController
import io.github.splitfy.api.web.dashboard.dto.DashboardKpiResponse
import io.github.splitfy.api.web.emailschedule.EmailScheduleController
import io.github.splitfy.api.web.emailschedule.dto.EmailScheduleOccurrenceResponse
import io.github.splitfy.api.web.emailschedule.dto.EmailScheduleSettingsResponse
import io.github.splitfy.api.web.platform.PlatformController
import io.github.splitfy.api.web.platform.dto.PlatformResponse
import io.github.splitfy.api.web.paymnets.PaymentConfirmationController
import io.github.splitfy.api.web.paymnets.dto.PaymentConfirmationResponse
import io.github.splitfy.api.web.paymnets.dto.PendingPaymentApprovalResponse
import io.github.splitfy.api.web.paymnets.dto.PendingPaymentPlatform
import io.github.splitfy.api.web.paymnets.dto.PendingPaymentSubscriber
import io.github.splitfy.api.web.profile.ProfileController
import io.github.splitfy.api.web.profile.dto.ProfileResponse
import io.github.splitfy.api.web.subscriber.SubscriberController
import io.github.splitfy.api.web.subscriber.dto.SubscriberResponse
import io.github.splitfy.api.web.user.UserController
import io.github.splitfy.api.web.user.dto.ProfileSummaryResponse
import io.github.splitfy.api.web.user.dto.UserResponse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID

@WebMvcTest(
    controllers = [
        HelloController::class,
        AuthController::class,
        DashboardController::class,
        BillingController::class,
        EmailScheduleController::class,
        PaymentConfirmationController::class,
        PlatformController::class,
        ProfileController::class,
        SubscriberController::class,
        UserController::class,
    ]
)
@AutoConfigureMockMvc(addFilters = false)
class ControllerWebMvcTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var authService: AuthService

    @MockitoBean
    lateinit var authRateLimitService: AuthRateLimitService

    @MockitoBean
    lateinit var dashboardService: DashboardService

    @MockitoBean
    lateinit var billingService: BillingService

    @MockitoBean
    lateinit var subscriberService: SubscriberService

    @MockitoBean
    lateinit var emailScheduleSettingsService: EmailScheduleSettingsService

    @MockitoBean
    lateinit var paymentConfirmationService: PaymentConfirmationService

    @MockitoBean
    lateinit var platformService: PlatformService

    @MockitoBean
    lateinit var profileService: ProfileService

    @MockitoBean
    lateinit var userService: UserService

    @MockitoBean
    lateinit var jwtService: JwtService

    @MockitoBean
    lateinit var userDetailsService: CustomUserDetailsService

    @MockitoBean
    lateinit var tokenBlacklistService: TokenBlacklistService

    @Test
    fun `hello endpoint returns greeting`() {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().string("Hello, World, Welcome to Splitfy!"))
    }

    @Test
    fun `auth login checks rate limit and delegates to auth service`() {
        val userId = UUID.randomUUID()
        whenever(authService.login(any())).thenReturn(
            LoginResponse(
                token = "jwt",
                expiresInMs = 3600,
                userId = userId,
                email = "user@example.com",
                profile = ProfileName.VIEWER,
            )
        )

        mockMvc.perform(
            post("/auth/login")
                .with { request ->
                    request.remoteAddr = "203.0.113.10"
                    request
                }
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"user@example.com","password":"password123"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value("jwt"))
            .andExpect(jsonPath("$.email").value("user@example.com"))

        verify(authRateLimitService).checkLoginAllowed("203.0.113.10", "user@example.com")
    }

    @Test
    fun `auth forgot and reset password delegate with rate limits`() {
        val resetToken = "S7A6B8NINzV0vDgCk3iybB24wL-r2I8M7VJt1o8C2aM"
        whenever(authService.forgotPassword(any(), eq("127.0.0.1")))
            .thenReturn(SimpleMessageResponse("Password reset instructions sent"))
        whenever(authService.tokenFingerprint(resetToken)).thenReturn("fingerprint")
        whenever(authService.resetPassword(any())).thenReturn(SimpleMessageResponse("Password updated"))

        mockMvc.perform(
            post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"user@example.com"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Password reset instructions sent"))

        mockMvc.perform(
            post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$resetToken","newPassword":"New-password123!"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Password updated"))

        verify(authRateLimitService).checkForgotPasswordAllowed("127.0.0.1", "user@example.com")
        verify(authRateLimitService).checkResetPasswordAllowed("127.0.0.1", "fingerprint")
    }

    @Test
    fun `auth logout accepts bearer token and rejects missing header`() {
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer logout-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Logout successful"))

        verify(authService).logout("logout-token")

        mockMvc.perform(post("/auth/logout"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
    }

    @Test
    fun `dashboard parses reference month and rejects invalid values`() {
        whenever(dashboardService.getKpis(YearMonth.of(2026, 4))).thenReturn(sampleDashboard())

        mockMvc.perform(get("/dashboard/kpis").param("referenceMonth", "2026-04"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currency").value("BRL"))

        mockMvc.perform(get("/dashboard/kpis").param("referenceMonth", "2026/04"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
    }

    @Test
    fun `billing endpoint passes parsed reference month and sends email summary`() {
        whenever(billingService.getBillingForSubscriber(1L, YearMonth.of(2026, 4)))
            .thenReturn(sampleBilling())

        mockMvc.perform(get("/billing/1").param("referenceMonth", "2026-04"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(1))

        mockMvc.perform(
            post("/billing/email-summary")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"subscriberIds":[1],"emails":["billing@example.com"],"referenceMonth":"2026-04"}""")
        )
            .andExpect(status().isOk)

        verify(subscriberService).sendBillingSummaryToEmails(any())
    }

    @Test
    fun `email schedule endpoints get and update schedule`() {
        val response = sampleSchedule()
        whenever(emailScheduleSettingsService.getKpiSummarySchedule()).thenReturn(response)
        whenever(emailScheduleSettingsService.updateKpiSummarySchedule(any())).thenReturn(response)

        mockMvc.perform(get("/email-schedules/kpi-summary"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.scheduleKey").value("KPI_SUMMARY_EMAIL"))

        mockMvc.perform(
            put("/email-schedules/kpi-summary")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":true,"timezone":"America/Sao_Paulo","occurrences":[{"dayOfWeek":1,"executionTime":"10:00"}]}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
    }

    @Test
    fun `payment confirmation endpoints create and list confirmations`() {
        whenever(paymentConfirmationService.createAdminConfirmations(eq(1L), any()))
            .thenReturn(listOf(samplePaymentConfirmation()))
        whenever(paymentConfirmationService.listPendingConfirmations("2026-04"))
            .thenReturn(listOf(samplePendingPayment()))

        mockMvc.perform(
            post("/paymnets/subscribers/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"referenceMonth":"2026-04","platformIds":[2]}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].subscriberId").value(1))

        mockMvc.perform(get("/paymnets/pending").param("referenceMonth", "2026-04"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].confirmationId").value(10))
    }

    @Test
    fun `platform endpoints create list get update and delete`() {
        val response = samplePlatform()
        whenever(platformService.create(any())).thenReturn(response)
        whenever(platformService.findAll(any(), eq("Netflix"))).thenReturn(PageImpl(listOf(response)))
        whenever(platformService.findById(2L)).thenReturn(response)
        whenever(platformService.update(eq(2L), any())).thenReturn(response)

        val request = """{"name":"Netflix","price":59.90,"currency":"BRL","serviceType":"Video Streaming","totalSlots":4,"availableSlots":2}"""
        mockMvc.perform(post("/platforms").contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Netflix"))

        mockMvc.perform(get("/platforms").param("name", "Netflix"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(2))

        mockMvc.perform(get("/platforms/2"))
            .andExpect(status().isOk)

        mockMvc.perform(put("/platforms/2").contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isOk)

        mockMvc.perform(delete("/platforms/2"))
            .andExpect(status().isNoContent)

        verify(platformService).delete(2L)
    }

    @Test
    fun `profile endpoints create list get update and delete`() {
        val id = UUID.randomUUID()
        val response = ProfileResponse(id, ProfileName.ADMIN, LocalDateTime.now(), LocalDateTime.now())
        whenever(profileService.create(any())).thenReturn(response)
        whenever(profileService.list(any())).thenReturn(PageImpl(listOf(response)))
        whenever(profileService.getById(id)).thenReturn(response)
        whenever(profileService.update(eq(id), any())).thenReturn(response)

        mockMvc.perform(post("/profiles").contentType(MediaType.APPLICATION_JSON).content("""{"name":"ADMIN"}"""))
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/profiles/$id"))

        mockMvc.perform(get("/profiles"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].name").value("ADMIN"))

        mockMvc.perform(get("/profiles/$id"))
            .andExpect(status().isOk)

        mockMvc.perform(put("/profiles/$id").contentType(MediaType.APPLICATION_JSON).content("""{"name":"ADMIN"}"""))
            .andExpect(status().isOk)

        mockMvc.perform(delete("/profiles/$id"))
            .andExpect(status().isNoContent)

        verify(profileService).delete(id)
    }

    @Test
    fun `subscriber endpoints delegate common workflows`() {
        val response = sampleSubscriber()
        whenever(subscriberService.create(any())).thenReturn(response)
        whenever(subscriberService.list(any(), eq("Ana"))).thenReturn(PageImpl(listOf(response)))
        whenever(subscriberService.get(1L)).thenReturn(response)
        whenever(subscriberService.getSubscriptions(1L)).thenReturn(emptyList())
        whenever(subscriberService.update(eq(1L), any())).thenReturn(response)

        mockMvc.perform(get("/subscribers").param("name", "Ana"))
            .andExpect(status().isOk)

        mockMvc.perform(post("/subscribers").contentType(MediaType.APPLICATION_JSON).content("""{"name":"Ana","email":"ana@example.com"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("ana@example.com"))

        mockMvc.perform(get("/subscribers/1"))
            .andExpect(status().isOk)

        mockMvc.perform(get("/subscribers/1/subscriptions"))
            .andExpect(status().isOk)

        mockMvc.perform(put("/subscribers/1").contentType(MediaType.APPLICATION_JSON).content("""{"name":"Ana","email":"ana@example.com"}"""))
            .andExpect(status().isOk)

        mockMvc.perform(delete("/subscribers/1"))
            .andExpect(status().isNoContent)

        mockMvc.perform(
            post("/subscribers/1/associate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""[{"platformIds":[2]}]""")
        )
            .andExpect(status().isOk)

        mockMvc.perform(
            put("/subscribers/1/disassociate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""[{"platformIds":[2]}]""")
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `user endpoints delegate common workflows`() {
        val id = UUID.randomUUID()
        val profileId = UUID.randomUUID()
        val response = UserResponse(
            id = id,
            name = "User",
            email = "user@example.com",
            profile = ProfileSummaryResponse(profileId, ProfileName.VIEWER),
            isEnabled = true,
            receivesDashboardEmail = false,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            deletedAt = null,
        )
        whenever(userService.create(any())).thenReturn(response)
        whenever(userService.list(any(), eq("User"))).thenReturn(PageImpl(listOf(response)))
        whenever(userService.getById(id)).thenReturn(response)
        whenever(userService.update(eq(id), any())).thenReturn(response)
        whenever(userService.updateDashboardEmailPreference(eq(id), any())).thenReturn(response)
        whenever(userService.associateProfile(id, profileId)).thenReturn(response)
        whenever(userService.disassociateProfile(id)).thenReturn(response)

        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("""{"name":"User","email":"user@example.com","password":"password123"}"""))
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/users/$id"))

        mockMvc.perform(get("/users").param("name", "User"))
            .andExpect(status().isOk)

        mockMvc.perform(get("/users/$id"))
            .andExpect(status().isOk)

        mockMvc.perform(put("/users/$id").contentType(MediaType.APPLICATION_JSON).content("""{"name":"User"}"""))
            .andExpect(status().isOk)

        mockMvc.perform(patch("/users/$id/dashboard-email-preference").contentType(MediaType.APPLICATION_JSON).content("""{"receivesDashboardEmail":true,"force":true}"""))
            .andExpect(status().isOk)

        mockMvc.perform(put("/users/$id/profile/$profileId"))
            .andExpect(status().isOk)

        mockMvc.perform(delete("/users/$id/profile"))
            .andExpect(status().isOk)

        mockMvc.perform(delete("/users/$id"))
            .andExpect(status().isNoContent)
    }

    private fun sampleDashboard(): DashboardKpiResponse {
        return DashboardKpiResponse(
            referenceMonth = YearMonth.of(2026, 4),
            currency = "BRL",
            totalDue = BigDecimal("100.00"),
            totalPaid = BigDecimal("80.00"),
            totalPending = BigDecimal("20.00"),
            totalUnpaid = BigDecimal.ZERO,
            delinquencyRate = BigDecimal("20.00"),
            pendingByPlatform = emptyList(),
            debtors = emptyList(),
        )
    }

    private fun sampleBilling(): BillingResponse {
        return BillingResponse(
            userId = 1L,
            name = "Ana",
            email = "ana@example.com",
            referenceMonth = YearMonth.of(2026, 4),
            items = emptyList(),
            totalMonthlyDue = BigDecimal("10.00"),
        )
    }

    private fun sampleSchedule(): EmailScheduleSettingsResponse {
        return EmailScheduleSettingsResponse(
            scheduleKey = "KPI_SUMMARY_EMAIL",
            enabled = true,
            timezone = "America/Sao_Paulo",
            occurrences = listOf(EmailScheduleOccurrenceResponse(1, LocalTime.of(10, 0))),
        )
    }

    private fun samplePaymentConfirmation(): PaymentConfirmationResponse {
        return PaymentConfirmationResponse(
            id = 10L,
            subscriberId = 1L,
            platformId = 2L,
            referenceMonth = YearMonth.of(2026, 4),
            status = PaymentConfirmationStatus.CONFIRMED,
            requestedByEmail = "admin@example.com",
            requestedAt = LocalDateTime.now(),
            validatedByEmail = "admin@example.com",
            validatedAt = LocalDateTime.now(),
        )
    }

    private fun samplePendingPayment(): PendingPaymentApprovalResponse {
        return PendingPaymentApprovalResponse(
            confirmationId = 10L,
            referenceMonth = YearMonth.of(2026, 4),
            status = PaymentConfirmationStatus.PENDING,
            requestedByEmail = "ana@example.com",
            requestedAt = LocalDateTime.now(),
            subscriber = PendingPaymentSubscriber(1L, "Ana", "ana@example.com"),
            platform = PendingPaymentPlatform(2L, "Netflix", ServiceType.STREAMING_VIDEO, Currency.BRL, BigDecimal("59.90")),
        )
    }

    private fun samplePlatform(): PlatformResponse {
        return PlatformResponse(
            id = 2L,
            name = "Netflix",
            price = BigDecimal("59.90"),
            currency = Currency.BRL,
            priceInBrl = BigDecimal("59.90"),
            exchangeRateToBrl = BigDecimal.ONE,
            exchangeRateDate = null,
            url = null,
            serviceType = ServiceType.STREAMING_VIDEO,
            totalSlots = 4,
            availableSlots = 2,
            createdAt = LocalDateTime.now(),
            billingCycle = BillingCycle.MONTHLY,
        )
    }

    private fun sampleSubscriber(): SubscriberResponse {
        return SubscriberResponse(
            id = 1L,
            name = "Ana",
            email = "ana@example.com",
            financialResponsibleSubscriberId = null,
            financialResponsibleSubscriberName = null,
            createdAt = LocalDateTime.now(),
        )
    }
}
