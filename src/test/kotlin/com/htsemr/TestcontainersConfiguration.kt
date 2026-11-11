package com.htsemr

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.oracle.OracleContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration

/**
 * 테스트와 로컬 실행에서 쓰는 Oracle 컨테이너.
 * `@ServiceConnection`이 컨테이너의 접속 정보를 datasource 설정으로 넘겨 준다.
 *
 * [컨테이너 하나를 모든 컨텍스트가 같이 쓴다]
 * @ApplicationModuleTest는 모듈마다(그리고 @MockitoBean 구성마다) 컨텍스트를 따로 만들고,
 * Spring은 재사용을 위해 컨텍스트를 테스트가 끝날 때까지 캐시에 남긴다.
 * 빈 메서드가 매번 새 컨테이너를 만들면 컨텍스트 수만큼 Oracle이 동시에 떠서 Docker 메모리가 바닥난다.
 * 그래서 JVM에 하나뿐인 인스턴스를 돌려준다. 이미 떠 있으면 start()는 아무것도 하지 않는다.
 *
 * - 스키마는 Flyway가 처음 한 번 만들고, 이후 컨텍스트는 적용된 마이그레이션을 건너뛴다.
 * - DB를 같이 쓰므로 테스트는 자기가 만든 데이터를 반드시 지워야 한다(@AfterEach 정리나 @Transactional 롤백).
 * - 컨텍스트가 닫히면 Spring이 컨테이너도 닫는다. 컨텍스트는 JVM이 끝날 때 닫히므로 괜찮지만,
 *   @DirtiesContext처럼 실행 중에 컨텍스트를 닫으면 다른 테스트의 DB까지 멈춘다.
 *
 * [준비 대기 시간은 3분이다]
 * 기본값 60초로는 CI 러너(공유 VM이라 실행마다 속도가 다르다)에서 Oracle이 제때 뜨지 못해
 * 모든 컨텍스트가 만들어지지 못한 적이 있다. 로컬에서는 보통 수십 초면 뜬다.
 */
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
