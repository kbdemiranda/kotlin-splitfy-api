package io.github.splitfy.api.service.email

import org.springframework.stereotype.Service
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.util.Locale

@Service
class EmailTemplateService(
    private val templateEngine: TemplateEngine
) {

    constructor() : this(defaultTemplateEngine())

    fun render(
        preheader: String,
        heading: String,
        paragraphs: List<String>,
        highlight: String? = null,
        bulletItems: List<String> = emptyList(),
        footer: String? = null
    ): String {
        val context = Context(Locale("pt", "BR")).apply {
            setVariable("preheader", preheader)
            setVariable("heading", heading)
            setVariable("paragraphs", paragraphs)
            setVariable("highlight", highlight)
            setVariable("bulletItems", bulletItems)
            setVariable("footer", footer)
        }
        return templateEngine.process("email/base", context)
    }

    fun renderTemplate(templateName: String, variables: Map<String, Any?>): String {
        val context = Context(Locale("pt", "BR"))
        variables.forEach { (key, value) -> context.setVariable(key, value) }
        return templateEngine.process(templateName, context)
    }

    companion object {
        private fun defaultTemplateEngine(): TemplateEngine {
            val resolver = ClassLoaderTemplateResolver().apply {
                prefix = "templates/"
                suffix = ".html"
                templateMode = TemplateMode.HTML
                characterEncoding = "UTF-8"
                isCacheable = false
            }
            return SpringTemplateEngine().apply {
                setTemplateResolver(resolver)
            }
        }
    }
}
