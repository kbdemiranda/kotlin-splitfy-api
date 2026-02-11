package io.github.splitfy.api.interfaces.web

import io.github.splitfy.api.application.dto.PlatformDto
import io.github.splitfy.api.application.usecase.PlatformService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody
import io.swagger.v3.oas.annotations.Parameter

@RestController
@RequestMapping("/platforms")
class PlatformController(private val service: PlatformService) {

    @Operation(summary = "Criar plataforma", description = "Cria uma nova plataforma de assinatura")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Criado com sucesso"),
            ApiResponse(responseCode = "400", description = "Requisição inválida")
        ]
    )
    @PostMapping
    fun create(@OpenApiRequestBody(description = "Dados da plataforma a ser criada") @RequestBody dto: PlatformDto): ResponseEntity<PlatformDto> {
        val created = service.create(dto)
        return ResponseEntity.status(201).body(created)
    }

    @Operation(summary = "Listar plataformas", description = "Retorna todas as plataformas")
    @GetMapping
    fun list(): List<PlatformDto> = service.findAll()

    @Operation(summary = "Obter plataforma", description = "Retorna uma plataforma por id")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Encontrado"),
            ApiResponse(responseCode = "404", description = "Não encontrado")
        ]
    )
    @GetMapping("/{id}")
    fun get(@Parameter(description = "ID da plataforma") @PathVariable id: Long): ResponseEntity<PlatformDto> {
        val found = service.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(found)
    }

    @Operation(summary = "Atualizar plataforma", description = "Atualiza uma plataforma existente")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Atualizado"),
            ApiResponse(responseCode = "404", description = "Não encontrado")
        ]
    )
    @PutMapping("/{id}")
    fun update(
        @Parameter(description = "ID da plataforma") @PathVariable id: Long,
        @OpenApiRequestBody(description = "Dados atualizados da plataforma") @RequestBody dto: PlatformDto
    ): ResponseEntity<PlatformDto> {
        val updated = service.update(id, dto) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(updated)
    }

    @Operation(summary = "Deletar plataforma", description = "Marca uma plataforma como deletada")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Deletado"),
            ApiResponse(responseCode = "404", description = "Não encontrado")
        ]
    )
    @DeleteMapping("/{id}")
    fun delete(@Parameter(description = "ID da plataforma") @PathVariable id: Long): ResponseEntity<Void> {
        val deleted = service.delete(id)
        return if (deleted) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }
}
