package com.htsemr.observability

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

internal class ExternalCallLogTest {

    private val appender = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger("external.call") as Logger

    @BeforeEach
    fun setUp() {
        logger.level = Level.INFO
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(appender)
        appender.stop()
    }

    private fun ILoggingEvent.fields(): Map<String, Any?> = keyValuePairs.associate { it.key to it.value }

    @Test
    fun `성공한 호출은 결과를 그대로 돌려주고 OK 한 줄을 남긴다`() {
        val result = ExternalCallLog.record("eligibility") { "NATIONAL_HEALTH" }

        assertThat(result).isEqualTo("NATIONAL_HEALTH")
        val line = appender.list.single()
        assertThat(line.formattedMessage).isEqualTo("external.call")
        assertThat(line.level).isEqualTo(Level.INFO)
        assertThat(line.fields()).containsEntry("target", "eligibility").containsEntry("outcome", "OK")
        assertThat((line.fields()["durationMs"] as Number).toLong()).isGreaterThanOrEqualTo(0)
        assertThat(line.fields()).doesNotContainKey("errorType")
    }

    @Test
    fun `실패한 호출은 WARN으로 FAILED와 예외 종류를 남기고 예외를 그대로 던진다`() {
        assertThatThrownBy {
            ExternalCallLog.record("eligibility") { throw IllegalStateException("timeout") }
        }.isInstanceOf(IllegalStateException::class.java).hasMessage("timeout")

        val line = appender.list.single()
        assertThat(line.level).isEqualTo(Level.WARN)
        assertThat(line.fields()).containsEntry("outcome", "FAILED").containsEntry("errorType", "IllegalStateException")
    }
}
