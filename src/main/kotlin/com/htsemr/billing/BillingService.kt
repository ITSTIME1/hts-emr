package com.htsemr.billing

import com.htsemr.master.FeeItemNotFoundException
import com.htsemr.master.FeePriceNotFoundException
import com.htsemr.reception.VisitNotFoundException

interface BillingService {

    fun calculate(visitId: Long, targets: List<BillingTarget>): Long
}
