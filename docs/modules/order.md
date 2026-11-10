# order

상태: 초안 · 갱신: 2026-11-10

## 1. 책임 (Responsibility)
- 오더 항목의 생명주기를 관리한다: 오더, 시행, 오더 취소, 취소 요청, 취소 확정, 소멸([상태 전이표](../domain/03-state-transitions.md#오더-항목-orderitem)).
- 한 번에 낸 처방을 오더(`MedicalOrder`)로 남기고, 그 아래에 오더 항목을 둔다. 오더는 상태가 없고 바뀌지 않으며, 상태 전이는 모두 오더 항목 단위다.
- 오더 항목의 상태는 수량 전체에 한 번에 적용한다. 부분 시행·부분 취소는 없고, 수량을 바꾸려면 취소한 뒤 새로 발행한다.
- 선시행 승인은 발행할 때 정해 오더 항목에 남긴다. 의사가 지정했거나 수가 항목 기본값이 참이면 승인이며, 근거(`DOCTOR`, `FEE_ITEM_DEFAULT`)를 함께 남긴다(A-01, A-08).
- 지금 상태는 오더 항목에 두고, 상태를 바꾼 기록(오더 취소 등)은 따로 추가만 한다. 상태는 오더 항목의 메서드로만 바꾼다.
- 시행 기록과 시행 근거(수납, 선시행)를 남긴다.
- 진료일이 끝나면 미시행 항목을 소멸시키거나 취소를 요청한다(F-06.b).
- **수납 여부는 기록하지 않는다.** 수납 여부는 `payment`의 사실이다(03 문서 오더 항목 0절).

## 2. 소유 대상 (Ownership)
- [오더](../domain/02-glossary.md#오더) `MedicalOrder`(오더 의사, 오더 시각)
- [오더 항목](../domain/02-glossary.md#오더-항목) `OrderItem`(수량, 상태, 선시행 승인 `executableBeforePayment`와 그 근거)
- 시행 기록, [오더 취소](../domain/02-glossary.md#오더-취소), [취소 요청](../domain/02-glossary.md#취소-요청), [오더 소멸](../domain/02-glossary.md#오더-소멸)
- [원외처방](../domain/02-glossary.md#원외처방) `OutpatientPrescription`

## 3. 공개 계약 (Public Contract)

| 종류 | 이름 | 입력 | 결과 | 보장 수준 |
| :--- | :--- | :--- | :--- | :--- |
| 변경 | `OrderService.placeOrder` | 내원 건, 오더 의사, 항목 목록(`NewOrderItem`: 수가 항목, 수량, 의사의 선시행 지정) | 오더 id | 오더와 항목은 함께 저장되거나 함께 저장되지 않는다. 내원 건이 없으면 `VisitNotFoundException`(reception), 수가 항목이 없으면 `FeeItemNotFoundException`(master), 진료일에 유효한 수가가 없으면 `NoPriceOnVisitDateException`(T-ORD-01) |
| 변경 | `OrderService.cancelOrderItem` | 오더 항목, 취소 의사, 사유 | — | `ORDERED`일 때만 `CANCELLED`로 바꾸고 오더 취소 기록을 남긴다(T-ORD-04). 슬라이스 1에서는 수납 건의 사실을 확인하지 않는다(7절) |
| 조회 | `OrderItemQueryService.itemsOfVisit` | 내원 건 | 오더 항목과 상태(`OrderItemView`) | 조회 시점의 상태. 없으면 빈 목록. `billing`이 산정 대상을 고를 때 쓴다. |
| 조회 | `OrderItemQueryService.statusesOf` | 오더 항목들 | 항목별 상태(`OrderItemStatus`) | 조회 시점의 상태. 효력이 있는지는 `OrderItemStatus.isEffective`(`ORDERED`, `EXECUTED`, INV-04)로 판단한다. `payment`가 수납 완료 직전에 쓴다(T-PAY-03). 완료 판단과 어떻게 묶일지는 미정(ADR-0003) |
| 이벤트 | `OrderItemCancellationRequested` | 오더 항목, 요청자(의사, 시스템), 사유 | — | 취소 요청이 확정된 뒤 발행, 유실되지 않음. 받는 쪽: `payment`(T-RFD-01) |
| 이벤트 | `OrderItemCancelled`, `OrderItemExpired` | 오더 항목 | — | 상태가 확정된 뒤 발행. 받는 쪽: `payment`(진행 중 수납 거부, F-04.c) |
| 받는 사실 | 수납 완료, 환불 완료 | 오더 항목들 | — | `payment`에서 받는다. `order`는 `payment`에 코드 의존을 갖지 않는다. 방식은 미정(ADR-0002) |

- `OrderItemView`는 오더 항목 id, 오더 id, 내원 건 id, 수가 항목 id, 수량, 상태, 선시행 승인을 담는다. 내원 건 id는 오더에 저장된 값을 함께 보여 주는 것이며, 오더 항목에 따로 저장하지 않는다.
- "효력이 있다"의 정의는 `order`가 가진다. `payment`는 상태를 받아 거부 사유(취소, 소멸)까지 알 수 있다.

## 4. 모듈 의존성 (Dependencies)
- `reception`: 오더를 낼 내원 건이 있는지와 진료일을 확인한다(`VisitQueryService.visit`, T-ORD-01, F-06.b).
- `master`: 수가 항목의 선시행 기본값을 읽고(`FeeQueryService.feeItem`, A-08), 진료일에 유효한 수가가 있는지 확인한다(`FeeQueryService.hasPriceOn`, T-ORD-01).

## 5. 명시적 비책임 (Non-Goals)
- 수납 여부, 금액, 환불, 미수납(`payment`)
- 시행 후 예외 환불. 오더 항목은 `EXECUTED` 그대로다(`payment`, F-06.c).
- 잘못 기록한 시행의 되돌림(범위 밖)
- 실제 EMR의 처방 화면 단위 기능(처방 세트, 처방전 출력 양식 등)

## 6. 관련 규칙
- 전이: T-ORD-01 ~ T-ORD-08
- 불변식: INV-01, INV-02, INV-13, INV-14, INV-15
- ADR: ADR-0002, ADR-0003

## 7. 슬라이스 1 범위와 공백
슬라이스 1은 이중 수납 방어(C-03, C-04, C-06)를 재현하는 데 필요한 부분만 만든다.

| 구분 | 내용 |
| :--- | :--- |
| 만든다 | 오더 발행(T-ORD-01), 수납 전 오더 취소(T-ORD-04), `itemsOfVisit`, `statusesOf` |
| 미룬다 | 시행(T-ORD-02, T-ORD-03), 취소 요청(T-ORD-05), 진료일 경과(T-ORD-06, T-ORD-07), 취소 확정(T-ORD-08), 이벤트 발행, 원외처방. 모두 수납 건의 사실이 필요하거나(ADR-0002) 재현에 필요 없다. |

알고 남기는 공백:
- **진찰료가 `ORDERED`로 남는다.** 원래 진찰료는 발행과 함께 시행된다(T-ORD-03, A-08). 시행을 만들기 전까지는 진찰료도 수납 전 오더 취소가 가능하다. 재현 테스트는 검사 항목으로 구성한다.
- **T-ORD-04는 수납 건의 사실을 확인하지 않는다.** 상태 조건(`ORDERED`)만 본다. C-04(수납 완료와 오더 취소가 동시에 일어남)를 먼저 재현한 뒤, ADR-0003에서 방어를 정해 넣는다.
- **동시성 방어가 없다.** 같은 오더 항목을 동시에 바꾸는 경우를 무엇으로 막을지(버전, 조건부 갱신, 잠금)는 ADR-0003에서 정한다.
