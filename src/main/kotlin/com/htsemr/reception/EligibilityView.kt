package com.htsemr.reception

import java.time.LocalDateTime

/**
 * 내원 건의 자격 기록 하나. reception 모듈 밖으로 내보내는 값 객체다.
 *
 * 순번(seq)을 함께 내보낸다. billing이 어느 자격 기록으로 산정했는지 남겨야,
 * 자격이 소급 변경되었을 때 다시 산정할 대상을 고를 수 있다(F-04.d).
 */
data class EligibilityView(
    val visitId: Long,
    val seq: Int,
    val insuranceType: InsuranceType,
    /** 본인부담률. 정수 퍼센트(0~100). 일반 자격은 100이다. */
    val copayRate: Int,
    val recordedAt: LocalDateTime,
)
