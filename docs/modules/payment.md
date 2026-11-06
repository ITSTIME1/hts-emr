# payment

상태: 초안 · 갱신: 2026-11-06

## 1. 책임 (Responsibility)
- 산정 대상(수납하지 않은 항목)을 고르고 `billing`에 산정을 요청한다.
- 수납 건, 결제, 환불, 환불 몫의 생명주기를 관리한다([상태 전이표](../domain/03-state-transitions.md)).
- 결제 대행사와의 승인·망취소·승인 취소·부분 취소를 처리한다. 결제 대행사는 모의로 둔다.
- 현재 수납액(A-19), 미수납, 환불 대기, 영수증을 관리한다.
- 수납 건과 환불 몫의 마감 소속 값을 갖는다. 값은 `reconciliation`이 정한다.

## 2. 소유 대상 (Ownership)
- [수납 건](../domain/02-glossary.md#수납-건) `Payment`, [차액 수납 건](../domain/02-glossary.md#차액-수납-건) `AdjustmentPayment`
- [결제](../domain/02-glossary.md#결제) `PaymentLine`
- [환불](../domain/02-glossary.md#환불) `Refund`, [환불 몫](../domain/02-glossary.md#환불-몫) `RefundLine`
- [미수납](../domain/02-glossary.md#미수납) `Receivable`, [영수증](../domain/02-glossary.md#영수증) `Receipt`
- [마감 소속](../domain/02-glossary.md#마감-소속) `closing`(값)

## 3. 공개 계약 (Public Contract)

| 종류 | 이름 | 입력 | 결과 | 보장 수준 |
| :--- | :--- | :--- | :--- | :--- |
| 조회 | `Payments.outstandingOf` | 환자 | 미수납, 환불 대기 | 조회 시점의 값. 접수 화면이 쓴다(F-01). |
| 조회 | `Payments.closable` | 창구, 마감 시점 | 완료되었고 마감 소속이 없는 수납 건과 끝난 환불 몫 | 조회 시점의 값. `reconciliation`이 쓴다(A-13, A-26). |
| 조회 | `PaymentLines.ofCounter` | 창구, 기간 | 카드 결제와 승인·취소 상태 | `reconciliation`의 카드 대조에 쓴다. |
| 명령 | `Payments.assignClosing` | 마감, 수납 건·환불 몫들 | — | 아직 마감 소속이 없는 것만 받아들인다. 끝난 마감의 소속은 바뀌지 않는다(A-12). |
| 명령 | `Receivables.record` | 내원 건, 선시행 항목, 사유 | 미수납 | 같은 항목의 미수납은 한 번만 기록된다(F-06.a). |
| 명령 | `Payments.applyReconciliation` | 대조 결과(승인 없음, 짝 없는 승인, 취소 확인, 부분 취소 확인) | — | T-PLN-06, T-PLN-09 ~ T-PLN-11, T-RFL-04의 전이로 반영한다. |
| 받는 이벤트 | `EligibilityRetroactivelyChanged` | (`reception`) | — | 수납 건마다 재산정하고 환불 또는 차액 수납을 시작한다(F-04.d). |
| 받는 이벤트 | `OrderItemCancellationRequested` | (`order`) | — | 환불액을 정하고 환불을 시작한다(T-RFD-01). |
| 받는 이벤트 | `OrderItemCancelled`, `OrderItemExpired` | (`order`) | — | 그 항목을 포함한 진행 중인 수납을 거부한다(T-PAY-04). |
| 내보내는 사실 | 수납 완료, 환불 완료 | 오더 항목들 | — | `order`에 전달한다. `order`가 `payment`에 코드 의존을 갖지 않는 방식이어야 한다. 미정(ADR-0002) |

## 4. 모듈 의존성 (Dependencies)
- `order`: 산정 대상이 될 항목과 상태, 수납 완료 직전의 항목 효력 확인(T-PAY-03, INV-04). 이벤트 수신.
- `billing`: 산정, 재산정
- `reception`: 내원 건, 자격 소급 변경 이벤트 수신
- `master`: 창구
- 외부: 결제 대행사(모의)

## 5. 명시적 비책임 (Non-Goals)
- 시행 여부 판단과 오더 항목의 상태 변경(`order`)
- 금액 계산 규칙(`billing`)
- 마감 확정, 대조 판단(`reconciliation`)
- 환불 대기와 새 수납의 상계(범위 밖)

## 6. 관련 규칙
- 전이: T-PAY-01 ~ T-PAY-06, T-PLN-01 ~ T-PLN-13, T-RFD-01 ~ T-RFD-02, T-RFL-01 ~ T-RFL-08
- 불변식: INV-03 ~ INV-13
- ADR: ADR-0002, ADR-0003
