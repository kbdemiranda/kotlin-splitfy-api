package io.github.splitfy.api.web.docs

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class DocsRedirectController {

    @GetMapping("/docs")
    fun docs(): String = "redirect:/docs/index.html"
}
