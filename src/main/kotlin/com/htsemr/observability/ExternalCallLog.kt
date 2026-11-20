package com.htsemr.observability

import org.slf4j.LoggerFactory
import org.slf4j.event.Level

object ExternalCallLog {

    private val log = LoggerFactory.getLogger("external.call")

    fun <T> record(target: String, call: () -> T): T {
        val startedAt = System.nanoTime()
        try {
            val result = call()
            write(target, startedAt, null)
            return result
        } catch (t: Throwable) {
            write(target, startedAt, t)
            throw t
        }
    }

    private fun write(target: String, startedAt: Long, failure: Throwable?) {
        var builder = log.atLevel(if (failure == null) Level.INFO else Level.WARN)
            .addKeyValue("target", target)
            .addKeyValue("durationMs", (System.nanoTime() - startedAt) / NANOS_PER_MILLI)
            .addKeyValue("outcome", if (failure == null) "OK" else "FAILED")
        if (failure != null) {
            builder = builder.addKeyValue("errorType", failure.javaClass.simpleName)
        }
        builder.log("external.call")
    }

    private const val NANOS_PER_MILLI = 1_000_000L
}
