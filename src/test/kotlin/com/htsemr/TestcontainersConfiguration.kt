package com.htsemr

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.oracle.OracleContainer
import org.testcontainers.utility.DockerImageName

/**
 * 테스트와 로컬 실행에서 쓰는 Oracle 컨테이너.
 * `@ServiceConnection`이 컨테이너의 접속 정보를 datasource 설정으로 넘겨 준다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun oracleContainer(): OracleContainer =
        OracleContainer(DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart"))
}
