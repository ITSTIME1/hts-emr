package com.htsemr.master

import com.htsemr.TestcontainersConfiguration
import com.htsemr.common.Money
import com.htsemr.master.entity.FeeItem
import com.htsemr.master.entity.FeePrice
import com.htsemr.master.repository.FeeItemRepository
import com.htsemr.master.repository.FeePriceRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.modulith.test.ApplicationModuleTest
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@ApplicationModuleTest
@Import(TestcontainersConfiguration::class)
@Transactional
internal class FeeQueryServiceTest @Autowired constructor(

    private val feeItemRepository : FeeItemRepository,
    private val feePriceRepository : FeePriceRepository,
    private val feeQueryService : FeeQueryService
) {

    private lateinit var ct: FeeItem

    @BeforeEach
    fun setUpDummyData() {

        val testFeeItem = FeeItem(
            code = "T-CT",
            name = "테스트 CT",
            coverageType = CoverageType.COVERED,
            executableBeforePayment = false
        )

        ct = feeItemRepository.save(testFeeItem)

        feePriceRepository.save(FeePrice(
            ct.id!!,
            LocalDate.of(2026, 1, 1),
            Money(120_000)))

        feePriceRepository.save(FeePrice(
            ct.id!!,
            LocalDate.of(2026, 7, 1),
            Money(125_000)))
    }

    @Nested
    inner class `priceOn은` {

        @Test
        fun `진료일 이전 단가 가운데 가장 최근 것을 돌려준다` () {

            assertThat(feeQueryService.priceOn(feeItemId = ct.id!!, LocalDate.of(2026, 6,30)).amount).isEqualTo(120_000)

            assertThat(feeQueryService.priceOn(feeItemId = ct.id!!, LocalDate.of(2026, 7, 26)).amount).isEqualTo(125_000)
        }

        @Test
        fun `진료일 이전 단가가 존재하지 않으면 예외가 발생한다`() {
            assertThrows<FeePriceNotFoundException> {
                feeQueryService.priceOn(feeItemId = ct.id!!, LocalDate.of(2025, 12,26))
            }
        }
    }

    @Nested
    inner class `hasPriceOn은` {
        @Test
        fun `특정 진료일에 대해서 유효한 단가가 존재한다`() {
            assertTrue(feeQueryService.hasPriceOn(feeItemId = ct.id!!, visitDate = LocalDate.of(2026, 6, 1)))
        }
        @Test
        fun `특정 진료일에 대해서 유효한 단가가 존재하지 않는다`() {
            assertFalse(feeQueryService.hasPriceOn(feeItemId = ct.id!!, visitDate = LocalDate.of(2025, 3, 1)))
        }
    }

    @Nested
    inner class `feeItem은` {
        @Test
        fun `특정 수가가 존재한다`() {
            val feeItemView = feeQueryService.feeItem(ct.id!!)
            assertThat(feeItemView).isInstanceOf(FeeItemView::class.java)

            assertThat(feeItemView.id).isEqualTo(ct.id!!)
            assertThat(feeItemView.code).isEqualTo("T-CT")
            assertThat(feeItemView.name).isEqualTo("테스트 CT")
            assertThat(feeItemView.coverageType).isEqualTo(CoverageType.COVERED)
            assertFalse(feeItemView.executableBeforePayment)
        }

        @Test
        fun `특정 수가가 존재하지 않는다`() {
            assertThrows<FeeItemNotFoundException> {
                feeQueryService.feeItem(ct.id!! + 1)
            }
        }
    }

    @Nested
    inner class `단가 저장은` {
        @Test
        fun `같은 수가 항목, 같은 적용 시작일의 단가를 두 번 넣을 수 없다`() {
            assertThrows<DataIntegrityViolationException> {
                feePriceRepository.saveAndFlush(FeePrice(ct.id!!, LocalDate.of(2026, 7, 1), Money(130_000)))
            }
        }
    }

}
