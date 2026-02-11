package io.github.splitfy.api.interfaces.web

import io.swagger.v3.oas.annotations.Operation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/")
class HelloController {

    @Operation(summary = "Saudação", description = "Endpoint de teste que retorna uma mensagem de boas-vindas")
    @GetMapping()
    fun hello(): String{
        return "Hello, World, Welcome to Splitfy!"
    }
}