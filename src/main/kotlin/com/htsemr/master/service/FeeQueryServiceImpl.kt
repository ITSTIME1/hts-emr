package com.htsemr.master.service

import com.htsemr.common.Money
import com.htsemr.master.FeeItemNotFoundException
import com.htsemr.master.FeeItemView
import com.htsemr.master.FeePriceNotFoundException
import com.htsemr.master.FeeQueryService
import com.htsemr.master.mapping.toFeeItemView
import com.htsemr.master.repository.FeeItemRepository
import com.htsemr.master.repository.FeePriceRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
internal class FeeQueryServiceImpl(
    private val feeItemRepository : FeeItemRepository,
    private val feePriceRepository : FeePriceRepository,
)  : FeeQueryService {

    override fun feeItem(feeItemId: Long): FeeItemView {
        val feeItemEntity = feeItemRepository.findByIdOrNull(feeItemId)
            ?: throw FeeItemNotFoundException(feeItemId)
        return feeItemEntity.toFeeItemView()
    }

    override fun hasPriceOn(feeItemId: Long, visitDate: LocalDate): Boolean {
        return feePriceRepository.findEffectiveOn(feeItemId = feeItemId, visitDate = visitDate) != null
    }

    override fun priceOn(feeItemId: Long, visitDate: LocalDate): Money {
        return feePriceRepository.findEffectiveOn(feeItemId, visitDate)?.amount
            ?: throw FeePriceNotFoundException(feeItemId, visitDate)
    }
}
