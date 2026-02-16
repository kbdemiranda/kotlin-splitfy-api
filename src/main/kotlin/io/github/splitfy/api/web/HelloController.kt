package io.github.splitfy.api.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/")
@Tag(name = "General", description = "General endpoints")
class HelloController {

    @Operation(summary = "Greeting", description = "Simple health-check style endpoint that returns a welcome message")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Greeting returned")
        ]
    )
    @GetMapping()
    fun hello(): String{
        return "Hello, World, Welcome to Splitfy!"
    }
}
