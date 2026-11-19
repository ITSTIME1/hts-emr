package com.htsemr.billing.api

import com.htsemr.api.ApiExceptionHandler
import com.htsemr.billing.InvoiceLineView
import com.htsemr.billing.InvoiceNotFoundException
import com.htsemr.billing.InvoiceQueryService
import com.htsemr.billing.InvoiceView
import com.htsemr.common.Money
import com.htsemr.master.CoverageType
import com.htsemr.reception.InsuranceType
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDateTime

class InvoiceControllerTest {

    private val invoiceQueryService = mock(InvoiceQueryService::class.java)

    private val mockMvc = MockMvcBuilders
        .standaloneSetup(InvoiceController(invoiceQueryService))
        .setControllerAdvice(ApiExceptionHandler())
        .build()

    @Test
    fun `TC-A-47 산정 조회는 200이고 금액을 원 단위 숫자로 내보낸다`() {
        given(invoiceQueryService.invoice(700L)).willReturn(
            InvoiceView(
                id = 700L,
                visitId = 57L,
                eligibilitySeq = 1,
                insuranceType = InsuranceType.NATIONAL_HEALTH,
                copayRate = 30,
                coveredLineSum = Money(10000),
                coveredTotal = Money(10000),
                coveredCopay = Money(3000),
                insurerAmount = Money(7000),
                nonCoveredTotal = Money(2000),
                patientAmount = Money(5000),
                calculatedAt = LocalDateTime.of(2026, 11, 19, 10, 30, 0),
                lines = listOf(
                    InvoiceLineView(
                        id = 1L,
                        orderItemId = 901L,
                        feeItemId = 10L,
                        coverageType = CoverageType.COVERED,
                        unitPrice = Money(5000),
                        quantity = 2,
                        lineTotal = Money(10000),
                    ),
                ),
            ),
        )

        mockMvc.get("/api/v1/invoices/700").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(700) }
            jsonPath("$.visitId") { value(57) }
            jsonPath("$.insuranceType") { value("NATIONAL_HEALTH") }
            jsonPath("$.copayRate") { value(30) }
            jsonPath("$.patientAmount") { value(5000) }
            jsonPath("$.insurerAmount") { value(7000) }
            jsonPath("$.lines.length()") { value(1) }
            jsonPath("$.lines[0].orderItemId") { value(901) }
            jsonPath("$.lines[0].coverageType") { value("COVERED") }
            jsonPath("$.lines[0].lineTotal") { value(10000) }
        }
    }

    @Test
    fun `TC-A-48 없는 산정은 404 INVOICE_NOT_FOUND`() {
        given(invoiceQueryService.invoice(999L)).willThrow(InvoiceNotFoundException(999L))

        mockMvc.get("/api/v1/invoices/999").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("INVOICE_NOT_FOUND") }
        }
    }
}
