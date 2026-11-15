package com.htsemr.payment

import com.htsemr.billing.InvoiceNotFoundException
import com.htsemr.common.Money
import com.htsemr.master.CounterNotFoundException
import com.htsemr.reception.VisitNotFoundException

/**
 * 수납. payment 모듈의 공개 기능이다. 슬라이스 1은 현금 수납의 정상 흐름만 다룬다(payment.md 7절).
 *
 * 이중 수납 방어(INV-03, INV-05)는 일부러 없다. ADR-0003에서 재현 테스트로 실패를 보인 뒤 넣는다.
 */
interface PaymentService {

    /**
     * 수납하지 않은 효력 있는 오더 항목으로 산정을 요청하고, 산정 id를 돌려준다(A-02, F-03).
     * 수납 건은 아직 만들지 않는다. 수납 건은 첫 결제 때 생긴다(A-22).
     *
     * "수납하지 않은 항목"은 완료된 수납 건에 들어 있지 않은 항목이다. 진행 중인 수납 건의 항목도 다시 고를 수 있다.
     * INV-03은 완료된 수납 건만 따지므로 이것은 허용된다. 같은 항목이 두 번 완료되는 것은 receiveCash의 완료 단계에서 막는다.
     *
     * @throws NothingToPayException 수납할 항목이 없을 때
     * @throws VisitNotFoundException 내원 건이 없을 때(billing의 산정에서)
     */
    fun requestInvoice(visitId: Long): Long

    /**
     * 현금 결제를 받는다(T-PLN-12).
     *
     * - 그 산정으로 진행 중인 수납 건이 있으면 거기에 결제를 더한다(T-PAY-02). 없으면 새로 만든다(T-PAY-01).
     *   그 산정의 수납 건이 이미 끝났으면 거부한다(A-22, INV-05).
     * - 결제 합계가 본인부담 합계에 도달하면 같은 호출에서 완료를 시도한다(T-PAY-03). 포함한 항목이 모두 효력이 있고(INV-04)
     *   다른 완료된 수납 건에 없어야(INV-03) 완료한다.
     * - 완료가 막혀도 받은 결제는 남는다. 결과로 알린다([CashResult.CompletionBlocked]).
     *
     * @param received 환자에게서 받은 금액. 거스름돈 = received − applied
     * @param applied 수납 반영 금액. 결제 합계에는 이 값만 산입한다.
     * @throws IllegalArgumentException 수납 반영 금액이 0 이하이거나 받은 금액보다 클 때, 수납자 번호가 비었을 때
     * @throws PaymentAmountExceededException 결제 합계가 본인부담 합계를 넘을 때(A-20)
     * @throws InvoiceAlreadyPaidException 그 산정으로 만든 수납 건이 이미 끝났을 때(A-22)
     * @throws InvoiceNotFoundException 산정이 없을 때
     * @throws CounterNotFoundException 창구가 없을 때
     */
    fun receiveCash(invoiceId: Long, counterId: Long, cashierNo: String, received: Money, applied: Money): CashResult
}
