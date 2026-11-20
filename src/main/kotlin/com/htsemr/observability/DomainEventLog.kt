package com.htsemr.observability

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

object DomainEventLog {

    private val log = LoggerFactory.getLogger("domain.event")

    fun emit(event: String, vararg fields: Pair<String, Any?>) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = write(event, fields)
            })
        } else {
            write(event, fields)
        }
    }

    private fun write(event: String, fields: Array<out Pair<String, Any?>>) {
        var builder = log.atInfo()
        fields.forEach { (key, value) ->
            if (value != null && MDC.get(key) == null) {
                builder = builder.addKeyValue(key, if (key in BusinessKeys.KEYS) value.toString() else value)
            }
        }
        builder.log(event)
    }
}
