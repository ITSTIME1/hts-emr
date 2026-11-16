package com.htsemr.master

import com.htsemr.TestcontainersConfiguration
import com.htsemr.master.entity.Counter
import com.htsemr.master.repository.CounterRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.modulith.test.ApplicationModuleTest
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.java

@ApplicationModuleTest
@Import(TestcontainersConfiguration::class)
@Transactional
internal class CounterQueryServiceTest @Autowired constructor(
    private val counterRepository: CounterRepository,
    private val counterQueryService: CounterQueryService,
) {

    private lateinit var counter: Counter

    @BeforeEach
    fun setUp() {
        counter = counterRepository.save(Counter(code = "T-C01", name = "테스트 창구"))
    }

    @Nested
    inner class `counter는` {

        @Test
        fun `창구가 있으면 그 창구의 정보를 돌려준다`() {
            val view = counterQueryService.counter(counter.id!!)

            assertThat(view).isInstanceOf(CounterView::class.java)

            assertThat(view).isEqualTo(CounterView(id = counter.id!!, code = "T-C01", name = "테스트 창구"))
        }

        @Test
        fun `창구가 없으면 예외가 발생한다`() {
            val missingId = -1L

            val exception = assertThrows<CounterNotFoundException> {
                counterQueryService.counter(missingId)
            }
            assertThat(exception.counterId).isEqualTo(missingId)
        }
    }

    @Nested
    inner class `창구 저장은` {

        @Test
        fun `같은 코드의 창구를 두 번 넣을 수 없다`() {
            assertThrows<DataIntegrityViolationException> {
                counterRepository.saveAndFlush(Counter(code = "T-C01", name = "중복 창구"))
            }
        }
    }
}
