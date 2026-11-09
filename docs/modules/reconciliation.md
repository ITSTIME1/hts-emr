# reconciliation

상태: 초안 · 갱신: 2026-11-09

## 1. 책임 (Responsibility)
- 창구별 일 마감을 한다: 집계, 현금 대조, 카드 대조, 미결 확인, 차이 내역(F-07).
- 사후 검출로 정한 불변식을 점검한다: INV-08, INV-09, INV-11, INV-14, INV-15.
- 일 마감의 상태 전이표는 이 모듈을 개발할 때 쓴다([상태 전이표 대상 목록](../domain/03-state-transitions.md#대상-목록)).

## 2. 소유 대상 (Ownership)
- [일 마감](../domain/02-glossary.md#일-마감) `DailyClosing`
- 대조 결과: [차이](../domain/02-glossary.md#차이) `Discrepancy`, [시차](../domain/02-glossary.md#시차) `TimingGap`, [짝 없는 승인](../domain/02-glossary.md#짝-없는-승인) `UnmatchedApproval`의 내역
- 마감 소속의 값은 `payment`가 갖고, 이 모듈이 정한다.

## 3. 공개 계약 (Public Contract)

| 종류 | 이름 | 입력 | 결과 | 보장 수준 |
| :--- | :--- | :--- | :--- | :--- |
| 조회 | `ClosingQueryService.closing` | 창구, 일자 | 마감 결과와 대조 내역 | 끝난 마감은 바뀌지 않는다(A-12). |

- 발행하는 이벤트: 일 마감 상태표를 쓸 때 정한다.

## 4. 모듈 의존성 (Dependencies)
- `payment`: 마감 대상 조회, 마감 소속 지정, 미수납 기록, 대조 결과 반영
- `order`: 그날 선시행된 항목 확인(미결 확인, INV-14), 자동 환불된 항목의 시행 기록 확인(INV-15)
- `master`: 창구
- 외부: 결제 대행사의 승인 내역(모의)

## 5. 명시적 비책임 (Non-Goals)
- 승인 취소·부분 취소의 실행. 대조 결과를 `payment`에 넘기면 `payment`가 처리한다.
- 결손 처리(범위 밖)

## 6. 관련 규칙
- 흐름: F-07
- 가정: A-12, A-13, A-26
- 불변식: INV-08, INV-09, INV-11, INV-12, INV-14, INV-15
