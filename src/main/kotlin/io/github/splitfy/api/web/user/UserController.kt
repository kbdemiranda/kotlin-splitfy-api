package io.github.splitfy.api.web.user

import io.github.splitfy.api.service.user.UserService
import io.github.splitfy.api.web.user.dto.UserCreateRequest
import io.github.splitfy.api.web.user.dto.UserDashboardEmailPreferenceRequest
import io.github.splitfy.api.web.user.dto.UserResponse
import io.github.splitfy.api.web.user.dto.UserUpdateRequest
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
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "Operations related to application users")
class UserController(
    private val userService: UserService,
) {

    @Operation(
        summary = "Create user",
        description = "Creates a new user."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "User created"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "409", description = "E-mail already in use")
        ]
    )
    @PostMapping
    fun create(@Valid @RequestBody request: UserCreateRequest): ResponseEntity<UserResponse> {
        val created = userService.create(request)
        return ResponseEntity.created(URI.create("/users/${created.id}")).body(created)
    }

    @Operation(
        summary = "List users",
        description = "Returns a paginated list of users, including whether each user is selected to receive the scheduled dashboard e-mail."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Users listed")
        ]
    )
    @GetMapping
    fun list(
        @Parameter(description = "Optional filter by user name (partial match, case-insensitive)")
        @RequestParam(required = false) name: String?,
        @Parameter(description = "Pagination and sorting configuration")
        @PageableDefault(page = 0, size = 10, sort = ["name"]) pageable: Pageable,
    ): ResponseEntity<Page<UserResponse>> {
        return ResponseEntity.ok(userService.list(pageable, name))
    }

    @Operation(
        summary = "Get user by ID",
        description = "Returns a user by its unique identifier, including whether this user is selected to receive the scheduled dashboard e-mail."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User found"),
            ApiResponse(responseCode = "404", description = "User not found")
        ]
    )
    @GetMapping("/{id}")
    fun getById(@Parameter(description = "User ID") @PathVariable id: UUID): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.getById(id))
    }

    @Operation(
        summary = "Update user",
        description = "Updates an existing user."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User updated"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "404", description = "User not found"),
            ApiResponse(responseCode = "409", description = "E-mail already in use")
        ]
    )
    @PutMapping("/{id}")
    fun update(
        @Parameter(description = "User ID") @PathVariable id: UUID,
        @Valid @RequestBody request: UserUpdateRequest
    ): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.update(id, request))
    }

    @Operation(
        summary = "Update dashboard e-mail recipient flag",
        description = "Updates only the flag that defines whether the user receives the scheduled dashboard e-mail. If another active user is already selected, the API returns 409 unless `force=true` is informed."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Dashboard e-mail preference updated"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "404", description = "User not found"),
            ApiResponse(responseCode = "409", description = "Another user is already configured as the dashboard e-mail recipient")
        ]
    )
    @PatchMapping("/{id}/dashboard-email-preference")
    fun updateDashboardEmailPreference(
        @Parameter(description = "User ID") @PathVariable id: UUID,
        @Valid @RequestBody request: UserDashboardEmailPreferenceRequest
    ): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.updateDashboardEmailPreference(id, request))
    }

    @Operation(summary = "Delete user", description = "Soft-deletes a user")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "User deleted"),
            ApiResponse(responseCode = "404", description = "User not found")
        ]
    )
    @DeleteMapping("/{id}")
    fun delete(@Parameter(description = "User ID") @PathVariable id: UUID): ResponseEntity<Void> {
        userService.softDelete(id)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Associate profile", description = "Associates a profile to a user")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Profile associated"),
            ApiResponse(responseCode = "404", description = "User or profile not found")
        ]
    )
    @PutMapping("/{id}/profile/{profileId}")
    fun associateProfile(
        @Parameter(description = "User ID") @PathVariable id: UUID,
        @Parameter(description = "Profile ID") @PathVariable profileId: UUID
    ): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.associateProfile(id, profileId))
    }

    @Operation(summary = "Disassociate profile", description = "Removes the profile association from a user")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Profile disassociated"),
            ApiResponse(responseCode = "404", description = "User not found")
        ]
    )
    @DeleteMapping("/{id}/profile")
    fun disassociateProfile(@Parameter(description = "User ID") @PathVariable id: UUID): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.disassociateProfile(id))
    }
}
