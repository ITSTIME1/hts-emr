package com.htsemr.billing.entity

import com.htsemr.common.Money
import com.htsemr.master.CoverageType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "invoice_line")
internal class InvoiceLine private constructor(
    @Column(nullable = false) val invoiceId: Long,
    @Column(nullable = false) val orderItemId: Long,
    @Column(nullable = false) val feeItemId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val coverageType: CoverageType,

    @Column(nullable = false) val unitPrice: Money,
    @Column(nullable = false) val quantity: Int,
    @Column(nullable = false) val lineTotal: Money,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    companion object {
        fun of(invoiceId: Long, item: PricedItem) = InvoiceLine(
            invoiceId = invoiceId,
            orderItemId = item.orderItemId,
            feeItemId = item.feeItemId,
            coverageType = item.coverageType,
            unitPrice = item.unitPrice,
            quantity = item.quantity,
            lineTotal = item.lineTotal,
        )
    }
}
