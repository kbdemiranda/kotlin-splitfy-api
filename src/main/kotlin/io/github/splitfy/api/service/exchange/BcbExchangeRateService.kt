package io.github.splitfy.api.service.exchange

import io.github.splitfy.api.domain.enums.Currency
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.concurrent.ConcurrentHashMap

@Service
class BcbExchangeRateService(
    @Value("\${splitfy.exchange.bcb.base-url:https://olinda.bcb.gov.br}") private val baseUrl: String,
    @Value("\${splitfy.exchange.bcb.lookback-days:10}") private val lookbackDays: Long,
    @Value("\${splitfy.exchange.fallback.base-url:https://api.frankfurter.app}") private val fallbackBaseUrl: String,
    @Value("\${splitfy.exchange.timeout.connect-ms:2000}") private val connectTimeoutMs: Long,
    @Value("\${splitfy.exchange.timeout.read-ms:3000}") private val readTimeoutMs: Long,
    @Value("\${splitfy.exchange.retry.max-attempts:3}") private val maxAttempts: Int,
    @Value("\${splitfy.exchange.retry.backoff-ms:300}") private val backoffMs: Long,
    @Value("\${splitfy.exchange.cache.ttl-seconds:300}") private val cacheTtlSeconds: Long,
    @Value("\${splitfy.exchange.cache.max-stale-seconds:21600}") private val maxStaleSeconds: Long,
) : ExchangeRateService {

    private val logger = LoggerFactory.getLogger(BcbExchangeRateService::class.java)
    private val restClient = buildRestClient(baseUrl)
    private val fallbackRestClient = buildRestClient(fallbackBaseUrl)
    private val cache = ConcurrentHashMap<Currency, CachedQuote>()
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

        val now = LocalDateTime.now()
        val cached = cache[currency]
        if (cached != null && !isCacheExpired(cached, now, cacheTtlSeconds)) {
            return cached.quote
        }

        val freshQuote = fetchFreshQuote(currency)
        if (freshQuote != null) {
            cache[currency] = CachedQuote(quote = freshQuote, fetchedAt = now)
            return freshQuote
        }

        if (cached != null && !isCacheExpired(cached, now, maxStaleSeconds)) {
            logger.warn(
                "Using stale cached exchange rate for {} quoted at {} due to external provider failure",
                currency,
                cached.quote.quotedAt
            )
            return cached.quote
        }

        return null
    }

    private fun fetchFreshQuote(currency: Currency): ExchangeRateQuote? {
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
            "&\$orderby=dataHoraCotacao desc" +
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
            val quote = executeWithRetry("BCB[$currency][$window days]") retryBlock@{
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
                    ?: return@retryBlock null

                ExchangeRateQuote(
                    currency = currency,
                    rateToBrl = latestQuote.first.cotacaoVenda,
                    quotedAt = latestQuote.second
                )
            }

            if (quote != null) return quote
        }

        return null
    }

    private fun fetchFromFallback(currency: Currency): ExchangeRateQuote? {
        return executeWithRetry("Fallback[$currency]") retryBlock@{
            val response = fallbackRestClient.get()
                .uri("/latest?from=${currency.name}&to=BRL")
                .retrieve()
                .body(FallbackRateResponse::class.java)
                ?: return@retryBlock null

            val date = response.date?.let { LocalDate.parse(it) } ?: return@retryBlock null
            val rate = response.rates["BRL"] ?: return@retryBlock null

            ExchangeRateQuote(
                currency = currency,
                rateToBrl = rate,
                quotedAt = LocalDateTime.of(date, LocalTime.NOON)
            )
        }
    }

    private fun buildRestClient(baseUrl: String): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
            setReadTimeout(Duration.ofMillis(readTimeoutMs))
        }
        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build()
    }

    private fun <T> executeWithRetry(operation: String, block: () -> T?): T? {
        val attempts = maxAttempts.coerceAtLeast(1)
        for (attempt in 1..attempts) {
            try {
                return block()
            } catch (ex: Exception) {
                val isLastAttempt = attempt == attempts
                logger.warn(
                    "External call {} failed (attempt {}/{}): {}",
                    operation,
                    attempt,
                    attempts,
                    ex.message
                )
                if (isLastAttempt) {
                    return null
                }
                sleepBackoff(attempt)
            }
        }
        return null
    }

    private fun sleepBackoff(attempt: Int) {
        val delay = backoffMs.coerceAtLeast(0) * attempt
        if (delay <= 0L) return
        try {
            Thread.sleep(delay)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun isCacheExpired(cached: CachedQuote, now: LocalDateTime, maxAgeSeconds: Long): Boolean {
        val age = Duration.between(cached.fetchedAt, now).seconds
        return age > maxAgeSeconds
    }
}

private data class CachedQuote(
    val quote: ExchangeRateQuote,
    val fetchedAt: LocalDateTime,
)

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
