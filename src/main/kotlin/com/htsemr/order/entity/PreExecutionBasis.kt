package com.htsemr.order.entity

/** 선시행 승인의 근거. order 안에서만 쓴다. */
internal enum class PreExecutionBasis {
    /** 수가 항목 기본값(A-08). 의사 지정보다 우선한다. */
    FEE_ITEM_DEFAULT,

    /** 의사가 직접 지정했다(A-01). */
    DOCTOR,
}
