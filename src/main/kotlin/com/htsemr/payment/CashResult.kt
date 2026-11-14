package com.htsemr.payment

import com.htsemr.common.Money

/**
 * 현금 결제(receiveCash)의 결과. 세 경우 가운데 하나다.
 *
 * sealed라서 부르는 쪽의 when이 세 경우를 모두 다뤄야 컴파일된다. 경우가 늘면 처리하지 않은 곳이 컴파일 오류로 드러난다.
 *
 * [완료가 막힌 경우를 예외가 아니라 결과로 알리는 이유]
 * 예외를 던지면 트랜잭션이 롤백되어, 같은 호출에서 받은 현금 기록까지 사라진다(payment.md 7절).
 */
sealed interface CashResult {
    val paymentId: Long

    /** 결제를 받았고 아직 남은 금액이 있다. */
    data class InProgress(override val paymentId: Long, val remaining: Money) : CashResult

    /** 결제 합계가 본인부담 합계에 도달해 수납을 완료했다(T-PAY-03). */
    data class Completed(override val paymentId: Long) : CashResult

    /**
     * 결제 합계는 도달했지만, 포함한 오더 항목 중 효력이 없는 것이 있어 완료하지 않았다(INV-04).
     * 수납 건은 진행 중으로 남고 받은 결제도 그대로다. 수납 거부와 결제 되돌림은 다음 슬라이스에서 만든다.
     */
    data class CompletionBlocked(override val paymentId: Long, val ineffectiveItemIds: Set<Long>) : CashResult
}
