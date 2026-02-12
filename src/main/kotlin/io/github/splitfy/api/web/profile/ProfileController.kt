package io.github.splitfy.api.web.profile

import io.github.splitfy.api.service.profile.ProfileService
import io.github.splitfy.api.web.profile.dto.ProfileRequest
import io.github.splitfy.api.web.profile.dto.ProfileResponse
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
class ProfileController(
    private val profileService: ProfileService,
) {

    @PostMapping
    fun create(@Valid @RequestBody request: ProfileRequest): ResponseEntity<ProfileResponse> {
        val created = profileService.create(request)
        return ResponseEntity.created(URI.create("/profiles/${created.id}")).body(created)
    }

    @GetMapping
    fun list(@PageableDefault(page = 0, size = 10, sort = ["name"]) pageable: Pageable): ResponseEntity<Page<ProfileResponse>> {
        return ResponseEntity.ok(profileService.list(pageable))
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): ResponseEntity<ProfileResponse> {
        return ResponseEntity.ok(profileService.getById(id))
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: ProfileRequest): ResponseEntity<ProfileResponse> {
        return ResponseEntity.ok(profileService.update(id, request))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        profileService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
