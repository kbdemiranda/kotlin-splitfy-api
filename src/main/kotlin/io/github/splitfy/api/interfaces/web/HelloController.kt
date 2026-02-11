package io.github.splitfy.api.interfaces.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/")
@Tag(name = "General", description = "General endpoints")
class HelloController {

    @Operation(summary = "Greeting", description = "Test endpoint that returns a welcome message")
    @GetMapping()
    fun hello(): String{
        return "Hello, World, Welcome to Splitfy!"
    }
}
