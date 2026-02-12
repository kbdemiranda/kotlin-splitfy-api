package io.github.splitfy.api.web.user

import io.github.splitfy.api.service.user.UserService
import io.github.splitfy.api.web.user.dto.UserCreateRequest
import io.github.splitfy.api.web.user.dto.UserResponse
import io.github.splitfy.api.web.user.dto.UserUpdateRequest
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/users")
class UserController(
    private val userService: UserService,
) {

    @PostMapping
    fun create(@Valid @RequestBody request: UserCreateRequest): ResponseEntity<UserResponse> {
        val created = userService.create(request)
        return ResponseEntity.created(URI.create("/users/${created.id}")).body(created)
    }

    @GetMapping
    fun list(
        @RequestParam(required = false) name: String?,
        @PageableDefault(page = 0, size = 10, sort = ["name"]) pageable: Pageable,
    ): ResponseEntity<Page<UserResponse>> {
        return ResponseEntity.ok(userService.list(pageable, name))
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.getById(id))
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: UserUpdateRequest): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.update(id, request))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        userService.softDelete(id)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/{id}/profile/{profileId}")
    fun associateProfile(@PathVariable id: UUID, @PathVariable profileId: UUID): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.associateProfile(id, profileId))
    }

    @DeleteMapping("/{id}/profile")
    fun disassociateProfile(@PathVariable id: UUID): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.disassociateProfile(id))
    }
}
