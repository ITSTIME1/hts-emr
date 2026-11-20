package com.htsemr.observability

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.ReadinessState
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextClosedEvent
import java.time.Duration

internal class LifecycleLogTest {

    private val appender = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger("app.lifecycle") as Logger
    private val lifecycleLog = LifecycleLog()

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
    fun `기동이 끝나면 app started와 걸린 시간을 남긴다`() {
        val event = ApplicationReadyEvent(
            mock(SpringApplication::class.java),
            arrayOf(),
            mock(ConfigurableApplicationContext::class.java),
            Duration.ofMillis(4321),
        )

        lifecycleLog.onReady(event)

        val line = appender.list.single()
        assertThat(line.formattedMessage).isEqualTo("app.started")
        assertThat(line.fields()).containsEntry("startupMs", 4321L)
    }

    @Test
    fun `준비 상태가 바뀔 때마다 상태를 남긴다`() {
        lifecycleLog.onReadinessChange(AvailabilityChangeEvent(this, ReadinessState.ACCEPTING_TRAFFIC))
        lifecycleLog.onReadinessChange(AvailabilityChangeEvent(this, ReadinessState.REFUSING_TRAFFIC))

        assertThat(appender.list.map { it.formattedMessage }).containsExactly("app.readiness", "app.readiness")
        assertThat(appender.list.map { it.fields()["state"] })
            .containsExactly("ACCEPTING_TRAFFIC", "REFUSING_TRAFFIC")
    }

    @Test
    fun `종료가 시작되면 app shuttingDown을 남긴다`() {
        lifecycleLog.onClosing(ContextClosedEvent(mock(ApplicationContext::class.java)))

        assertThat(appender.list.single().formattedMessage).isEqualTo("app.shuttingDown")
    }
}
