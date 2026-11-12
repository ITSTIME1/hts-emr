package com.htsemr.billing

/**
 * 산정 조회. billing 모듈의 공개 기능이며 조회만 한다.
 *
 * - payment: 수납을 시작할 때 본인부담 합계를 확인한다(F-04).
 */
interface InvoiceQueryService {

    /**
     * 산정과 산정 내역. 내역은 id 순서다. 산정은 만든 뒤 바뀌지 않는다.
     *
     * @throws InvoiceNotFoundException 산정이 없을 때
     */
    fun invoice(invoiceId: Long): InvoiceView
}
