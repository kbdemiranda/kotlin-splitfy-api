package io.github.splitfy.api.web.platform

import io.github.splitfy.api.service.platform.PlatformService
import io.github.splitfy.api.web.platform.dto.PlatformRequest
import io.github.splitfy.api.web.platform.dto.PlatformResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault

@RestController
@RequestMapping("/platforms")
@Tag(name = "Platforms", description = "Operations related to subscription platforms")
class PlatformController(private val service: PlatformService) {

    @Operation(summary = "Create platform", description = "Creates a new subscription platform")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Created successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request")
        ]
    )
    @PostMapping
    fun create(@OpenApiRequestBody(description = "Platform data to be created") @RequestBody dto: PlatformRequest): ResponseEntity<PlatformResponse> {
        val created = service.create(dto)
        return ResponseEntity.status(201).body(created)
    }

    @Operation(summary = "List platforms", description = "Returns all platforms")
    @GetMapping
    fun list(
        @Parameter(description = "Filter by name (partial match, case-insensitive)")
        @RequestParam(required = false) name: String?,
        @PageableDefault(page = 0, size = 10, sort = ["name"]) pageable: Pageable
    ): Page<PlatformResponse> = service.findAll(pageable, name)

    @Operation(summary = "Get platform", description = "Returns a platform by ID")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Found"),
            ApiResponse(responseCode = "404", description = "Not found")
        ]
    )
    @GetMapping("/{id}")
    fun get(@Parameter(description = "Platform ID") @PathVariable id: Long): ResponseEntity<PlatformResponse> {
        val found = service.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(found)
    }

    @Operation(summary = "Update platform", description = "Updates an existing platform")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Updated"),
            ApiResponse(responseCode = "404", description = "Not found")
        ]
    )
    @PutMapping("/{id}")
    fun update(
        @Parameter(description = "Platform ID") @PathVariable id: Long,
        @OpenApiRequestBody(description = "Updated platform data") @RequestBody dto: PlatformRequest
    ): ResponseEntity<PlatformResponse> {
        val updated = service.update(id, dto) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(updated)
    }

    @Operation(summary = "Delete platform", description = "Marks a platform as deleted")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Deleted"),
            ApiResponse(responseCode = "404", description = "Not found")
        ]
    )
    @DeleteMapping("/{id}")
    fun delete(@Parameter(description = "Platform ID") @PathVariable id: Long): ResponseEntity<Void> {
        val deleted = service.delete(id)
        return ResponseEntity.noContent().build()
    }
}
