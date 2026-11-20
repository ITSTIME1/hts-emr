package com.htsemr.observability

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object PatientRef {

    private const val ALGORITHM = "HmacSHA256"
    private const val LENGTH = 8
    private const val DEFAULT_SALT = "local-dev-salt"

    private val key = SecretKeySpec(
        (System.getenv("PATIENT_REF_SALT")?.takeIf { it.isNotBlank() } ?: DEFAULT_SALT).toByteArray(),
        ALGORITHM,
    )

    fun of(patientNo: String): String {
        val mac = Mac.getInstance(ALGORITHM).apply { init(key) }
        return mac.doFinal(patientNo.toByteArray()).joinToString("") { "%02x".format(it) }.take(LENGTH)
    }
}
