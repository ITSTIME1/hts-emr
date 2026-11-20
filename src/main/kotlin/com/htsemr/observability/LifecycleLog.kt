package com.htsemr.observability

import org.slf4j.LoggerFactory
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.ReadinessState
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
internal class LifecycleLog {

    @EventListener
    fun onReady(event: ApplicationReadyEvent) {
        val builder = log.atInfo()
        (event.timeTaken?.let { builder.addKeyValue("startupMs", it.toMillis()) } ?: builder)
            .log("app.started")
    }

    @EventListener
    fun onReadinessChange(event: AvailabilityChangeEvent<ReadinessState>) {
        log.atInfo()
            .addKeyValue("state", event.state.name)
            .log("app.readiness")
    }

    @EventListener
    fun onClosing(event: ContextClosedEvent) {
        log.info("app.shuttingDown")
    }

    private companion object {
        private val log = LoggerFactory.getLogger("app.lifecycle")
    }
}
