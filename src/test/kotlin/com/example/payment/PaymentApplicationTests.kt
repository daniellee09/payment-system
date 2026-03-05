package com.example.payment

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@Disabled("로컬 Docker 환경(MySQL + Redis)이 필요합니다. CI 환경에서는 별도 프로파일로 분리하세요.")
class PaymentApplicationTests {

	@Test
	fun contextLoads() {
	}

}
