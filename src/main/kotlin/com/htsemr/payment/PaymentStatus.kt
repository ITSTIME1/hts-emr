package com.htsemr.payment

/**
 * 수납 건의 상태(상태 전이표 Payment). 슬라이스 1은 IN_PROGRESS, COMPLETED만 쓴다.
 * 다른 모듈이 수납 건 조회 결과로 받으므로 공개한다.
 */
enum class PaymentStatus { IN_PROGRESS, COMPLETED, REJECTED, HELD }
