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

    /**
     * 테스트마다 창구 하나를 넣는다.
     * 예시 데이터(C01)와 겹치지 않도록 테스트 전용 코드(T-)를 쓴다.
     * @Transactional이므로 테스트가 끝나면 롤백되어 다음 테스트에 남지 않는다.
     */
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

            // 엔티티가 아니라 공개 값 객체로, 저장한 값 그대로 나와야 한다.
            assertThat(view).isEqualTo(CounterView(id = counter.id!!, code = "T-C01", name = "테스트 창구"))
        }

        @Test
        fun `창구가 없으면 예외가 발생한다`() {
            // IDENTITY는 1부터 증가하므로 음수 id는 존재할 수 없다.
            val missingId = -1L

            val exception = assertThrows<CounterNotFoundException> {
                counterQueryService.counter(missingId)
            }
            // 어떤 창구를 찾지 못했는지 예외가 알려 줘야 한다.
            assertThat(exception.counterId).isEqualTo(missingId)
        }
    }

    @Nested
    inner class `창구 저장은` {

        @Test
        fun `같은 코드의 창구를 두 번 넣을 수 없다`() {
            // 수납 건과 마감이 창구 코드로도 구분되므로, 코드는 유일해야 한다(uk_counter_code).
            assertThrows<DataIntegrityViolationException> {
                counterRepository.saveAndFlush(Counter(code = "T-C01", name = "중복 창구"))
            }
        }
    }
}
