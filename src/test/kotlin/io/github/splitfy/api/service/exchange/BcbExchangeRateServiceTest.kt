package io.github.splitfy.api.service.exchange

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.splitfy.api.domain.enums.Currency
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicInteger

class BcbExchangeRateServiceTest {

    @Test
    fun `BRL returns identity quote without calling external providers`() {
        val service = service("http://127.0.0.1:1", "http://127.0.0.1:2")

        val quote = service.getLatestBrlRate(Currency.BRL)

        assertNotNull(quote)
        assertEquals(Currency.BRL, quote?.currency)
        assertEquals(BigDecimal.ONE, quote?.rateToBrl)
    }

    @Test
    fun `returns latest quote from BCB provider`() {
        stubServer { exchange ->
            assertTrue(exchange.requestURI.rawQuery.contains("@moeda='USD'"))
            json(
                """
                {
                  "value": [
                    {"cotacaoVenda": 5.10, "dataHoraCotacao": "2026-04-20 10:00:00.0"},
                    {"cotacaoVenda": 5.25, "dataHoraCotacao": "2026-04-20 13:04:38.0"}
                  ]
                }
                """
            )
        }.use { server ->
            val service = service(server.baseUrl, "http://127.0.0.1:1")

            val quote = service.getLatestBrlRate(Currency.USD)

            assertNotNull(quote)
            assertEquals(Currency.USD, quote?.currency)
            assertEquals(BigDecimal("5.25"), quote?.rateToBrl)
            assertEquals(LocalDate.of(2026, 4, 20), quote?.quotedAt?.toLocalDate())
        }
    }

    @Test
    fun `uses fallback provider when BCB has no usable quote`() {
        val bcbCalls = AtomicInteger()
        stubServer { exchange ->
            if (exchange.requestURI.path.contains("/latest")) {
                json("""{"date": "2026-04-19", "rates": {"BRL": 5.50}}""")
            } else {
                bcbCalls.incrementAndGet()
                json("""{"value": [{"cotacaoVenda": 5.10, "dataHoraCotacao": "not-a-date"}]}""")
            }
        }.use { server ->
            val service = service(server.baseUrl, server.baseUrl)

            val quote = service.getLatestBrlRate(Currency.USD)

            assertNotNull(quote)
            assertEquals(BigDecimal("5.50"), quote?.rateToBrl)
            assertEquals(LocalTime.NOON, quote?.quotedAt?.toLocalTime())
            assertEquals(3, bcbCalls.get())
        }
    }

    @Test
    fun `returns null when BCB and fallback providers fail without cached quote`() {
        stubServer { exchange ->
            exchange.sendResponseHeaders(500, 0)
            ""
        }.use { server ->
            val service = service(server.baseUrl, server.baseUrl)

            val quote = service.getLatestBrlRate(Currency.EUR)

            assertNull(quote)
        }
    }

    @Test
    fun `uses fresh cached quote without another external call`() {
        val calls = AtomicInteger()
        stubServer {
            calls.incrementAndGet()
            json("""{"value": [{"cotacaoVenda": 4.40, "dataHoraCotacao": "2026-04-20 11:00:00.0"}]}""")
        }.use { server ->
            val service = service(server.baseUrl, server.baseUrl, cacheTtlSeconds = 3600)

            val first = service.getLatestBrlRate(Currency.EUR)
            val second = service.getLatestBrlRate(Currency.EUR)

            assertEquals(first, second)
            assertEquals(1, calls.get())
        }
    }

    @Test
    fun `uses stale cached quote when providers fail and stale window is valid`() {
        val calls = AtomicInteger()
        stubServer { exchange ->
            val call = calls.incrementAndGet()
            if (call == 1) {
                json("""{"value": [{"cotacaoVenda": 5.75, "dataHoraCotacao": "2026-04-20 09:00:00.0"}]}""")
            } else {
                exchange.sendResponseHeaders(500, 0)
                ""
            }
        }.use { server ->
            val service = service(
                baseUrl = server.baseUrl,
                fallbackBaseUrl = server.baseUrl,
                cacheTtlSeconds = -1,
                maxStaleSeconds = 3600,
            )

            val first = service.getLatestBrlRate(Currency.USD)
            val second = service.getLatestBrlRate(Currency.USD)

            assertEquals(first, second)
            assertEquals(BigDecimal("5.75"), second?.rateToBrl)
            assertTrue(calls.get() > 1)
        }
    }

    @Test
    fun `fallback response without BRL rate returns null`() {
        stubServer { exchange ->
            if (exchange.requestURI.path.contains("/latest")) {
                json("""{"date": "2026-04-19", "rates": {"USD": 1.0}}""")
            } else {
                json("""{"value": []}""")
            }
        }.use { server ->
            val service = service(server.baseUrl, server.baseUrl)

            assertNull(service.getLatestBrlRate(Currency.USD))
        }
    }

    private fun service(
        baseUrl: String,
        fallbackBaseUrl: String,
        cacheTtlSeconds: Long = 300,
        maxStaleSeconds: Long = 21_600,
    ): BcbExchangeRateService {
        return BcbExchangeRateService(
            baseUrl = baseUrl,
            lookbackDays = 10,
            fallbackBaseUrl = fallbackBaseUrl,
            connectTimeoutMs = 200,
            readTimeoutMs = 200,
            maxAttempts = 1,
            backoffMs = 0,
            cacheTtlSeconds = cacheTtlSeconds,
            maxStaleSeconds = maxStaleSeconds,
        )
    }

    private fun stubServer(handler: (HttpExchange) -> String): StubServer {
        return StubServer(handler)
    }

    private fun json(raw: String): String = raw.trimIndent()

    private class StubServer(
        private val handler: (HttpExchange) -> String,
    ) : AutoCloseable {
        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        val baseUrl: String
            get() = "http://127.0.0.1:${server.address.port}"

        init {
            server.createContext("/") { exchange ->
                val body = handler(exchange).toByteArray()
                if (exchange.responseCode == -1) {
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                }
                exchange.responseBody.use { it.write(body) }
            }
            server.start()
        }

        override fun close() {
            server.stop(0)
        }
    }
}
