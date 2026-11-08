package com.htsemr

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic


/**
 * @ApplicationModuleTest의 기본 모드는 STANDALONE 이다.
 * 모듈 단위로 테스트할 때는 모듈 테스트 시작 시, com.htsemr.[module] 패키지의 빈과 엔티티만 찾는다.
 * 반면 com.htsemr.common.converter 같은 다른 패키지는 찾지 못하여 테스트 시작에 실패한다.
 *
 * Hibernate는 테스트 컨텍스트에 존재하지 않는 클래스를 해석할 수 없다. "common" 모듈은 모든 모듈이 의존하므로,
 * 모듈 테스트에서 공통적으로 참조하는 모듈의 경우 아래와 같이 @Modulithic을 설정한다.
 */
@Modulithic(sharedModules = ["common"])
@SpringBootApplication
class HtsEmrApplication

fun main(args: Array<String>) {
    runApplication<HtsEmrApplication>(*args)
}
