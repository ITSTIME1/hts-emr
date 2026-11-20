package com.htsemr.observability

import com.htsemr.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.ReadinessState
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
internal class LifecycleLogIntegrationTest @Autowired constructor(
    private val context: ApplicationContext,
) {

    @Test
    fun `준비 상태 변경 이벤트가 실제 컨텍스트에서 app readiness JSON 줄로 남는다`(output: CapturedOutput) {
        AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC)
        AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC)

        val lines = output.out.lineSequence()
            .filter { it.contains("\"message\":\"app.readiness\"") }
            .toList()
            .takeLast(2)
        assertThat(lines).hasSize(2)
        assertThat(lines[0]).contains("\"state\":\"REFUSING_TRAFFIC\"")
        assertThat(lines[1]).contains("\"state\":\"ACCEPTING_TRAFFIC\"")
    }
}
