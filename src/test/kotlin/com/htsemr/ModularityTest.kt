package com.htsemr

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModularityTest {

    @Test
    fun `모듈 경계 규칙을 위반하지 않는다`() {
        ApplicationModules.of(HtsEmrApplication::class.java).verify()
    }
}
