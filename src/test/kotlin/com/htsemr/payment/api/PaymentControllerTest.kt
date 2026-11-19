package com.htsemr.payment.api

import com.htsemr.api.ApiExceptionHandler
import com.htsemr.common.Money
import com.htsemr.master.CounterNotFoundException
import com.htsemr.billing.InvoiceNotFoundException
import com.htsemr.payment.CashResult
import com.htsemr.payment.InvoiceAlreadyUsedException
import com.htsemr.payment.NothingToPayException
import com.htsemr.payment.PaymentAmountExceededException
import com.htsemr.payment.PaymentCompletionConflictException
import com.htsemr.payment.PaymentLineStatus
import com.htsemr.payment.PaymentLineView
import com.htsemr.payment.PaymentMethod
import com.htsemr.payment.PaymentNotFoundException
import com.htsemr.payment.PaymentQueryService
import com.htsemr.payment.PaymentService
import com.htsemr.payment.PaymentStartConflictException
import com.htsemr.payment.PaymentStatus
import com.htsemr.payment.PaymentView
import com.htsemr.reception.VisitNotFoundException
import org.hamcrest.Matchers.endsWith
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDateTime

class PaymentControllerTest {

    private val paymentService = mock(PaymentService::class.java)
    private val paymentQueryService = mock(PaymentQueryService::class.java)

    private val mockMvc = MockMvcBuilders
        .standaloneSetup(PaymentController(paymentService, paymentQueryService))
        .setControllerAdvice(ApiExceptionHandler())
        .build()

    private fun requestInvoice(visitId: Long) = mockMvc.post("/api/v1/visits/$visitId/invoices")

    private fun cashPayment(invoiceId: Long, body: String) =
        mockMvc.post("/api/v1/invoices/$invoiceId/cash-payments") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private val validBody =
        """{"counterId":3,"cashierNo":"C0001","receivedAmount":10000,"appliedAmount":5000}"""


    @Test
    fun `TC-A-30 산정 요청은 201과 Location, invoiceId를 돌려준다`() {
        given(paymentService.requestInvoice(57L)).willReturn(700L)

        requestInvoice(57L).andExpect {
            status { isCreated() }
            header { string("Location", endsWith("/api/v1/invoices/700")) }
            jsonPath("$.invoiceId") { value(700) }
        }
    }

    @Test
    fun `TC-A-31 수납할 항목이 없으면 409 NOTHING_TO_PAY`() {
        given(paymentService.requestInvoice(57L)).willThrow(NothingToPayException(57L))

        requestInvoice(57L).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("NOTHING_TO_PAY") }
        }
    }

    @Test
    fun `TC-A-32 없는 접수에 산정을 요청하면 404 VISIT_NOT_FOUND`() {
        given(paymentService.requestInvoice(999L)).willThrow(VisitNotFoundException(999L))

        requestInvoice(999L).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("VISIT_NOT_FOUND") }
        }
    }


    @Test
    fun `TC-A-33 덜 받았으면 200 IN_PROGRESS와 남은 금액을 돌려준다`() {
        given(paymentService.receiveCash(700L, 3L, "C0001", Money(10000), Money(5000)))
            .willReturn(CashResult.InProgress(800L, Money(3000)))

        cashPayment(700L, validBody).andExpect {
            status { isOk() }
            jsonPath("$.paymentId") { value(800) }
            jsonPath("$.status") { value("IN_PROGRESS") }
            jsonPath("$.remaining") { value(3000) }
        }
    }

    @Test
    fun `TC-A-34 다 받았으면 200 COMPLETED이고 남은 금액이 없다`() {
        given(paymentService.receiveCash(700L, 3L, "C0001", Money(10000), Money(5000)))
            .willReturn(CashResult.Completed(800L))

        cashPayment(700L, validBody).andExpect {
            status { isOk() }
            jsonPath("$.paymentId") { value(800) }
            jsonPath("$.status") { value("COMPLETED") }
            jsonPath("$.remaining") { doesNotExist() }
        }
    }

    @Test
    fun `TC-A-35 완료를 막는 항목이 있으면 409 PAYMENT_COMPLETION_BLOCKED와 막은 항목 ID`() {
        given(paymentService.receiveCash(700L, 3L, "C0001", Money(10000), Money(5000)))
            .willReturn(CashResult.CompletionBlocked(800L, setOf(902L, 901L), setOf(903L)))

        cashPayment(700L, validBody).andExpect {
            status { isConflict() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("PAYMENT_COMPLETION_BLOCKED") }
            jsonPath("$.paymentId") { value(800) }
            jsonPath("$.ineffectiveItemIds[0]") { value(901) }
            jsonPath("$.ineffectiveItemIds[1]") { value(902) }
            jsonPath("$.alreadyPaidItemIds[0]") { value(903) }
        }
    }


    @Test
    fun `TC-A-36 반영 금액이 0이면 400 VALIDATION_FAILED이고 서비스를 부르지 않는다`() {
        cashPayment(700L, """{"counterId":3,"cashierNo":"C0001","receivedAmount":10000,"appliedAmount":0}""")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.errors[?(@.field=='appliedAmount')]") { exists() }
            }

        verifyNoInteractions(paymentService)
    }

    @Test
    fun `TC-A-37 받은 금액이 반영 금액보다 작으면 400 VALIDATION_FAILED`() {
        cashPayment(700L, """{"counterId":3,"cashierNo":"C0001","receivedAmount":3000,"appliedAmount":5000}""")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.errors[0].field") { value("receivedCoversApplied") }
                jsonPath("$.errors[0].reason") { value("받은 금액은 반영 금액 이상이어야 합니다") }
            }

        verifyNoInteractions(paymentService)
    }

    @Test
    fun `TC-A-38 수납자 번호가 빈 값이면 400 VALIDATION_FAILED`() {
        cashPayment(700L, """{"counterId":3,"cashierNo":"","receivedAmount":10000,"appliedAmount":5000}""")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0].field") { value("cashierNo") }
            }

        verifyNoInteractions(paymentService)
    }


    @Test
    fun `TC-A-39 결제 합계가 본인부담을 넘으면 422 PAYMENT_AMOUNT_EXCEEDED`() {
        given(paymentService.receiveCash(700L, 3L, "C0001", Money(10000), Money(5000)))
            .willThrow(PaymentAmountExceededException(700L, Money(5000), Money(7000)))

        cashPayment(700L, validBody).andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("PAYMENT_AMOUNT_EXCEEDED") }
        }
    }

    @Test
    fun `TC-A-40 이미 쓴 산정이면 409 INVOICE_ALREADY_USED`() {
        given(paymentService.receiveCash(700L, 3L, "C0001", Money(10000), Money(5000)))
            .willThrow(InvoiceAlreadyUsedException(700L))

        cashPayment(700L, validBody).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("INVOICE_ALREADY_USED") }
        }
    }

    @Test
    fun `TC-A-41 다른 창구가 방금 수납을 시작했으면 409 PAYMENT_START_CONFLICT`() {
        given(paymentService.receiveCash(700L, 3L, "C0001", Money(10000), Money(5000)))
            .willThrow(PaymentStartConflictException(700L))

        cashPayment(700L, validBody).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PAYMENT_START_CONFLICT") }
        }
    }

    @Test
    fun `TC-A-42 같은 항목을 다른 수납 건이 방금 완료했으면 409 PAYMENT_COMPLETION_CONFLICT`() {
        given(paymentService.receiveCash(700L, 3L, "C0001", Money(10000), Money(5000)))
            .willThrow(PaymentCompletionConflictException(700L))

        cashPayment(700L, validBody).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PAYMENT_COMPLETION_CONFLICT") }
        }
    }

    @Test
    fun `TC-A-43 없는 산정으로 수납하면 404 INVOICE_NOT_FOUND`() {
        given(paymentService.receiveCash(999L, 3L, "C0001", Money(10000), Money(5000)))
            .willThrow(InvoiceNotFoundException(999L))

        cashPayment(999L, validBody).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("INVOICE_NOT_FOUND") }
        }
    }

    @Test
    fun `TC-A-44 없는 창구로 수납하면 404 COUNTER_NOT_FOUND`() {
        given(paymentService.receiveCash(700L, 3L, "C0001", Money(10000), Money(5000)))
            .willThrow(CounterNotFoundException(3L))

        cashPayment(700L, validBody).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("COUNTER_NOT_FOUND") }
        }
    }



    @Test
    fun `TC-A-45 수납 조회는 200이고 결제 내역을 돌려준다`() {
        given(paymentQueryService.payment(800L)).willReturn(
            PaymentView(
                id = 800L,
                invoiceId = 700L,
                visitId = 57L,
                counterId = 3L,
                cashierNo = "C0001",
                patientAmount = Money(5000),
                paidAmount = Money(5000),
                status = PaymentStatus.COMPLETED,
                startedAt = LocalDateTime.of(2026, 11, 19, 10, 30, 0),
                completedAt = LocalDateTime.of(2026, 11, 19, 10, 31, 0),
                orderItemIds = listOf(901L, 902L),
                lines = listOf(
                    PaymentLineView(
                        id = 1L,
                        method = PaymentMethod.CASH,
                        status = PaymentLineStatus.RECEIVED,
                        receivedAmount = Money(10000),
                        amount = Money(5000),
                        change = Money(5000),
                        receivedAt = LocalDateTime.of(2026, 11, 19, 10, 31, 0),
                    ),
                ),
            ),
        )

        mockMvc.get("/api/v1/payments/800").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(800) }
            jsonPath("$.status") { value("COMPLETED") }
            jsonPath("$.paidAmount") { value(5000) }
            jsonPath("$.orderItemIds.length()") { value(2) }
            jsonPath("$.lines[0].method") { value("CASH") }
            jsonPath("$.lines[0].receivedAmount") { value(10000) }
            jsonPath("$.lines[0].appliedAmount") { value(5000) }
            jsonPath("$.lines[0].change") { value(5000) }
        }
    }

    @Test
    fun `TC-A-46 없는 수납 건은 404 PAYMENT_NOT_FOUND`() {
        given(paymentQueryService.payment(999L)).willThrow(PaymentNotFoundException(999L))

        mockMvc.get("/api/v1/payments/999").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("PAYMENT_NOT_FOUND") }
        }
    }
}
