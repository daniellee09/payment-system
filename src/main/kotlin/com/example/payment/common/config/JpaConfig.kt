package com.example.payment.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

/**
 * JPA 설정.
 *
 * @EnableJpaAuditing을 활성화해야 엔티티의 @CreatedDate, @LastModifiedDate가 동작한다.
 * @SpringBootApplication에 직접 붙이지 않고 별도 Config 클래스에 분리한 이유는,
 * @SpringBootTest 슬라이스 테스트(@WebMvcTest 등)에서 JPA Auditing이 없는 컨텍스트를
 * 구동할 때 충돌이 발생하는 것을 방지하기 위해서다.
 */
@Configuration
@EnableJpaAuditing
class JpaConfig
