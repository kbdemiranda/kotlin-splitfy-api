package io.github.splitfy.api.domain.converter

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.time.MonthDay

@Converter(autoApply = false)
class MonthDayAttributeConverter : AttributeConverter<MonthDay, String> {
    override fun convertToDatabaseColumn(attribute: MonthDay?): String? {
        return attribute?.toString() // MM-DD
    }

    override fun convertToEntityAttribute(dbData: String?): MonthDay? {
        return dbData?.let { MonthDay.parse(it) }
    }
}
