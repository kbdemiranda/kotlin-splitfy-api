package io.github.splitfy.api.integration

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.github.splitfy.api.domain.enums.Currency
import io.github.splitfy.api.web.platform.dto.PlatformRequest
import io.github.splitfy.api.web.subscriber.dto.SubscriberRequest
import io.github.splitfy.api.web.subscriber.dto.PlatformAssociationRequest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.math.BigDecimal

@SpringBootTest
@AutoConfigureMockMvc
class SubscriberIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    // create a local ObjectMapper configured to match application's enum serialization (toString)
    val objectMapper: ObjectMapper = ObjectMapper()
        .findAndRegisterModules()
        .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    @Test
    fun `associate multiple platforms flow via MockMvc`() {
        // create platform 1
        val p1Req = PlatformRequest(
            name = "Plat A",
            price = BigDecimal("10.00"),
            currency = Currency.USD,
            url = "http://a",
            serviceType = io.github.splitfy.api.domain.enums.ServiceType.STREAMING_VIDEO,
            totalSlots = 5,
            availableSlots = 3,
            billingCycle = io.github.splitfy.api.domain.enums.BillingCycle.MONTHLY,
            billingDay = null
        )
        val p1Json = objectMapper.writeValueAsString(p1Req)
        val p1Result = mockMvc.perform(post("/platforms").contentType(MediaType.APPLICATION_JSON).content(p1Json))
            .andExpect(status().isCreated)
            .andReturn()
        val createdP1Node: JsonNode = objectMapper.readTree(p1Result.response.contentAsString)
        val createdP1Id = createdP1Node.get("id").asLong()
        val createdP1AvailableSlots = createdP1Node.get("availableSlots").asInt()
        val createdP1Currency = createdP1Node.get("currency").asText()

        // create platform 2
        val p2Req = PlatformRequest(
            name = "Plat B",
            price = BigDecimal("5.00"),
            url = "http://b",
            serviceType = io.github.splitfy.api.domain.enums.ServiceType.STREAMING_VIDEO,
            totalSlots = 4,
            availableSlots = 2,
            billingCycle = io.github.splitfy.api.domain.enums.BillingCycle.MONTHLY,
            billingDay = null
        )
        val p2Json = objectMapper.writeValueAsString(p2Req)
        val p2Result = mockMvc.perform(post("/platforms").contentType(MediaType.APPLICATION_JSON).content(p2Json))
            .andExpect(status().isCreated)
            .andReturn()
        val createdP2Node: JsonNode = objectMapper.readTree(p2Result.response.contentAsString)
        val createdP2Id = createdP2Node.get("id").asLong()
        val createdP2AvailableSlots = createdP2Node.get("availableSlots").asInt()

        // create subscriber
        val sReq = SubscriberRequest(name = "Test User", email = "t@example.com")
        val sJson = objectMapper.writeValueAsString(sReq)
        val sResult = mockMvc.perform(post("/subscribers").contentType(MediaType.APPLICATION_JSON).content(sJson))
            .andExpect(status().isOk)
            .andReturn()
        val createdSNode: JsonNode = objectMapper.readTree(sResult.response.contentAsString)
        val createdSId = createdSNode.get("id").asLong()

        // associate both platforms
        val assocReq = listOf(PlatformAssociationRequest(platformIds = listOf(createdP1Id, createdP2Id)))
        val assocJson = objectMapper.writeValueAsString(assocReq)
        mockMvc.perform(post("/subscribers/${createdSId}/associate").contentType(MediaType.APPLICATION_JSON).content(assocJson))
            .andExpect(status().isOk)

        // check platforms updated
        mockMvc.perform(get("/platforms/$createdP1Id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.availableSlots").value(createdP1AvailableSlots - 1))
            .andExpect(jsonPath("$.currency").value(createdP1Currency))

        mockMvc.perform(get("/platforms/$createdP2Id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.availableSlots").value(createdP2AvailableSlots - 1))

        // disassociate
        val disassocReqJson = objectMapper.writeValueAsString(listOf(PlatformAssociationRequest(platformIds = listOf(createdP1Id, createdP2Id))))
        mockMvc.perform(put("/subscribers/${createdSId}/disassociate").contentType(MediaType.APPLICATION_JSON).content(disassocReqJson))
            .andExpect(status().isOk)

        mockMvc.perform(get("/platforms/$createdP1Id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.availableSlots").value(createdP1AvailableSlots))

        mockMvc.perform(get("/platforms/$createdP2Id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.availableSlots").value(createdP2AvailableSlots))
    }

    @Test
    fun `associate with empty request does nothing via MockMvc`() {
        val sReq = SubscriberRequest(name = "Empty Test", email = "e@example.com")
        val sJson = objectMapper.writeValueAsString(sReq)
        val sResult = mockMvc.perform(post("/subscribers").contentType(MediaType.APPLICATION_JSON).content(sJson))
            .andExpect(status().isOk)
            .andReturn()
        val createdSNode: JsonNode = objectMapper.readTree(sResult.response.contentAsString)
        val createdSId = createdSNode.get("id").asLong()

        val emptyJson = objectMapper.writeValueAsString(emptyList<PlatformAssociationRequest>())
        mockMvc.perform(post("/subscribers/${createdSId}/associate").contentType(MediaType.APPLICATION_JSON).content(emptyJson))
            .andExpect(status().isOk)

        mockMvc.perform(get("/subscribers/${createdSId}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(createdSNode.get("email").asText()))
    }
}
