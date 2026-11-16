package com.htsemr

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic

@Modulithic(sharedModules = ["common"])
@SpringBootApplication
class HtsEmrApplication

fun main(args: Array<String>) {
    runApplication<HtsEmrApplication>(*args)
}
