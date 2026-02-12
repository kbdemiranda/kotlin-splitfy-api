package io.github.splitfy.api.domain.converter

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import java.time.MonthDay
import java.time.format.DateTimeParseException

class MonthDayAttributeConverterTest {

    private val converter = MonthDayAttributeConverter()

    @Test
    fun `convertToDatabaseColumn with MonthDay returns expected string`() {
        val md = MonthDay.of(12, 25)
        val db = converter.convertToDatabaseColumn(md)
        // MonthDay.toString() produces --MM-dd (e.g. --12-25)
        assertEquals("--12-25", db)
    }

    @Test
    fun `convertToDatabaseColumn with null returns null`() {
        assertNull(converter.convertToDatabaseColumn(null))
    }

    @Test
    fun `convertToEntityAttribute with valid string returns MonthDay`() {
        val md = converter.convertToEntityAttribute("--03-05")
        assertEquals(MonthDay.of(3, 5), md)
    }

    @Test
    fun `convertToEntityAttribute with null returns null`() {
        assertNull(converter.convertToEntityAttribute(null))
    }

    @Test
    fun `convertToEntityAttribute with invalid string throws exception`() {
        assertFailsWith<DateTimeParseException> {
            converter.convertToEntityAttribute("invalid")
        }
    }
}
