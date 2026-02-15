package io.github.splitfy.api.web.dashboard

import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.service.dashboard.DashboardService
import io.github.splitfy.api.web.dashboard.dto.DashboardKpiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth

@RestController
@RequestMapping("/dashboard")
@Tag(name = "Dashboard", description = "Dashboard and KPI endpoints")
class DashboardController(
    private val dashboardService: DashboardService,
) {

    @Operation(summary = "Get billing dashboard KPIs")
    @GetMapping("/kpis")
    fun getKpis(
        @Parameter(description = "Reference month in format YYYY-MM, e.g. 2026-02")
        @RequestParam(required = false) referenceMonth: String?
    ): ResponseEntity<DashboardKpiResponse> {
        val parsedMonth = parseReferenceMonth(referenceMonth)
        return ResponseEntity.ok(dashboardService.getKpis(parsedMonth))
    }

    private fun parseReferenceMonth(referenceMonth: String?): YearMonth? {
        if (referenceMonth.isNullOrBlank()) {
            return null
        }
        return runCatching { YearMonth.parse(referenceMonth) }
            .getOrElse { throw BadRequestApiException("Invalid referenceMonth. Expected format: YYYY-MM") }
    }
}
