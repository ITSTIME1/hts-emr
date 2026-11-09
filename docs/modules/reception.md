# reception

상태: 초안 · 갱신: 2026-11-09

## 1. 책임 (Responsibility)
- 접수로 내원 건을 만든다(F-01). 환자, 진료과, 진료 의사는 다른 시스템에서 받은 값으로만 가진다(A-29).
- 접수 때 외부 기관에 자격을 조회해 내원 건의 자격 기록으로 남긴다(A-03, A-04). 외부 기관은 모의로 둔다.
  - 외부 기관 조회는 reception 내부의 `EligibilityProvider` 뒤에 두고, 모의 구현이 응답한다. 조회 실패나 지연 같은 시나리오는 모의 구현을 바꿔 재현한다.
- 자격 기록은 추가만 하고 고치거나 지우지 않는다(A-03).
- 본인부담률은 정수 퍼센트(0~100)로 저장한다. 일반 자격은 100으로 고정하고 DB 제약으로 강제한다. billing이 자격 유형을 분기하지 않고 비율만 적용하게 하려는 것이다.
- 자격 소급 변경을 확인해 새 자격 기록을 추가하고, 그 사실을 알린다(F-04.d, A-17).

## 2. 소유 대상 (Ownership)
- [내원 건](../domain/02-glossary.md#내원-건) `Visit`, [진료일](../domain/02-glossary.md#진료일) `VisitDate`
- [자격](../domain/02-glossary.md#자격) `Eligibility`(내원 건의 자격 기록), [본인부담률](../domain/02-glossary.md#본인부담률)
- [자격 소급 변경](../domain/02-glossary.md#자격-소급-변경) `RetroactiveEligibilityChange`

## 3. 공개 계약 (Public Contract)

| 종류 | 이름 | 입력 | 결과 | 보장 수준 |
| :--- | :--- | :--- | :--- | :--- |
| 변경 | `RegistrationService.registerVisit` | 환자 번호, 진료과 코드, 진료 의사 번호, 진료일 | 내원 건 id | 외부 조회는 트랜잭션 밖에서 먼저 하고, 내원 건과 첫 자격 기록은 함께 저장되거나 함께 저장되지 않는다. 같은 환자·진료일·진료과면 `DuplicateVisitException`. 화면이 호출하지만, 여러 모듈에 걸친 재현 테스트가 실제 흐름대로 내원 건을 만들 수 있도록 공개한다 |
| 조회 | `VisitQueryService.visit` | 내원 건 | 환자, 진료과, 진료 의사, 진료일 | 조회 시점의 값 |
| 조회 | `EligibilityQueryService.currentEligibility` | 내원 건 | 가장 나중의 자격 기록 | 기존 기록은 고치지 않으므로, 이전에 받은 결과는 그때의 기록으로 남아 있다(A-03) |
| 이벤트 | `EligibilityRetroactivelyChanged` | 내원 건, 이전 자격, 새 자격 | — | 새 자격 기록이 확정된 뒤에만 발행되고 유실되지 않는다. 받는 쪽: `payment` |

## 4. 모듈 의존성 (Dependencies)
- 없음

## 5. 명시적 비책임 (Non-Goals)
- 접수 때 알리는 미수납·환불 대기의 내용. 접수 화면이 `payment`의 공개 조회를 함께 사용한다. `reception`이 `payment`를 부르면 순환한다.
- 자격 변경에 따른 재산정과 환불(`payment`, `billing`)
- 예약(범위 밖)

## 6. 관련 규칙
- 흐름: F-01, F-04.d
- 가정: A-03, A-04, A-15, A-17, A-29
