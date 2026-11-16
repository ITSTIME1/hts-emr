package com.htsemr.order

import com.htsemr.master.FeeItemNotFoundException
import com.htsemr.reception.VisitNotFoundException

interface OrderService {

    fun placeOrder(visitId: Long, doctorNo: String, items: List<NewOrderItem>): Long

    fun cancelOrderItem(orderItemId: Long, doctorNo: String, reason: String)
}
