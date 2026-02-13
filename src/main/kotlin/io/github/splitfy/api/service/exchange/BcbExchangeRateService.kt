package io.github.splitfy.api.service.exchange

import io.github.splitfy.api.domain.enums.Currency
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

@Service
class BcbExchangeRateService(
    @Value("\${splitfy.exchange.bcb.base-url:https://olinda.bcb.gov.br}") private val baseUrl: String,
    @Value("\${splitfy.exchange.bcb.lookback-days:10}") private val lookbackDays: Long,
    @Value("\${splitfy.exchange.fallback.base-url:https://api.frankfurter.app}") private val fallbackBaseUrl: String
) : ExchangeRateService {

    private val logger = LoggerFactory.getLogger(BcbExchangeRateService::class.java)
    private val restClient = RestClient.builder().baseUrl(baseUrl).build()
    private val fallbackRestClient = RestClient.builder().baseUrl(fallbackBaseUrl).build()
    private val queryDateFormatter = DateTimeFormatter.ofPattern("MM-dd-yyyy")
    private val quoteDateFormatter: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .optionalStart()
        .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
        .optionalEnd()
        .toFormatter()

    override fun getLatestBrlRate(currency: Currency): ExchangeRateQuote? {
        if (currency == Currency.BRL) {
            return ExchangeRateQuote(
                currency = Currency.BRL,
                rateToBrl = BigDecimal.ONE,
                quotedAt = LocalDateTime.now()
            )
        }

        val endDate = LocalDate.now()

        return fetchFromBcb(currency, endDate)
            ?: fetchFromFallback(currency)
    }

    private fun buildBcbUri(currency: Currency, startDate: LocalDate, endDate: LocalDate): String {
        val start = startDate.format(queryDateFormatter)
        val end = endDate.format(queryDateFormatter)
        return "/olinda/servico/PTAX/versao/v1/odata/" +
            "CotacaoMoedaPeriodo(moeda=@moeda,dataInicial=@dataInicial,dataFinalCotacao=@dataFinalCotacao)" +
            "?@moeda='${currency.name}'" +
            "&@dataInicial='$start'" +
            "&@dataFinalCotacao='$end'" +
            "&\$top=1" +
            "&\$orderby=dataHoraCotacao%20desc" +
            "&\$format=json"
    }

    private fun parseQuotedAt(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalDateTime.parse(raw, quoteDateFormatter) }.getOrNull()
    }

    private fun fetchFromBcb(currency: Currency, endDate: LocalDate): ExchangeRateQuote? {
        val windows = listOf(lookbackDays, 30L, 90L).distinct()
        for (window in windows) {
            val startDate = endDate.minusDays(window)
            val quote = runCatching {
                val response = restClient.get()
                    .uri(buildBcbUri(currency, startDate, endDate))
                    .retrieve()
                    .body(BcbCotacaoResponse::class.java)

                val latestQuote = response?.value
                    ?.mapNotNull { raw ->
                        val quotedAt = parseQuotedAt(raw.dataHoraCotacao) ?: return@mapNotNull null
                        Pair(raw, quotedAt)
                    }
                    ?.maxByOrNull { it.second }
                    ?: return@runCatching null

                ExchangeRateQuote(
                    currency = currency,
                    rateToBrl = latestQuote.first.cotacaoVenda,
                    quotedAt = latestQuote.second
                )
            }.onFailure { ex ->
                logger.warn(
                    "Failed to fetch BCB exchange rate for {} (lookback {} days): {}",
                    currency, window, ex.message
                )
            }.getOrNull()

            if (quote != null) return quote
        }

        return null
    }

    private fun fetchFromFallback(currency: Currency): ExchangeRateQuote? {
        return runCatching {
            val response = fallbackRestClient.get()
                .uri("/latest?from=${currency.name}&to=BRL")
                .retrieve()
                .body(FallbackRateResponse::class.java)
                ?: return null

            val date = response.date?.let { LocalDate.parse(it) } ?: return null
            val rate = response.rates["BRL"] ?: return null

            ExchangeRateQuote(
                currency = currency,
                rateToBrl = rate,
                quotedAt = LocalDateTime.of(date, LocalTime.NOON)
            )
        }.onFailure { ex ->
            logger.warn("Failed to fetch fallback exchange rate for {}: {}", currency, ex.message)
        }.getOrNull()
    }
}

data class BcbCotacaoResponse(
    val value: List<BcbCotacaoItem> = emptyList()
)

data class BcbCotacaoItem(
    val cotacaoVenda: BigDecimal,
    val dataHoraCotacao: String?
)

data class FallbackRateResponse(
    val date: String? = null,
    val rates: Map<String, BigDecimal> = emptyMap()
)
