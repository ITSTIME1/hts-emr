package com.htsemr.payment

/** 결제 수단. 슬라이스 1은 현금만 다룬다(카드는 다음 슬라이스). */
enum class PaymentMethod { CASH }

/** 결제 상태(상태 전이표 PaymentLine). 현금은 받음(RECEIVED)과 돌려줌(RETURNED)뿐이다. */
enum class PaymentLineStatus { RECEIVED, RETURNED }
