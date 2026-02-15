package io.github.splitfy.api.domain.converter

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.time.YearMonth

@Converter(autoApply = false)
class YearMonthAttributeConverter : AttributeConverter<YearMonth, String> {

    override fun convertToDatabaseColumn(attribute: YearMonth?): String? = attribute?.toString()

    override fun convertToEntityAttribute(dbData: String?): YearMonth? = dbData?.let { YearMonth.parse(it) }
}
