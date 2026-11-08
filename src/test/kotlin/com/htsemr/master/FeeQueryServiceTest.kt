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
    private val feeQueryService : FeeQueryService // 구현체 주입
) {

    private lateinit var ct: FeeItem

    /**
     * 테스트 전, 준비 데이터를 넣는다.
     * 수가 데이터를 넣어야 하므로, 미리 데이터를 준비한다.
     */
    @BeforeEach
    fun setUpDummyData() {

        // 수가 하나를 생성
        val testFeeItem = FeeItem(
            code = "T-CT",
            name = "테스트 CT",
            coverageType = CoverageType.COVERED,
            executableBeforePayment = false
        )

        ct = feeItemRepository.save(testFeeItem)

        // 같은 수가 ID의 가격은 N개 존재 가능하기 때문에 (단, 같은 수가ID + 적용 시작일이 모두 같을 수 없음.)
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
            /**
             * `priceOn`은 JPQL로 작성되어 있으며, nativeQuery가 적용되어 있지 않습니다.
             *  특정 수가ID와 내원일을 인수로 넘기면, 내원일 기준으로 이하 범위에서 가장 최근의 적용된 것 하나를 리턴합니다.
             *  그러므로, 테스트의 의도는 06.01 날짜의 가격과 07.01 날짜의 가격 데이터를 먼저 넣어둔 뒤,
             *
             *  06.30일에 내원했다면, 07.01은 초과 범위이므로 해당되지 않고
             *  06.01이 최근 유효한 가격이므로 12.0을 리턴하게 합니다.
             *
             *  07.026일에 내원했다면, 06.30, 07.01 모두 이하범위에 해당되지만, 가장 최근것을 리턴하는 기준이 존재하므로 07.01 가격이 리턴된다.
             *
             */


            // 2026.06.30 이전 유효한 단가중 가장 최근 단가는 12.0 이다.
            assertThat(feeQueryService.priceOn(feeItemId = ct.id!!, LocalDate.of(2026, 6,30)).amount).isEqualTo(120_000)

            // 2026.07.26 이전 유효한 단가중 가장 최근 단가는 12.5 이다.
            assertThat(feeQueryService.priceOn(feeItemId = ct.id!!, LocalDate.of(2026, 7, 26)).amount).isEqualTo(125_000)
        }

        @Test
        fun `진료일 이전 단가가 존재하지 않으면 예외가 발생한다`() {
            /**
             * 진료일 이전 단가가 존재하지 않는다는 것은, 오더에 대해 적용할 수 있는 단가가 없다는 뜻이므로, 데이터에 문제가 생겼음을 알 수 있음.
             */
            assertThrows<FeePriceNotFoundException> {
                feeQueryService.priceOn(feeItemId = ct.id!!, LocalDate.of(2025, 12,26))
            }
        }
    }

    @Nested
    inner class `hasPriceOn은` {
        // 그 진료일에 유효한 단가가 **있는지** 묻는다.
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