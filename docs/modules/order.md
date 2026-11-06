# order

상태: 초안 · 갱신: 2026-11-06

## 1. 책임 (Responsibility)
- 오더 항목의 생명주기를 관리한다: 오더, 시행, 오더 취소, 취소 요청, 취소 확정, 소멸([상태 전이표](../domain/03-state-transitions.md#오더-항목-orderitem)).
- 시행 기록과 시행 근거(수납, 선시행)를 남긴다.
- 진료일이 끝나면 미시행 항목을 소멸시키거나 취소를 요청한다(F-06.b).
- **수납 여부는 기록하지 않는다.** 수납 여부는 `payment`의 사실이다(03 문서 오더 항목 0절).

## 2. 소유 대상 (Ownership)
- [오더 항목](../domain/02-glossary.md#오더-항목) `OrderItem`(선시행 승인 `executableBeforePayment` 포함)
- 시행 기록, [오더 취소](../domain/02-glossary.md#오더-취소), [취소 요청](../domain/02-glossary.md#취소-요청), [오더 소멸](../domain/02-glossary.md#오더-소멸)
- [원외처방](../domain/02-glossary.md#원외처방) `OutpatientPrescription`

## 3. 공개 계약 (Public Contract)

| 종류 | 이름 | 입력 | 결과 | 보장 수준 |
| :--- | :--- | :--- | :--- | :--- |
| 조회 | `OrderItems.ofVisit` | 내원 건 | 오더 항목과 상태 | 조회 시점의 상태. `payment`가 산정 대상을 고를 때 쓴다. |
| 조회 | `OrderItems.effectiveness` | 오더 항목들 | 항목별 상태 | 조회 시점의 상태. `payment`가 수납 완료 직전에 쓴다(T-PAY-03). 완료 판단과 어떻게 묶일지는 미정(ADR-0003) |
| 이벤트 | `OrderItemCancellationRequested` | 오더 항목, 요청자(의사, 시스템), 사유 | — | 취소 요청이 확정된 뒤 발행, 유실되지 않음. 받는 쪽: `payment`(T-RFD-01) |
| 이벤트 | `OrderItemCancelled`, `OrderItemExpired` | 오더 항목 | — | 상태가 확정된 뒤 발행. 받는 쪽: `payment`(진행 중 수납 거부, F-04.c) |
| 받는 사실 | 수납 완료, 환불 완료 | 오더 항목들 | — | `payment`에서 받는다. `order`는 `payment`에 코드 의존을 갖지 않는다. 방식은 미정(ADR-0002) |

## 4. 모듈 의존성 (Dependencies)
- `reception`: 오더 항목이 접수된 내원 건에 속하는지 확인한다(T-ORD-01). 진료일을 안다(F-06.b).
- `master`: 수가 항목에 진료일에 유효한 수가가 있는지, 선시행 기본값이 무엇인지 확인한다(T-ORD-01, A-08).

## 5. 명시적 비책임 (Non-Goals)
- 수납 여부, 금액, 환불, 미수납(`payment`)
- 시행 후 예외 환불. 오더 항목은 `EXECUTED` 그대로다(`payment`, F-06.c).
- 잘못 기록한 시행의 되돌림(범위 밖)

## 6. 관련 규칙
- 전이: T-ORD-01 ~ T-ORD-08
- 불변식: INV-01, INV-02, INV-13, INV-14, INV-15
- ADR: ADR-0002, ADR-0003
