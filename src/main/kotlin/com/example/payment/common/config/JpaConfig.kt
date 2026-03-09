package com.example.payment.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * JPA 설정.
 *
 * @EnableJpaAuditing을 활성화해야 엔티티의 @CreatedDate, @LastModifiedDate가 동작한다.
 * @SpringBootApplication에 직접 붙이지 않고 별도 Config 클래스에 분리한 이유는,
 * @SpringBootTest 슬라이스 테스트(@WebMvcTest 등)에서 JPA Auditing이 없는 컨텍스트를
 * 구동할 때 충돌이 발생하는 것을 방지하기 위해서다.
 *
 * @EnableJpaRepositories로 JPA 리포지토리 스캔 범위를 명시해
 * Redis와 JPA 리포지토리 자동 감지 충돌("strict repository configuration mode" 경고)을 방지한다.
 */
@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = ["com.example.payment"])
class JpaConfig
