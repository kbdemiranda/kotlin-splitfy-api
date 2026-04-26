package io.github.splitfy.api.service.email

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.IContext

@ExtendWith(MockitoExtension::class)
class EmailTemplateServiceTest {

    @Mock
    lateinit var templateEngine: TemplateEngine

    @Test
    fun `render sends base email variables to template engine`() {
        whenever(templateEngine.process(eq("email/base"), any<IContext>())).thenAnswer { invocation ->
            val context = invocation.getArgument<IContext>(1)
            listOf(
                context.getVariable("preheader"),
                context.getVariable("heading"),
                context.getVariable("highlight"),
                context.getVariable("footer"),
            ).joinToString("|")
        }
        val service = EmailTemplateService(templateEngine)

        val html = service.render(
            preheader = "Preview",
            heading = "Heading",
            paragraphs = listOf("First", "Second"),
            highlight = "Important",
            bulletItems = listOf("A", "B"),
            footer = "Footer",
        )

        assertEquals("Preview|Heading|Important|Footer", html)
        verify(templateEngine).process(eq("email/base"), any<IContext>())
    }

    @Test
    fun `renderTemplate forwards arbitrary variables to requested template`() {
        whenever(templateEngine.process(eq("email/custom"), any<IContext>())).thenAnswer { invocation ->
            val context = invocation.getArgument<IContext>(1)
            "${context.getVariable("name")}:${context.getVariable("amount")}"
        }
        val service = EmailTemplateService(templateEngine)

        val html = service.renderTemplate("email/custom", mapOf("name" to "Ana", "amount" to "R$ 10,00"))

        assertEquals("Ana:R$ 10,00", html)
    }

    @Test
    fun `default constructor renders bundled base template`() {
        val html = EmailTemplateService().render(
            preheader = "Preview",
            heading = "Welcome",
            paragraphs = listOf("Line one"),
            highlight = null,
            bulletItems = emptyList(),
            footer = "Footer",
        )

        assertTrue(html.contains("Welcome"))
        assertTrue(html.contains("Line one"))
        assertTrue(html.contains("Footer"))
    }
}
