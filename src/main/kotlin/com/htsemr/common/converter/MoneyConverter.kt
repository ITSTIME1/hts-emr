package com.htsemr.common.converter

import com.htsemr.common.Money
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter


@Converter(autoApply = true)
internal class MoneyConverter : AttributeConverter<Money, Long> {
    override fun convertToDatabaseColumn(money: Money?) = money?.amount
    override fun convertToEntityAttribute(value: Long?) = value?.let(::Money)
}