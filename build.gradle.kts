plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	id("org.springframework.boot") version "4.0.3"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.2.21"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "Demo project for Spring Boot"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// 웹 MVC (REST API 제공)
	implementation("org.springframework.boot:spring-boot-starter-web")
	// JPA (MySQL ORM)
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	// Redis (캐싱 + 분산 락)
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	// Spring Cache Abstraction (@Cacheable, @CacheEvict 사용에 필요)
	implementation("org.springframework.boot:spring-boot-starter-cache")
	// Bean Validation (@Valid, @NotNull 등)
	implementation("org.springframework.boot:spring-boot-starter-validation")
	// Kotlin 리플렉션 (Spring이 Kotlin 클래스를 다루는 데 필요)
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	// Jackson Kotlin 모듈 (data class 직렬화/역직렬화)
	implementation("tools.jackson.module:jackson-module-kotlin")
	// Redisson (Redis 분산 락 구현 — 4주차에 Spring Boot 4.x 호환 버전으로 추가 예정)
	// implementation("org.redisson:redisson-spring-boot-starter:3.25.0")
	// MySQL 드라이버 (런타임만 필요)
	// Connector/J 9.x: auth_gssapi_client 버그 (MySQL Enterprise 플러그인 로드 실패)
	// Connector/J 8.1.0+: MySQL 8.0.28+의 Server Hello 변경에 여전히 실패 (BadAuthenticationPlugin)
	// Connector/J 8.0.33 + MySQL 8.0.27: 8.0.28 이전 — Server Hello 핸드쉐이크 변경 없는 마지막 안정 조합
	runtimeOnly("com.mysql:mysql-connector-j:8.0.33")
	// Spring Boot 통합 테스트 (JUnit 5 포함)
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	// MockK (Kotlin 친화적인 Mocking 프레임워크)
	testImplementation("io.mockk:mockk:1.13.8")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
