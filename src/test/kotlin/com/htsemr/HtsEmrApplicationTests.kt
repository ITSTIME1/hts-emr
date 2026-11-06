package com.htsemr

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class HtsEmrApplicationTests {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `애플리케이션이 Oracle 컨테이너에 연결되어 뜬다`() {
        val result = jdbcTemplate.queryForObject("select 1 from dual", Int::class.java)

        assertThat(result).isEqualTo(1)
    }
}
