package io.github.splitfy.api.web.profile

import io.github.splitfy.api.service.profile.ProfileService
import io.github.splitfy.api.web.profile.dto.ProfileRequest
import io.github.splitfy.api.web.profile.dto.ProfileResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/profiles")
@Tag(name = "Profiles", description = "Operations related to user access profiles")
class ProfileController(
    private val profileService: ProfileService,
) {

    @Operation(summary = "Create profile", description = "Creates a new access profile")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Profile created"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "409", description = "Profile already exists")
        ]
    )
    @PostMapping
    fun create(@Valid @RequestBody request: ProfileRequest): ResponseEntity<ProfileResponse> {
        val created = profileService.create(request)
        return ResponseEntity.created(URI.create("/profiles/${created.id}")).body(created)
    }

    @Operation(summary = "List profiles", description = "Returns a paginated list of profiles")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Profiles listed")
        ]
    )
    @GetMapping
    fun list(
        @Parameter(description = "Pagination and sorting configuration")
        @PageableDefault(page = 0, size = 10, sort = ["name"]) pageable: Pageable
    ): ResponseEntity<Page<ProfileResponse>> {
        return ResponseEntity.ok(profileService.list(pageable))
    }

    @Operation(summary = "Get profile by ID", description = "Returns a profile by its unique identifier")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Profile found"),
            ApiResponse(responseCode = "404", description = "Profile not found")
        ]
    )
    @GetMapping("/{id}")
    fun getById(@Parameter(description = "Profile ID") @PathVariable id: UUID): ResponseEntity<ProfileResponse> {
        return ResponseEntity.ok(profileService.getById(id))
    }

    @Operation(summary = "Update profile", description = "Updates an existing profile")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Profile updated"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "404", description = "Profile not found"),
            ApiResponse(responseCode = "409", description = "Profile already exists")
        ]
    )
    @PutMapping("/{id}")
    fun update(
        @Parameter(description = "Profile ID") @PathVariable id: UUID,
        @Valid @RequestBody request: ProfileRequest
    ): ResponseEntity<ProfileResponse> {
        return ResponseEntity.ok(profileService.update(id, request))
    }

    @Operation(summary = "Delete profile", description = "Deletes a profile by ID")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Profile deleted"),
            ApiResponse(responseCode = "404", description = "Profile not found")
        ]
    )
    @DeleteMapping("/{id}")
    fun delete(@Parameter(description = "Profile ID") @PathVariable id: UUID): ResponseEntity<Void> {
        profileService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
