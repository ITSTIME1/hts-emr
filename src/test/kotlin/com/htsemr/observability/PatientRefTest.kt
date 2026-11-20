package com.htsemr.observability

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PatientRefTest {

    @Test
    fun `같은 환자번호는 늘 같은 참조가 나오고 형식은 16진수 8자리다`() {
        val first = PatientRef.of("P-20261120-0001")
        val second = PatientRef.of("P-20261120-0001")

        assertThat(first).isEqualTo(second)
        assertThat(first).matches("^[0-9a-f]{8}$")
    }

    @Test
    fun `다른 환자번호는 다른 참조가 나오고 원문을 담지 않는다`() {
        val a = PatientRef.of("P-20261120-0001")
        val b = PatientRef.of("P-20261120-0002")

        assertThat(a).isNotEqualTo(b)
        assertThat(a).doesNotContain("0001")
    }
}
