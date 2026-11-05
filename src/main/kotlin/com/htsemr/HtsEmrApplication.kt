package com.htsemr

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class HtsEmrApplication

fun main(args: Array<String>) {
    runApplication<HtsEmrApplication>(*args)
}
