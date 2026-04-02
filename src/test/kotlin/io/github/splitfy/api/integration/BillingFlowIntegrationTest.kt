package io.github.splitfy.api.integration

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.github.splitfy.api.domain.enums.Currency as PlatformCurrency
import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.domain.enums.ServiceType
import io.github.splitfy.api.service.exchange.ExchangeRateQuote
import io.github.splitfy.api.service.exchange.ExchangeRateService
import io.github.splitfy.api.web.platform.dto.PlatformRequest
import io.github.splitfy.api.web.subscriber.dto.SubscriberRequest
import io.github.splitfy.api.web.subscriber.dto.PlatformAssociationRequest
import io.github.splitfy.api.web.paymnets.dto.PaymentConfirmationPlatformsRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.YearMonth

@SpringBootTest
@AutoConfigureMockMvc
class BillingFlowIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var exchangeRateService: ExchangeRateService

    val objectMapper: ObjectMapper = ObjectMapper()
        .findAndRegisterModules()
        .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    @Test
    fun `full billing and payment flow integration`() {
        val token = authenticateAsAdmin()
        val refMonth = YearMonth.now()
        val nextMonth = refMonth.plusMonths(1)

        // 1. Create USD platform (Notion) with 2 slots to test conflict and out of slots
        val notionReq = PlatformRequest(
            name = "Notion USD",
            price = BigDecimal("10.00"),
            currency = io.github.splitfy.api.domain.enums.Currency.USD,
            url = "http://notion.so",
            serviceType = ServiceType.SOFTWARE,
            totalSlots = 2,
            availableSlots = 2,
            billingCycle = BillingCycle.MONTHLY,
            billingDay = null
        )
        val notionResult = mockMvc.perform(
            post("/platforms")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(notionReq))
        ).andExpect(status().isCreated).andReturn()
        val notionId = objectMapper.readTree(notionResult.response.contentAsString).get("id").asLong()

        // 2. Create BRL platform (Netflix)
        val netflixReq = PlatformRequest(
            name = "Netflix BRL",
            price = BigDecimal("50.00"),
            currency = io.github.splitfy.api.domain.enums.Currency.BRL,
            url = "http://netflix.com",
            serviceType = ServiceType.STREAMING_VIDEO,
            totalSlots = 4,
            availableSlots = 4,
            billingCycle = BillingCycle.MONTHLY,
            billingDay = null
        )
        val netflixResult = mockMvc.perform(
            post("/platforms")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(netflixReq))
        ).andExpect(status().isCreated).andReturn()
        val netflixId = objectMapper.readTree(netflixResult.response.contentAsString).get("id").asLong()

        // 3. Create responsible subscriber
        val respReq = SubscriberRequest(name = "Responsible User", email = "resp@example.com")
        val respResult = mockMvc.perform(
            post("/subscribers")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(respReq))
        ).andExpect(status().isOk).andReturn()
        val respId = objectMapper.readTree(respResult.response.contentAsString).get("id").asLong()

        // 4. Create dependent subscriber
        val depReq = SubscriberRequest(name = "Dependent User", email = "dep@example.com", financialResponsibleSubscriberId = respId)
        val depResult = mockMvc.perform(
            post("/subscribers")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(depReq))
        ).andExpect(status().isOk).andReturn()
        val depId = objectMapper.readTree(depResult.response.contentAsString).get("id").asLong()

        // 5. Associate platforms
        mockMvc.perform(
            post("/subscribers/$respId/associate")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(listOf(PlatformAssociationRequest(platformIds = listOf(notionId)))))
        ).andExpect(status().isOk)

        // Test conflict: associate again
        mockMvc.perform(
            post("/subscribers/$respId/associate")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(listOf(PlatformAssociationRequest(platformIds = listOf(notionId)))))
        ).andExpect(status().isConflict)

        // Test out of slots
        // Associate another user to fill the slots
        val extraUserReq = SubscriberRequest(name = "Extra User", email = "extra@example.com")
        val extraUserResult = mockMvc.perform(
            post("/subscribers")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(extraUserReq))
        ).andExpect(status().isOk).andReturn()
        val extraId = objectMapper.readTree(extraUserResult.response.contentAsString).get("id").asLong()

        mockMvc.perform(
            post("/subscribers/$extraId/associate")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(listOf(PlatformAssociationRequest(platformIds = listOf(notionId)))))
        ).andExpect(status().isOk)

        // Now slots should be 0, so next attempt should be BadRequest
        mockMvc.perform(
            post("/subscribers/$depId/associate")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(listOf(PlatformAssociationRequest(platformIds = listOf(notionId)))))
        ).andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.message").value("Plataforma sem vagas disponíveis"))

        mockMvc.perform(
            post("/subscribers/$depId/associate")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(listOf(PlatformAssociationRequest(platformIds = listOf(netflixId)))))
        ).andExpect(status().isOk)

        // Mock exchange rate
        whenever(exchangeRateService.getLatestBrlRate(io.github.splitfy.api.domain.enums.Currency.USD)).thenReturn(
            ExchangeRateQuote(io.github.splitfy.api.domain.enums.Currency.USD, BigDecimal("5.00"), LocalDateTime.now())
        )

        // 6. Check billing for current month
        val billingResult = mockMvc.perform(
            get("/billing/$respId?referenceMonth=$refMonth")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk).andReturn()
        val billingNode = objectMapper.readTree(billingResult.response.contentAsString)
        
        // Notion (10 USD * 5.00 = 50 BRL) / 2 participants = 25 BRL + Netflix (50 BRL) = 75 BRL
        assertTrue(BigDecimal("75.00").compareTo(BigDecimal(billingNode.get("totalMonthlyDue").asText())) == 0)
        assertEquals(2, billingNode.get("items").size())

        // 7. Admin confirms payment for Notion USD only
        val confirmReq = PaymentConfirmationPlatformsRequest(
            referenceMonth = refMonth.toString(),
            platformIds = listOf(notionId)
        )
        mockMvc.perform(
            post("/paymnets/subscribers/$respId")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(confirmReq))
        ).andExpect(status().isOk)

        // 8. Check billing again
        val billingAfterResult = mockMvc.perform(
            get("/billing/$respId?referenceMonth=$refMonth")
                .header("Authorization", "Bearer $token")
        ).andExpect(status().isOk).andReturn()
        val billingAfterNode = objectMapper.readTree(billingAfterResult.response.contentAsString)

        // Total should still be 75 but status of Notion should be PAID
        assertTrue(BigDecimal("75.00").compareTo(BigDecimal(billingAfterNode.get("totalMonthlyDue").asText())) == 0)
        val notionItem = billingAfterNode.get("items").elements().asSequence().first { it.get("serviceId").asLong() == notionId }
        assertEquals("PAID", notionItem.get("paymentStatus").asText())
        val netflixItem = billingAfterNode.get("items").elements().asSequence().first { it.get("serviceId").asLong() == netflixId }
        assertEquals("UNPAID", netflixItem.get("paymentStatus").asText())
    }

    private fun authenticateAsAdmin(): String {
        val loginJson = """
            {
              "email": "admin@test.local",
              "password": "Admin@Test123"
            }
        """.trimIndent()

        val loginResult = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson)
        ).andExpect(status().isOk).andReturn()

        return objectMapper.readTree(loginResult.response.contentAsString).get("token").asText()
    }
}
