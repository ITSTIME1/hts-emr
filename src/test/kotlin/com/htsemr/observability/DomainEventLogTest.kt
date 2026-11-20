package com.htsemr.observability

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionSynchronizationUtils

internal class DomainEventLogTest {

    private val appender = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger("domain.event") as Logger

    @BeforeEach
    fun setUp() {
        MDC.clear()
        logger.level = Level.INFO
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(appender)
        appender.stop()
        MDC.clear()
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    private fun ILoggingEvent.fields(): Map<String, Any?> = keyValuePairs.associate { it.key to it.value }

    @Test
    fun `트랜잭션이 없으면 바로 한 줄을 남긴다`() {
        DomainEventLog.emit("payment.completed", "paymentId" to 3307L, "invoiceId" to 9021L)

        val line = appender.list.single()
        assertThat(line.formattedMessage).isEqualTo("payment.completed")
        assertThat(line.level).isEqualTo(Level.INFO)
        assertThat(line.fields()).containsEntry("paymentId", "3307").containsEntry("invoiceId", "9021")
    }

    @Test
    fun `업무 키 필드는 MDC와 같게 문자열로, 그 밖의 숫자는 숫자로 남긴다`() {
        DomainEventLog.emit("payment.cashReceived", "paymentId" to 3307L, "applied" to 20000L)

        val fields = appender.list.single().fields()
        assertThat(fields["paymentId"]).isEqualTo("3307")
        assertThat(fields["applied"]).isEqualTo(20000L)
    }

    @Test
    fun `트랜잭션 안에서는 커밋 전에는 남기지 않고 커밋 뒤에 남긴다`() {
        TransactionSynchronizationManager.initSynchronization()

        DomainEventLog.emit("payment.completed", "paymentId" to 3307L)
        assertThat(appender.list).isEmpty()

        TransactionSynchronizationUtils.triggerAfterCommit()
        assertThat(appender.list.single().formattedMessage).isEqualTo("payment.completed")
    }

    @Test
    fun `롤백된 트랜잭션의 이벤트는 남기지 않는다`() {
        TransactionSynchronizationManager.initSynchronization()

        DomainEventLog.emit("payment.completed", "paymentId" to 3307L)
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)

        assertThat(appender.list).isEmpty()
    }

    @Test
    fun `값이 null인 필드는 싣지 않는다`() {
        DomainEventLog.emit("order.placed", "orderId" to 5L, "doctorNo" to null)

        assertThat(appender.list.single().fields().keys).containsExactly("orderId")
    }

    @Test
    fun `MDC가 이미 가진 키는 다시 싣지 않는다`() {
        MDC.put("visitId", "57")

        DomainEventLog.emit("invoice.calculated", "visitId" to 57L, "invoiceId" to 9021L)

        assertThat(appender.list.single().fields().keys).containsExactly("invoiceId")
    }
}
