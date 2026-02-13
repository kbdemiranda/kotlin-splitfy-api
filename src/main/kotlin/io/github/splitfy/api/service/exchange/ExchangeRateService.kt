package io.github.splitfy.api.service.exchange

import io.github.splitfy.api.domain.enums.Currency
import java.math.BigDecimal
import java.time.LocalDateTime

interface ExchangeRateService {
    fun getLatestBrlRate(currency: Currency): ExchangeRateQuote?
}

data class ExchangeRateQuote(
    val currency: Currency,
    val rateToBrl: BigDecimal,
    val quotedAt: LocalDateTime
)
