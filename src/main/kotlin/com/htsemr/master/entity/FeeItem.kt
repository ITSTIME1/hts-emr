package com.htsemr.master.entity

import com.htsemr.master.CoverageType
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.type.NumericBooleanConverter

@Entity
@Table(name = "fee_item")
internal class FeeItem(
    @Column(nullable = false, unique = true)
    val code : String,

    @Column(nullable = false)
    val name : String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val coverageType : CoverageType,

    @Convert(converter = NumericBooleanConverter::class)
    @Column(nullable = false)
    val executableBeforePayment: Boolean,
    ) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
