package com.htsemr

import org.springframework.boot.fromApplication
import org.springframework.boot.with

/**
 * 로컬 실행용 진입점. Oracle 컨테이너를 함께 띄운다.
 * 실행: ./gradlew bootTestRun
 */
fun main(args: Array<String>) {
    fromApplication<HtsEmrApplication>().with(TestcontainersConfiguration::class).run(*args)
}
