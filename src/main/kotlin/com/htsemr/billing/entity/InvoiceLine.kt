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

/**
 * 산정 내역(InvoiceLine). 오더 항목 하나의 계산 결과를 값으로 남긴다. 만든 뒤 바뀌지 않는다.
 * 항목별 본인부담·공단부담은 나누지 않는다(A-32).
 */
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
        /** 저장한 산정의 id와, 그 산정을 계산할 때 쓴 항목으로 만든다. 총액은 계산할 때의 값을 그대로 쓴다. */
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
