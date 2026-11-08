package com.htsemr.master

/**
 * 급여 구분. 수가 항목이 건강보험 적용 대상(급여)인지 아닌지(비급여)를 나눈다.
 * 일반 자격 환자는 급여 항목도 전액 본인부담이다(F-03).
 *
 * billing이 급여 구분을 받아야 하므로 공개 패키지에 둔다.
 */
enum class CoverageType { COVERED, NON_COVERED }