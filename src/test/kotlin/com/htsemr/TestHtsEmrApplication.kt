package com.htsemr

import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
    fromApplication<HtsEmrApplication>().with(TestcontainersConfiguration::class).run(*args)
}
