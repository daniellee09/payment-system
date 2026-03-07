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
	// Redisson (분산 락 구현 — Spring Boot 4.x 호환을 위해 starter 대신 plain 라이브러리 사용)
	// redisson-spring-boot-starter는 Spring Boot 버전에 강하게 결합되어 있어
	// 최신 Spring Boot 4.x와 호환되는 버전을 특정하기 어렵다.
	// plain redisson은 RedissonClient를 수동으로 구성하므로 Spring Boot 버전에 무관하게 동작한다.
	implementation("org.redisson:redisson:3.40.0")
	// MySQL 드라이버 (런타임만 필요)
	// Spring Boot 4.x BOM 관리 버전(9.x)을 사용한다.
	// 8.0.33은 HikariCP 7.x(Spring Boot 4.x 번들)와 호환 불가 — BadAuthenticationPlugin 에러 발생
	runtimeOnly("com.mysql:mysql-connector-j")
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
