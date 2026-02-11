package io.github.splitfy.api.application.dto

import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.domain.enums.ServiceType
import java.math.BigDecimal
import java.time.MonthDay
import java.time.LocalDateTime
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Dados da plataforma de assinatura")
data class PlatformDto(
    @Schema(description = "Identificador da plataforma", example = "1")
    val id: Long? = null,

    @Schema(description = "Nome da plataforma", example = "Netflix")
    val name: String,

    @Schema(description = "Preço da assinatura", example = "19.90")
    val price: BigDecimal,

    @Schema(description = "URL da plataforma", example = "https://www.netflix.com")
    val url: String? = null,

    @Schema(description = "Tipo de serviço")
    val serviceType: ServiceType,

    @Schema(description = "Total de vagas (slots)", example = "4")
    val totalSlots: Int,

    @Schema(description = "Vagas disponíveis", example = "2")
    val availableSlots: Int,

    @Schema(description = "Data de criação")
    val createdAt: LocalDateTime? = null,

    @Schema(description = "Data de atualização")
    val updatedAt: LocalDateTime? = null,

    @Schema(description = "Data de deleção")
    val deletedAt: LocalDateTime? = null,

    @Schema(description = "Ciclo de cobrança")
    val billingCycle: BillingCycle = BillingCycle.MONTHLY,

    @Schema(description = "Dia do ciclo de cobrança (se aplicável)")
    val billingDay: MonthDay? = null
)
