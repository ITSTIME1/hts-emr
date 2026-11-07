package com.htsemr.common.converter

import com.htsemr.common.Money
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * Money와 DB 컬럼(NUMBER, 원 단위)을 변환한다.
 *
 * autoApply라서 엔티티의 모든 Money 필드에 자동으로 적용된다. 엔티티에 @Convert를 붙이지 않아도 되고,
 * 다른 모듈이 이 클래스를 import할 필요도 없다. 그래서 공개 패키지가 아닌 하위 패키지에 둔다.
 */
@Converter(autoApply = true)
internal class MoneyConverter : AttributeConverter<Money, Long> {
    override fun convertToDatabaseColumn(money: Money?) = money?.amount
    override fun convertToEntityAttribute(value: Long?) = value?.let(::Money)
}