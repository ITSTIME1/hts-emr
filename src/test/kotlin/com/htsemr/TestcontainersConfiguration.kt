package com.htsemr

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.oracle.OracleContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun oracleContainer(): OracleContainer = ORACLE

    companion object {
        private val ORACLE = OracleContainer(DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart"))
            .withStartupTimeout(Duration.ofMinutes(3))
    }
}
