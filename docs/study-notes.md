# 결제 시스템 프로젝트 — 개인 공부 노트

> 이 파일은 프로젝트를 진행하면서 만든 결정들의 **"왜"** 에 초점을 맞춘 개인 복습 자료다.
> 코드가 무엇을 하는지보다, 왜 그렇게 짰는지를 이해하는 것이 목표다.

---

## 목차

1. [프로젝트 구조 설계](#1-프로젝트-구조-설계)
2. [Gradle 의존성](#2-gradle-의존성)
3. [Kotlin + Spring Boot 조합의 특이점](#3-kotlin--spring-boot-조합의-특이점)
4. [Docker로 로컬 인프라 관리](#4-docker로-로컬-인프라-관리)
5. [application.yml 핵심 설정들](#5-applicationyml-핵심-설정들)
6. [Connector/J 버그 트러블슈팅](#6-connectorj-버그-트러블슈팅)
7. [공통 레이어 구현 — 예외 처리와 응답 설계](#7-공통-레이어-구현--예외-처리와-응답-설계)
8. [Product 도메인 구현 — 엔티티 설계와 캐싱](#8-product-도메인-구현--엔티티-설계와-캐싱)
9. [Order 도메인 구현 — 상태 머신과 N+1 방지](#9-order-도메인-구현--상태-머신과-n1-방지)
10. [Payment 도메인 구현 — 멱등성과 트랜잭션 안전성](#10-payment-도메인-구현--멱등성과-트랜잭션-안전성)
11. [동시성 제어 — 분산 락과 낙관적 락 이중 방어](#11-동시성-제어--분산-락과-낙관적-락-이중-방어)
12. [Redis 캐싱 전략](#12-redis-캐싱-전략)
13. [단위 테스트 전략](#13-단위-테스트-전략)
14. [DB 스키마 설계](#14-db-스키마-설계)
15. [포트 충돌 트러블슈팅 — 진짜 원인은 MariaDB였다](#15-포트-충돌-트러블슈팅--진짜-원인은-mariadb였다)
16. [코드 리뷰에서 발견한 14가지 이슈와 해결](#16-코드-리뷰에서-발견한-14가지-이슈와-해결)

---

## 1. 프로젝트 구조 설계

### 왜 도메인 중심으로 패키지를 나눴나?

```
product/
  domain/
  repository/
  service/
  controller/
  dto/
```

흔히 보이는 다른 방식:

```
controller/
  ProductController
  OrderController
service/
  ProductService
  OrderService
```

**레이어 중심** 구조는 작은 프로젝트에선 괜찮지만, 파일이 많아지면 `service/` 폴더 안에 관련 없는 서비스들이 뒤섞인다. 특정 도메인(예: `product`)을 이해하려면 `controller/`, `service/`, `repository/` 세 폴더를 번갈아가며 봐야 한다.

**도메인 중심** 구조는 `product/` 폴더만 보면 해당 도메인의 모든 코드를 찾을 수 있다. 응집도(cohesion)가 높다. 나중에 MSA로 분리할 때도 도메인 폴더 단위로 떼어내면 된다.

---

### 왜 common/ 패키지가 별도로 존재하나?

`ApiResponse`, `BusinessException`, `GlobalExceptionHandler`는 특정 도메인에 속하지 않는다. 모든 도메인이 공통으로 사용하는 코드다. 이걸 `product/`나 `payment/` 안에 두면 다른 도메인이 그 패키지에 의존하게 되어 의존 방향이 꼬인다. `common/`이라는 중립 지대를 만들어 모든 도메인이 공통 코드에 단방향으로 의존하게 한다.

---

## 2. Gradle 의존성

### 핵심 의존성 선택 이유

| 의존성 | 선택 이유 |
|--------|-----------|
| `spring-boot-starter-web` | REST API를 제공하려면 내장 Tomcat + Spring MVC가 필요하다 |
| `spring-boot-starter-data-jpa` | 직접 JDBC를 쓰면 SQL을 모두 수동으로 작성해야 한다. JPA는 객체-테이블 매핑과 기본 CRUD를 자동화해준다 |
| `spring-boot-starter-data-redis` | 캐싱과 분산 락에 Redis를 쓴다. Reactive 버전이 아닌 동기 버전을 선택한 이유는 이 프로젝트가 Spring MVC(블로킹) 기반이기 때문 |
| `spring-boot-starter-cache` | `@Cacheable` 어노테이션을 사용하려면 Spring Cache Abstraction이 활성화되어 있어야 한다. 이 스타터가 없으면 어노테이션을 달아도 캐시가 동작하지 않는다 |
| `spring-boot-starter-validation` | `@Valid`, `@NotNull`, `@Min` 같은 Bean Validation 어노테이션을 Controller에서 동작시키려면 이 의존성이 필요하다 |
| `mysql-connector-j` (runtimeOnly) | 컴파일 시에는 필요 없고, 앱이 실제로 실행될 때 MySQL 드라이버가 있으면 된다. `runtimeOnly`로 선언해 컴파일 클래스패스를 깔끔하게 유지한다 |
| `mockk` | Kotlin 클래스는 기본적으로 `final`이라 Mockito가 프록시를 생성하지 못한다. MockK는 Kotlin을 위한 Mocking 프레임워크로 `final` 클래스도 모킹할 수 있다 |

---

### Redisson 의존성 — starter 대신 core 라이브러리를 선택한 이유

```kotlin
implementation("org.redisson:redisson:3.40.0")  // starter가 아닌 core
```

처음에는 `redisson-spring-boot-starter:3.25.0`을 사용하려 했으나 Spring Boot 4.x 호환성이 확인되지 않아 주석 처리했다. 4주차에 분산 락을 실제로 구현할 때 **starter 대신 plain 라이브러리(3.40.0)**를 선택했다. starter는 Spring Boot 자동 설정에 의존하는데, Spring Boot 4.x(Spring Framework 7.x)의 큰 변경으로 3.x용 starter가 동작하지 않을 수 있다. plain 라이브러리 + `RedissonConfig`에서 수동 빈 등록 방식이 버전 호환성 문제를 가장 안전하게 우회한다.

---

### `implementation` vs `runtimeOnly` vs `testImplementation` 차이

| 스코프 | 언제 클래스패스에 포함되나 | 예시 |
|--------|--------------------------|------|
| `implementation` | 컴파일 + 런타임 모두 | Spring MVC, JPA |
| `runtimeOnly` | 런타임만 (컴파일 시 코드에서 직접 참조 안 할 때) | MySQL 드라이버 |
| `testImplementation` | 테스트 컴파일 + 테스트 런타임 | MockK, JUnit |
| `testRuntimeOnly` | 테스트 런타임만 | JUnit Platform Launcher |

MySQL 드라이버를 `runtimeOnly`로 선언하면 실수로 애플리케이션 코드에서 `com.mysql.*` 클래스를 직접 import하는 것을 컴파일 오류로 막아준다. DB 드라이버는 JPA가 내부적으로 사용하는 것이지, 우리 코드가 직접 다룰 필요가 없다.

---

## 3. Kotlin + Spring Boot 조합의 특이점

### 왜 `allOpen` 설정이 필요한가?

```kotlin
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
```

Kotlin의 모든 클래스는 기본적으로 `final`이다 (Java는 기본 open). JPA는 내부적으로 **프록시(Proxy)** 패턴을 사용한다. 엔티티를 상속한 프록시 클래스를 동적으로 생성해 Lazy Loading, 변경 감지 등을 구현한다.

`final` 클래스는 상속이 불가능하므로 JPA가 프록시를 만들 수 없다. `allOpen` 플러그인이 `@Entity` 등의 어노테이션이 붙은 클래스를 자동으로 `open`으로 만들어준다.

```
일반 조회:  영속성 컨텍스트 → Product 엔티티 직접 반환
Lazy 조회:  영속성 컨텍스트 → ProductProxy (Product 상속) → 실제 접근 시 쿼리 실행
```

`plugin.jpa`도 함께 쓰는데, 이 플러그인은 JPA 엔티티의 기본 생성자(no-arg constructor)를 자동으로 생성해준다. JPA는 리플렉션으로 엔티티 인스턴스를 만들 때 기본 생성자가 필요한데, Kotlin data class나 일반 클래스는 기본 생성자가 없을 수 있기 때문이다.

---

### 왜 엔티티에 `data class`를 사용하지 않나?

`data class`를 쓰면 `equals()`, `hashCode()`, `copy()`, `toString()`이 자동 생성된다. 언뜻 편리해 보이지만 JPA 엔티티에는 문제가 된다.

**`hashCode()` 문제**: `data class`의 `hashCode()`는 모든 프로퍼티를 기반으로 계산한다. 엔티티는 DB에 저장되기 전(`id = null`)과 저장된 후(`id = 1L`)에 `hashCode()`가 달라진다. `HashSet`이나 `HashMap`에 저장된 엔티티가 저장 후 사라지는 버그가 발생할 수 있다.

**`equals()` 문제**: 두 엔티티가 동일한 DB 행을 나타내는지는 id로 판단해야 한다. `data class`의 `equals()`는 모든 필드를 비교하므로, id가 같아도 다른 필드가 다르면 다른 객체로 취급한다.

**`copy()` 문제**: JPA 엔티티는 영속성 컨텍스트가 관리하는 상태(identity)를 가진다. `copy()`로 새 인스턴스를 만들면 JPA가 모르는 객체가 생기고, 변경 감지(dirty checking)가 동작하지 않는다.

올바른 엔티티 equals/hashCode 구현 방법:

```kotlin
@Entity
class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    // id가 null이면(저장 전) 같은 참조인 경우만 같다고 본다
    // id가 있으면 id 기준으로 비교한다
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Product) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
```

---

### 왜 `!!` 사용을 금지하나?

```kotlin
val product = productRepository.findById(id).get()  // NoSuchElementException 가능
val product = productRepository.findById(id)!!       // NullPointerException 가능
```

Kotlin의 강점은 컴파일 타임에 null을 타입으로 구분한다는 것이다. `!!`는 "나는 이 값이 절대 null이 아님을 확신한다"는 선언인데, 이 확신이 틀리면 런타임에 `NullPointerException`이 발생한다. 이는 Kotlin의 null safety를 스스로 무력화하는 행위다.

대신 명시적으로 처리한다:

```kotlin
// 없으면 의미 있는 예외를 던진다
val product = productRepository.findById(id)
    .orElseThrow { BusinessException(ErrorCode.PRODUCT_NOT_FOUND) }
```

이렇게 하면 "왜 실패했는가"를 API 응답에서 정확하게 전달할 수 있다.

---

### Jackson Kotlin 모듈이 필요한 이유

```kotlin
implementation("tools.jackson.module:jackson-module-kotlin")
```

Spring MVC는 HTTP 요청/응답 본문을 Jackson으로 직렬화/역직렬화한다. 기본 Jackson은 Java 기반이라 Kotlin의 특성을 모른다.

- Kotlin의 `data class`는 모든 필드를 생성자 파라미터로 받는다. 기본 Jackson은 기본 생성자 + setter로 객체를 만들려 하므로 실패한다.
- Kotlin의 nullable 타입 (`String?`)을 Java의 `@Nullable`과 다르게 처리해야 한다.

`jackson-module-kotlin`이 이 간극을 메워준다.

---

## 4. Docker로 로컬 인프라 관리

### 왜 직접 설치 대신 Docker를 쓰나?

로컬에 MySQL, Redis를 직접 설치하면:
- 팀원마다 버전이 다를 수 있다 → "내 컴퓨터에서는 되는데?"
- 프로젝트별로 설정이 달라야 할 때 충돌 → 포트, 비밀번호 등
- 프로젝트가 끝나도 DB가 로컬에 남아 자원을 차지한다

Docker Compose는 `docker-compose.yml` 하나로 팀원 모두가 동일한 환경을 `docker-compose up -d` 한 줄로 재현한다. `docker-compose down`으로 깔끔하게 정리되고, 프로젝트 코드와 함께 버전 관리된다.

---

### 포트 매핑의 의미: `"3307:3306"`

```yaml
ports:
  - "3307:3306"  # 호스트포트:컨테이너포트
```

컨테이너 내부에서 MySQL은 3306 포트로 실행된다. 이 포트를 호스트(내 컴퓨터)의 3307로 연결한다. 3307을 선택한 이유는 내 로컬에 MySQL이 이미 3306을 점유하고 있기 때문이다.

`application.yml`의 JDBC URL도 그에 맞게 설정한다:
```
jdbc:mysql://localhost:3307/payment
```

---

### volumes 설정이 없으면 어떻게 되나?

```yaml
volumes:
  mysql-data:/var/lib/mysql
```

Docker 컨테이너는 기본적으로 상태가 없다(stateless). `docker-compose down`을 하면 컨테이너가 삭제되면서 그 안에 쌓인 데이터도 함께 사라진다. `volumes`로 MySQL의 데이터 디렉토리를 호스트 볼륨에 연결하면, 컨테이너를 재시작해도 데이터가 유지된다.

Redis는 volumes를 설정하지 않았다. 캐시 데이터는 휘발되어도 무방하기 때문이다. 캐시는 원본 데이터가 DB에 있고, 빠른 접근을 위한 사본일 뿐이다.

---

### 왜 `mysql:latest`를 쓰는가? (그리고 실제 운영에서는 쓰면 안 되는 이유)

개발 초기에는 최신 버전 기능을 쓰고 싶어서 `latest`를 사용했다. 하지만 `latest`는 실제 운영 환경에서는 위험하다:
- 언제 이미지를 받느냐에 따라 버전이 달라진다 → 재현 불가능한 환경
- 오늘은 `8.0.36`, 내일 팀원이 받으면 `9.0.0` → 동작이 달라질 수 있다

운영이나 팀 프로젝트에서는 `mysql:8.0.36`처럼 정확한 버전을 고정하는 게 맞다.

---

## 5. application.yml 핵심 설정들

### 왜 `application.properties`를 두고 `application.yml`을 따로 만들었나?

둘 다 Spring Boot 설정 파일로 기능은 동일하다. `.properties`는 key=value 형식이라 중첩 설정이 복잡하다:

```properties
# properties 방식
spring.datasource.url=jdbc:mysql://...
spring.datasource.username=payment
spring.jpa.hibernate.ddl-auto=create
spring.jpa.properties.hibernate.format_sql=true
```

`.yml`은 계층 구조를 들여쓰기로 표현한다:

```yaml
# yml 방식
spring:
  datasource:
    url: jdbc:mysql://...
    username: payment
  jpa:
    hibernate:
      ddl-auto: create
    properties:
      hibernate:
        format_sql: true
```

설정 항목이 많아질수록 `yml`의 가독성이 압도적으로 좋다. 두 파일이 공존하면 `application.properties`가 먼저 적용되고 `application.yml`이 덮어쓴다 (Spring Boot 기본 동작). 우리는 `application.properties`를 빈 파일로 남겨두고 실질적인 설정을 모두 `application.yml`에 작성했다.

---

### `ddl-auto: create`를 선택한 이유와 왜 나중에 바꿔야 하나

| 값 | 동작 |
|----|------|
| `create` | 시작 시 기존 테이블 DROP 후 다시 CREATE |
| `create-drop` | 시작 시 CREATE, 종료 시 DROP |
| `update` | 엔티티와 비교해 누락된 컬럼/테이블만 추가 (삭제는 안 함) |
| `validate` | 엔티티와 DB 스키마가 일치하는지만 검사. 불일치면 시작 실패 |
| `none` | 아무것도 하지 않음 |

`create`는 개발 초기에 편리하다. 엔티티를 수정할 때마다 테이블이 자동으로 재생성된다. 단, **앱을 재시작할 때마다 모든 데이터가 사라진다**.

2주차 이후에는 엔티티 설계가 어느 정도 확정되면 `validate`로 바꾼다. `validate`는 JPA가 DB를 건드리지 않고 스키마가 맞는지만 확인한다. 실수로 엔티티를 잘못 수정했을 때 앱 실행 시점에 바로 오류를 알 수 있어서 안전하다. 실제 운영 환경에서는 `none`을 사용하고 DB 마이그레이션 툴(Flyway, Liquibase)로 스키마를 관리한다.

---

### `open-in-view: false`를 꼭 설정해야 하는 이유

Spring Boot의 기본값은 `open-in-view: true`다. 이는 **OSIV(Open Session In View)** 패턴을 활성화한다. HTTP 요청이 들어온 순간부터 응답을 보낼 때까지 JPA 세션(영속성 컨텍스트)을 열어둔다.

```
[요청 시작] → [Controller] → [Service/트랜잭션] → [Controller로 반환] → [응답 직렬화] → [세션 닫힘]
```

이렇게 하면 Controller나 View에서도 Lazy Loading이 동작한다. 언뜻 편리해 보이지만:

**문제점**: 트랜잭션이 끝난 후 Controller에서 Lazy Loading이 발생하면 의도치 않은 추가 쿼리가 나간다. 이 쿼리가 느리거나 N+1 문제를 일으켜도 눈치채기 어렵다.

`false`로 설정하면:
- 트랜잭션 범위 밖에서 Lazy Loading을 시도하면 `LazyInitializationException`이 발생해 즉시 알 수 있다
- DB 연결이 트랜잭션 범위 안에서만 유지되어 커넥션 풀 효율이 좋아진다
- 어디서 쿼리가 나가는지 추적하기 쉽다

---

### SQL 로그 두 종류를 모두 설정한 이유

```yaml
logging:
  level:
    org.hibernate.SQL: debug           # 실행 SQL 출력
    org.hibernate.orm.jdbc.bind: trace # 바인딩 파라미터 출력
```

첫 번째만 설정하면:
```sql
SELECT * FROM product WHERE id = ?
```

두 번째까지 설정하면:
```sql
SELECT * FROM product WHERE id = ?
binding parameter [1] as [BIGINT] - [42]
```

`?` 자리에 실제 어떤 값이 들어갔는지 알 수 없으면 쿼리를 직접 실행해서 확인해야 한다. 두 개를 함께 설정하면 개발 중에 실행되는 쿼리와 파라미터를 바로 눈으로 볼 수 있다. 운영 환경에서는 성능과 보안(민감 데이터 노출) 이유로 제거한다.

---

### Redis 기본 TTL을 5분으로 설정한 이유

```yaml
cache:
  redis:
    time-to-live: 300000  # 5분 (ms 단위)
```

TTL(Time To Live)은 캐시 항목이 자동으로 만료되는 시간이다.

- **너무 짧으면**: 캐시 히트율이 낮아져 캐싱의 이점이 없다 → DB 부하가 줄지 않음
- **너무 길면**: 원본 데이터가 바뀌었는데 캐시는 옛날 데이터를 돌려준다 → 정합성 문제

5분은 대부분의 캐싱 시나리오에서 무난한 출발점이다. 상품 상세(`/products/{id}`)처럼 자주 바뀌지 않는 데이터는 10분으로, 재고처럼 실시간성이 중요한 데이터는 캐시를 쓰지 않거나 훨씬 짧게 설정한다. 기본값을 설정해두고 도메인별로 개별 오버라이드하는 방식으로 관리한다.

---

## 6. Connector/J 버그 트러블슈팅

> 이 과정이 가장 많은 시간을 쏟은 부분이다. 문제를 어떻게 추적하고 해결했는지 기록한다.

### 증상

Spring Boot 앱 시작 시 에러 발생:
```
Error loading class 'com.mysql.cj.protocol.a.authentication.AuthenticationGssapiClient'
```

### 원인 추적 과정

**1단계 — 가설 1: MySQL 서버 버전 문제**
- MySQL 8.0 → 9.6.0으로 업그레이드
- 결과: 동일 에러 → MySQL 버전은 원인이 아니었다

**2단계 — 가설 2: Connector/J 버전 문제**
- Connector/J를 8.4.0, 8.0.33으로 다운그레이드
- 결과: 다른 에러 발생 (`mysql_native_password` 관련) → 근본 해결이 아님

**3단계 — 원인 확정**

Hibernate가 시작할 때 데이터베이스 메타데이터(방언, 스키마 정보 등)를 파악하기 위해 JDBC 연결을 생성한다. 이 연결 과정에서 MySQL 서버가 인증 플러그인 목록을 클라이언트에게 전달하는데, 그 목록에 `auth_gssapi_client`가 포함되어 있었다. Connector/J 9.x가 이 플러그인 클래스를 로드하려다 실패했다 (GSSAPI는 Kerberos 인증 관련 엔터프라이즈 기능이고, 기본 JDBC 드라이버 jar에 포함되지 않는다).

이것은 **Connector/J 9.x의 버그** — 서버가 보낸 플러그인 목록 중 실제로 사용하지 않는 것도 로드하려 시도했다.

**4단계 — 해결**

```yaml
jpa:
  properties:
    hibernate:
      boot.allow_jdbc_metadata_access: false
```

이 설정은 Hibernate가 시작 시 JDBC 메타데이터 조회 연결 자체를 만들지 않도록 한다. `ddl-auto`, `dialect` 등을 이미 `application.yml`에 명시했기 때문에 Hibernate가 이 값들을 DB에서 추론할 필요가 없다 → 연결을 만들지 않음 → 버그 미발생.

### 트러블슈팅에서 배운 것

- **같은 에러를 보고 여러 가설을 세운다**: MySQL 버전, Connector/J 버전, 설정 문제 등 가능한 원인을 모두 나열하고 하나씩 제거한다
- **버전을 올리는 게 항상 해결책이 아니다**: 내려가도 안 되고 올려도 안 됐다 → 버전이 원인이 아님을 확인
- **근본 원인 vs 우회**: `allow_jdbc_metadata_access: false`는 우회책이다. Connector/J가 버그를 고치면 이 설정은 불필요해진다. 우회책을 쓸 때는 왜 우회하는지 주석으로 반드시 남긴다

---

## 7. 공통 레이어 구현 — 예외 처리와 응답 설계

> 2주차에 Product/Order 도메인을 만들기 전에, 모든 도메인이 공통으로 사용할 기반 코드를 먼저 구축했다.

### 왜 공통 레이어를 먼저 만들었나?

Product, Order, Payment 모든 도메인이 "API 응답 형식"과 "예외 처리 방식"에 의존한다. 이 기반이 없으면 각 도메인 개발자가 제각각 응답 형식을 만들게 되고, 나중에 통일하려면 대규모 리팩토링이 필요하다. 공통 코드 5개 파일을 먼저 구현해서 모든 도메인이 동일한 규칙 위에서 개발되도록 했다.

### ApiResponse<T> — 공통 응답 래퍼 구현

API를 사용하는 클라이언트(앱, 프론트엔드) 입장에서는 응답 형식이 일관되어야 처리하기 쉽다.

```kotlin
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ErrorResponse? = null,
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(success = true, data = data)
        fun <T> error(code: String, message: String): ApiResponse<T> =
            ApiResponse(success = false, error = ErrorResponse(code, message, LocalDateTime.now()))
    }
}
```

**왜 이렇게 설계했나**:
- `success` 필드 하나로 성공/실패를 즉시 구분한다. 클라이언트가 HTTP 상태 코드를 파싱할 필요 없이 JSON 본문만으로 결과를 판단할 수 있다.
- `data`와 `error`가 동시에 존재하되 하나만 non-null이다. 타입 안전성을 유지하면서 하나의 응답 클래스로 성공/실패를 모두 표현한다.
- `companion object`의 팩토리 메서드로 Controller 코드를 간결하게 유지한다: `ApiResponse.success(data)` 한 줄이면 된다.

```json
// 성공 응답
{ "success": true, "data": { "productId": "abc", "name": "노트북" } }

// 실패 응답
{ "success": false, "error": { "code": "PRODUCT_NOT_FOUND", "message": "상품을 찾을 수 없습니다.", "timestamp": "..." } }
```

---

### ErrorCode — 에러 코드를 enum 한 곳에서 관리

```kotlin
enum class ErrorCode(val status: HttpStatus, val message: String) {
    // 400 Bad Request
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액이 주문 금액과 일치하지 않습니다."),

    // 404 Not Found
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다."),

    // 409 Conflict
    OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
    ALREADY_PAID(HttpStatus.CONFLICT, "이미 결제된 주문입니다."),
    INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "주문 상태가 유효하지 않습니다."),
    INVALID_PAYMENT_STATUS(HttpStatus.CONFLICT, "결제 상태가 유효하지 않습니다."),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "동시 수정이 감지되었습니다. 다시 시도해주세요."),
    LOCK_ACQUISITION_FAILED(HttpStatus.CONFLICT, "요청 처리 중입니다. 잠시 후 다시 시도해주세요."),

    // 500 Internal Server Error
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
}
```

**왜 enum으로 관리하나**: 에러 코드가 흩어져 있으면 중복 코드가 생기고, 어떤 에러가 몇 번 상태 코드인지 찾으려면 여러 파일을 뒤져야 한다. enum 하나에 HTTP 상태 코드와 메시지를 함께 정의하면 새 에러를 추가할 때 항목 하나만 추가하면 된다.

**왜 409 Conflict가 많은가**: 결제 시스템은 "상태가 꼬이는" 상황이 빈번하다. 이미 결제된 주문에 또 결제하려는 경우, 재고가 없는데 주문하려는 경우, 동시에 같은 자원을 수정하려는 경우. 이런 "충돌" 상황을 409로 명확히 구분해 클라이언트가 재시도 여부를 판단할 수 있게 한다.

---

### BusinessException — 예외 계층화

```kotlin
abstract class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
) : RuntimeException(message)

// 구체 예외 클래스들
class OutOfStockException(currentStock: Int) :
    BusinessException(ErrorCode.OUT_OF_STOCK, "재고가 부족합니다. 현재 재고: $currentStock")

class PaymentAmountMismatchException(expected: BigDecimal, actual: BigDecimal) :
    BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
        "결제 금액이 주문 금액과 일치하지 않습니다. 주문 금액: $expected, 결제 금액: $actual")
```

**왜 RuntimeException을 상속하나**: Spring의 `@Transactional`은 기본적으로 unchecked exception(RuntimeException 하위)에서만 자동 롤백한다. checked exception이면 트랜잭션이 커밋되어 "결제는 실패했는데 재고는 차감된" 위험한 상황이 발생할 수 있다.

**왜 구체 예외 클래스를 따로 만드나**: `OutOfStockException(currentStock: Int)`처럼 현재 재고를 메시지에 포함하면 디버깅이 쉽다. "재고가 부족합니다"보다 "재고가 부족합니다. 현재 재고: 3"이 훨씬 유용하다. `PaymentAmountMismatchException`도 주문 금액과 실제 금액을 함께 보여줘서 어디서 불일치가 생겼는지 바로 알 수 있다.

---

### GlobalExceptionHandler — 예외 처리 중앙화

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {
    // 우선순위 순서로 처리

    // 1. 비즈니스 예외 (가장 구체적)
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ApiResponse<Unit>>

    // 2. 낙관적 락 충돌
    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLock(e: ObjectOptimisticLockingFailureException): ResponseEntity<ApiResponse<Unit>>

    // 3. Bean Validation 실패 (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Unit>>

    // 4. 존재하지 않는 리소스 (favicon.ico 등)
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(e: NoResourceFoundException): ResponseEntity<ApiResponse<Unit>>

    // 5. 그 외 모든 예외 (catch-all)
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Unit>>
}
```

**왜 우선순위를 이렇게 설정했나**:
- 구체적인 예외부터 잡아야 정확한 에러 코드를 반환할 수 있다. `BusinessException`을 먼저 잡지 않으면 `Exception` catch-all이 모든 비즈니스 에러를 500으로 처리해버린다.
- `ObjectOptimisticLockingFailureException`은 JPA 내부에서 던지는 예외라 우리 코드에서 직접 catch하지 못한다. GlobalExceptionHandler에서 잡아 409 CONCURRENT_UPDATE로 변환한다.
- `NoResourceFoundException`을 별도로 처리하는 이유: 브라우저가 자동으로 `/favicon.ico`를 요청하는데, 이걸 500 에러로 기록하면 모니터링 알림이 불필요하게 울린다.
- 마지막 `Exception` catch-all은 예상치 못한 에러를 잡는다. 내부적으로 전체 스택 트레이스를 로깅하되, 클라이언트에게는 "서버 내부 오류"만 보여준다. 스택 트레이스가 API 응답에 노출되면 보안 취약점이 된다.

---

### JpaConfig — @EnableJpaAuditing 분리

```kotlin
@Configuration
@EnableJpaAuditing
class JpaConfig
```

**왜 @SpringBootApplication에 직접 붙이지 않았나**: `@SpringBootTest`의 슬라이스 테스트(`@WebMvcTest` 등)에서 `@EnableJpaAuditing`이 `@SpringBootApplication` 위에 있으면, JPA 관련 빈을 로드하지 않는 슬라이스 테스트에서 audit listener 빈을 찾지 못해 실패한다. 별도 `@Configuration` 클래스로 분리하면 필요한 테스트에서만 로드된다.

---

## 8. Product 도메인 구현 — 엔티티 설계와 캐싱

> 2주차에 첫 번째로 구현한 도메인. 엔티티 설계 원칙과 JPA 활용의 기본기를 다지는 작업이었다.

### 왜 Product를 먼저 구현했나?

Product는 다른 도메인(Order, Payment)이 의존하는 기본 도메인이다. 주문을 만들려면 상품이 있어야 하고, 결제를 하려면 주문이 있어야 한다. 의존 방향의 시작점인 Product부터 구현해야 다른 도메인 개발 시 실제 데이터를 사용해 테스트할 수 있다.

### Product 엔티티 — 핵심 설계 결정들

```kotlin
@Entity
@Table(name = "products")
class Product(
    name: String,
    price: BigDecimal,
    stock: Int,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, unique = true, length = 36)
    val productId: String = UUID.randomUUID().toString()

    @Column(nullable = false, precision = 15, scale = 2)
    var price: BigDecimal = price
        protected set

    @Column(nullable = false)
    var stock: Int = stock
        protected set

    @Version
    var version: Long = 0
        protected set

    fun decreaseStock(quantity: Int) {
        if (stock < quantity) throw OutOfStockException(stock)
        stock -= quantity
    }

    fun increaseStock(quantity: Int) {
        stock += quantity
    }
}
```

#### 이중 ID 전략: `id`(Long) + `productId`(UUID)

**왜 ID가 두 개인가?**
- `id: Long` (AUTO_INCREMENT): DB 내부용 PK. 인덱스 성능이 좋고 JOIN이 빠르다.
- `productId: String` (UUID): 외부 API에서 노출하는 식별자.

**왜 내부 PK를 API에 노출하면 안 되나?**
- 순차적인 Long ID를 노출하면 `GET /products/1`, `GET /products/2`, `GET /products/3`으로 전체 상품을 열거할 수 있다.
- 경쟁사가 "이 서비스의 상품 수는 약 10만 개"라는 정보를 ID만 보고 추정할 수 있다.
- UUID는 추측이 불가능하므로 이런 열거 공격(enumeration attack)을 방지한다.

#### protected set — 엔티티 캡슐화

```kotlin
var stock: Int = stock
    protected set  // 외부에서 product.stock = 5 불가
```

**왜 private이 아닌 protected인가**: Kotlin + JPA `allOpen` 플러그인 조합에서 `@Entity` 클래스가 자동으로 `open`이 된다. Kotlin은 `open` 클래스의 프로퍼티에 `private set`을 허용하지 않는다 (하위 클래스가 접근할 수 없게 되므로). JPA 프록시(엔티티를 상속한 클래스)는 `protected`까지 접근 가능하므로 JPA 동작에 영향 없으면서 외부 직접 접근을 막는다.

#### BigDecimal 금액 처리

```kotlin
@Column(nullable = false, precision = 15, scale = 2)
var price: BigDecimal = price
```

**왜 Double/Float을 쓰면 안 되나**: 부동소수점은 이진수로 소수를 표현하므로 정확한 십진수 표현이 불가능하다.

```kotlin
println(0.1 + 0.2)  // 0.30000000000000004 (!!!)
```

결제 시스템에서 1원이라도 오차가 나면 안 된다. `BigDecimal`은 십진수를 정확하게 표현한다. `precision = 15, scale = 2`는 최대 13자리 정수부 + 2자리 소수부를 지원한다 (예: `9,999,999,999,999.99`).

#### decreaseStock/increaseStock — 비즈니스 로직 캡슐화

```kotlin
fun decreaseStock(quantity: Int) {
    if (stock < quantity) throw OutOfStockException(stock)
    stock -= quantity
}
```

**왜 Service에서 하지 않고 엔티티 내부에서 하나**: "재고가 0 미만이 되면 안 된다"는 Product의 비즈니스 규칙이다. 이 규칙을 Service에 두면 다른 Service가 Product.stock을 직접 수정할 때 검증을 깜빡할 수 있다. 엔티티 내부에 두면 어디서 호출하든 반드시 검증이 실행된다.

### ProductService — 레이어 분리와 트랜잭션

```kotlin
@Service
@Transactional(readOnly = true)  // 클래스 레벨: 기본 읽기 전용
class ProductService(
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository,
) {
    @Transactional  // 메서드 레벨: 쓰기용 트랜잭션으로 오버라이드
    fun createProduct(request: CreateProductRequest): ProductResponse {
        val product = Product(name = request.name, price = request.price, stock = request.stock)
        return ProductResponse.from(productRepository.save(product))
    }

    fun getProduct(productId: String): ProductResponse {
        val product = productRepository.findByProductId(productId)
            ?: throw ProductNotFoundException()
        return ProductResponse.from(product)
    }
}
```

**왜 클래스 레벨에 readOnly = true인가**: 대부분의 메서드가 조회이므로 기본값을 읽기 전용으로 설정한다. `readOnly = true`는 Hibernate에게 "이 트랜잭션에서는 변경이 없다"고 알려주어 dirty checking(변경 감지)을 건너뛴다. 조회 성능이 5~10% 개선된다. 쓰기가 필요한 메서드만 `@Transactional`로 개별 오버라이드한다.

**왜 Repository가 null을 반환하도록 설계했나**:
```kotlin
// ProductRepository
fun findByProductId(productId: String): Product?  // null 반환

// Service에서 명시적 처리
val product = productRepository.findByProductId(productId)
    ?: throw ProductNotFoundException()
```

`Optional<Product>`도 가능하지만 Kotlin에서는 nullable 타입이 더 자연스럽다. `Product?`를 반환하면 호출부에서 반드시 null을 처리해야 하므로 "상품이 없는 경우"를 깜빡하는 것이 불가능하다.

### CreateProductRequest — Kotlin에서 Bean Validation

```kotlin
data class CreateProductRequest(
    @field:NotBlank(message = "상품명은 필수입니다")
    val name: String,

    @field:NotNull(message = "가격은 필수입니다")
    @field:DecimalMin(value = "0.01", message = "가격은 0보다 커야 합니다")
    val price: BigDecimal,

    @field:NotNull(message = "재고는 필수입니다")
    @field:Min(value = 1, message = "재고는 1개 이상이어야 합니다")
    val stock: Int,
)
```

**왜 `@field:` 접두사가 필요한가**: Kotlin의 data class 프로퍼티는 생성자 파라미터이면서 동시에 필드이고 getter이다. `@NotNull`만 쓰면 어노테이션이 생성자 파라미터에 붙는다. Bean Validation은 필드나 getter의 어노테이션을 읽으므로 `@field:NotNull`로 명시해야 제대로 동작한다.

### ProductResponse — DTO 분리

```kotlin
data class ProductResponse(
    val productId: String,
    val name: String,
    val price: BigDecimal,
    val stock: Int,
    val createdAt: LocalDateTime,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L

        fun from(product: Product): ProductResponse = ProductResponse(
            productId = product.productId,
            name = product.name,
            price = product.price,
            stock = product.stock,
            createdAt = requireNotNull(product.createdAt) { "createdAt must not be null" },
        )
    }
}
```

**왜 엔티티를 직접 반환하지 않나**:
- 엔티티에는 내부 PK(`id: Long`), 버전(`version`), JPA 프록시 관련 필드가 있다. 이런 것들이 API 응답에 노출되면 안 된다.
- 엔티티 구조가 바뀌면 API 응답도 같이 바뀐다. DTO를 분리하면 내부 구조 변경이 API 계약에 영향을 주지 않는다.
- `from()` 팩토리 메서드로 변환 로직을 한 곳에 집중시킨다.

**왜 Serializable인가**: Redis `@Cacheable`이 기본적으로 JDK 직렬화를 사용한다. 캐시에 저장되는 DTO는 `Serializable`을 구현해야 한다. `serialVersionUID = 1L`은 클래스 구조가 변경됐을 때 역직렬화 호환성을 관리하는 데 사용된다.

**왜 `requireNotNull`인가**: `product.createdAt`은 `LocalDateTime?` 타입이다 (DB 저장 전에는 null). `from()`은 저장된 엔티티에서만 호출되므로 null일 수 없지만, `!!` 대신 `requireNotNull`을 사용해 null일 때 의미 있는 에러 메시지를 제공한다.

### ProductController — 책임 분리

```kotlin
@RestController
@RequestMapping("/api/v1/products")
class ProductController(private val productService: ProductService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createProduct(@Valid @RequestBody request: CreateProductRequest): ApiResponse<ProductResponse> =
        ApiResponse.success(productService.createProduct(request))

    @GetMapping
    fun getProducts(): ApiResponse<List<ProductResponse>> =
        ApiResponse.success(productService.getProducts())

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteProduct(@PathVariable productId: String) {
        productService.deleteProduct(productId)
    }
}
```

**Controller가 하는 일은 딱 세 가지**:
1. `@Valid`로 요청 검증 (Bean Validation)
2. Service 호출
3. `ApiResponse`로 응답 래핑

비즈니스 로직은 단 한 줄도 없다. "이 Controller 코드가 길어진다"는 것은 비즈니스 로직이 섞이고 있다는 신호다.

**왜 POST는 201, DELETE는 204인가**:
- `201 Created`: 새 리소스가 생성됐다는 의미. HTTP 표준에서 POST로 리소스를 생성할 때 권장하는 상태 코드.
- `204 No Content`: 요청은 성공했지만 응답 본문이 없다. 삭제된 리소스를 다시 보여줄 필요가 없으므로 본문을 비운다.

---

## 9. Order 도메인 구현 — 상태 머신과 N+1 방지

> 2주차에 Product 다음으로 구현한 도메인. 주문은 "상태 전이"라는 새로운 패턴이 등장한다.

### Order 엔티티 — 상태 머신(State Machine)

```kotlin
@Entity
@Table(name = "orders")
class Order(
    product: Product,
    quantity: Int,
    totalAmount: BigDecimal,
    customerName: String,
) {
    @Column(nullable = false, unique = true, length = 36)
    val orderId: String = UUID.randomUUID().toString()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product = product

    @Enumerated(EnumType.STRING)
    var status: OrderStatus = OrderStatus.CREATED
        protected set

    fun markAsPaid() {
        if (status != OrderStatus.CREATED) throw InvalidOrderStatusException()
        status = OrderStatus.PAID
    }

    fun markAsCancelled() {
        if (status != OrderStatus.PAID) throw InvalidOrderStatusException()
        status = OrderStatus.CANCELLED
    }
}
```

#### 상태 전이 규칙

```
CREATED ──→ PAID ──→ CANCELLED
   (결제 승인)    (결제 취소)
```

**허용되지 않는 전이**:
- CREATED → CANCELLED: 결제하지 않은 주문은 취소할 수 없다
- PAID → CREATED: 결제를 되돌릴 수 없다 (취소만 가능)
- CANCELLED → 어디든: 취소된 주문은 다시 활성화할 수 없다

**왜 엔티티 내부에서 전이를 검증하나**: Service에서 `if (order.status == CREATED)` 체크 후 `order.status = PAID`로 바꾸면 두 가지 문제가 생긴다.
1. 여러 Service에서 주문 상태를 바꿀 때 검증을 빠뜨릴 수 있다.
2. `protected set`으로 외부 직접 변경을 막고 있으므로 `order.status = PAID`가 컴파일 오류다.

`markAsPaid()`를 호출하면 검증 + 상태 변경이 원자적으로 실행된다.

#### @Enumerated(EnumType.STRING) vs EnumType.ORDINAL

```kotlin
@Enumerated(EnumType.STRING)
var status: OrderStatus = OrderStatus.CREATED
```

**STRING**: DB에 `"CREATED"`, `"PAID"`, `"CANCELLED"` 문자열로 저장.
**ORDINAL**: DB에 `0`, `1`, `2` 숫자로 저장.

ORDINAL의 위험: enum에 새 값을 중간에 추가하면 기존 데이터의 의미가 바뀐다. `CREATED(0), PENDING(1), PAID(2)`로 바꾸면 기존 `PAID(1)` 데이터가 `PENDING`으로 잘못 읽힌다. STRING은 이름이 바뀌지 않는 한 안전하다.

#### @ManyToOne(fetch = FetchType.LAZY) — 지연 로딩

```kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "product_id", nullable = false)
val product: Product = product
```

**왜 LAZY인가**: 주문을 조회할 때 항상 상품 정보가 필요한 것은 아니다. EAGER(기본값)로 설정하면 주문을 조회할 때마다 자동으로 상품도 JOIN해서 가져온다. 100개 주문 목록을 조회하면 100번의 추가 SELECT가 발생한다(N+1 문제). LAZY로 설정하면 `order.product`에 실제로 접근할 때만 쿼리가 나간다.

### N+1 문제와 @EntityGraph 해결

**N+1 문제란**: 1개의 목록 쿼리 + N개의 상세 쿼리가 실행되는 성능 문제.

```sql
-- 1. 주문 목록 조회 (1번)
SELECT * FROM orders;

-- 2. 각 주문의 상품을 개별 조회 (N번)
SELECT * FROM products WHERE id = 1;
SELECT * FROM products WHERE id = 2;
SELECT * FROM products WHERE id = 3;
... (100개 주문이면 100번)
```

**@EntityGraph로 해결**:

```kotlin
interface OrderRepository : JpaRepository<Order, Long> {
    @EntityGraph(attributePaths = ["product"])
    @Query("SELECT o FROM Order o ORDER BY o.createdAt DESC")
    fun findAllWithProduct(): List<Order>

    @EntityGraph(attributePaths = ["product"])
    fun findByOrderId(orderId: String): Order?
}
```

`@EntityGraph`는 LAZY 설정을 해당 쿼리에서만 EAGER로 오버라이드한다. SQL이 `LEFT JOIN products`로 바뀌어 한 번의 쿼리로 주문과 상품을 함께 가져온다.

```sql
-- @EntityGraph 적용 후: 1번 쿼리로 끝남
SELECT o.*, p.* FROM orders o LEFT JOIN products p ON o.product_id = p.id;
```

**왜 기본값을 EAGER로 바꾸지 않나**: EAGER는 모든 곳에서 항상 JOIN한다. 상품 정보가 필요 없는 곳에서도 불필요한 JOIN이 발생한다. LAZY를 기본으로 하고, 필요한 쿼리에서만 `@EntityGraph`로 선택적으로 JOIN하는 것이 효율적이다.

### OrderService — 서버 측 금액 계산과 분산 락

```kotlin
@Transactional
fun createOrder(request: CreateOrderRequest): OrderResponse {
    return distributedLockManager.withLock("product:${request.productId}") {
        val product = productRepository.findByProductId(request.productId)
            ?: throw ProductNotFoundException()

        product.decreaseStock(request.quantity)

        // 서버에서 총 금액을 계산한다. 클라이언트가 보낸 금액을 신뢰하지 않는다.
        val totalAmount = product.price.multiply(request.quantity.toBigDecimal())

        val order = Order(
            product = product,
            quantity = request.quantity,
            totalAmount = totalAmount,
            customerName = request.customerName,
        )

        OrderResponse.from(orderRepository.save(order))
    }
}
```

**왜 금액을 서버에서 계산하나**: 클라이언트가 `totalAmount: 100`을 보내면 1만원짜리 상품을 100원에 살 수 있다. 금액은 반드시 서버에서 `상품 가격 × 수량`으로 계산해야 한다. 클라이언트의 요청에는 `productId`와 `quantity`만 포함하고, `totalAmount` 필드 자체가 없다.

**왜 분산 락 안에서 조회+차감을 하나**: "재고 확인 → 재고 차감" 사이에 다른 스레드가 끼어들면 두 스레드 모두 "재고 있음"을 확인하고 동시에 차감할 수 있다. 이를 TOCTOU(Time Of Check to Time Of Use) 문제라 한다. 분산 락으로 이 구간 전체를 원자적으로 실행한다.

---

## 10. Payment 도메인 구현 — 멱등성과 트랜잭션 안전성

> 3주차에 구현한 마지막 도메인. 결제 시스템의 핵심인 "돈"을 다루므로 방어적 코딩이 가장 중요한 영역이다.

### 왜 결제 승인이 200 OK이고 201 Created가 아닌가?

멱등성 키가 이미 존재하면 새 리소스를 생성하지 않고 기존 결과를 반환한다. "새로 만들었을 수도 있고 아닐 수도 있는" 응답에 201 Created는 부적절하다. 요청이 성공적으로 처리됐다는 의미의 200 OK가 맞다.

### Payment 엔티티 — 멱등성 키와 상태 전이

```kotlin
@Entity
@Table(name = "payments")
class Payment(
    order: Order,
    amount: BigDecimal,
    paymentMethod: PaymentMethod,
    idempotencyKey: String,
) {
    @Column(nullable = false, unique = true, length = 36)
    val paymentKey: String = UUID.randomUUID().toString()

    @Column(nullable = false, unique = true)
    val idempotencyKey: String = idempotencyKey  // 중복 결제 방지

    var cancelReason: String? = null
        protected set

    fun approve() {
        if (status != PaymentStatus.READY) throw AlreadyPaidException()
        status = PaymentStatus.DONE
    }

    fun cancel(reason: String) {
        if (status != PaymentStatus.DONE) throw InvalidPaymentStatusException()
        status = PaymentStatus.CANCELLED
        cancelReason = reason
    }
}
```

**paymentKey vs idempotencyKey — 두 UUID의 역할이 다르다**:
- `paymentKey`: 서버가 생성. 결제 건을 외부에서 식별하는 용도 (취소할 때 사용: `POST /payments/{paymentKey}/cancel`).
- `idempotencyKey`: 클라이언트가 생성. 같은 결제 요청의 중복을 방지하는 용도. DB에 UNIQUE 제약으로 물리적으로 중복을 차단한다.

**상태 전이**:
```
READY ──→ DONE ──→ CANCELLED
  (승인)      (취소)
```

### PaymentService — 멱등성 구현의 전체 흐름

```kotlin
@Transactional
fun confirmPayment(request: ConfirmPaymentRequest): PaymentResponse {
    // 1단계: 멱등성 키로 기존 결제 확인
    val existingPayment = paymentRepository.findByIdempotencyKey(request.idempotencyKey)
    if (existingPayment != null) {
        validateIdempotencyRequest(existingPayment, request)
        return PaymentResponse.from(existingPayment)  // 새로 만들지 않고 기존 결과 반환
    }

    // 2단계: 주문 조회 + 이미 결제된 주문 확인
    val order = orderRepository.findByOrderId(request.orderId)
        ?: throw OrderNotFoundException()
    if (order.status == OrderStatus.PAID) throw AlreadyPaidException()

    // 3단계: 금액 검증
    if (order.totalAmount.compareTo(request.amount) != 0) {
        throw PaymentAmountMismatchException(expected = order.totalAmount, actual = request.amount)
    }

    // 4단계: 결제 생성 + Race Condition 방어
    val payment = Payment(order = order, amount = request.amount, ...)
    val savedPayment = try {
        paymentRepository.saveAndFlush(payment)
    } catch (e: DataIntegrityViolationException) {
        // 동시 요청이 먼저 INSERT에 성공한 경우
        val concurrentPayment = paymentRepository.findByIdempotencyKey(request.idempotencyKey)
            ?: throw e
        return PaymentResponse.from(concurrentPayment)
    }

    // 5단계: 결제 승인 + 주문 상태 변경 (같은 트랜잭션)
    savedPayment.approve()
    order.markAsPaid()

    return PaymentResponse.from(savedPayment)
}
```

#### 멱등성 흐름을 시나리오로 이해하기

**시나리오 1: 정상 요청**
```
클라이언트 → idempotencyKey="abc123"
서버: findByIdempotencyKey("abc123") → null (처음)
서버: 주문 확인 → 금액 검증 → Payment INSERT → approve → 200 OK
```

**시나리오 2: 재시도 (네트워크 오류 후)**
```
클라이언트 → idempotencyKey="abc123" (같은 키로 재시도)
서버: findByIdempotencyKey("abc123") → Payment 존재!
서버: orderId, amount 일치 확인 → 기존 결과 그대로 반환 → 200 OK
```

**시나리오 3: 동시 요청 (Race Condition)**
```
Thread A: findByIdempotencyKey → null
Thread B: findByIdempotencyKey → null (동시에 조회)
Thread A: saveAndFlush → INSERT 성공 (UNIQUE 획득)
Thread B: saveAndFlush → DataIntegrityViolationException! (UNIQUE 위반)
Thread B: catch에서 재조회 → Thread A의 결과 반환
```

#### 왜 save가 아닌 saveAndFlush인가

```kotlin
val savedPayment = paymentRepository.saveAndFlush(payment)  // 즉시 INSERT
```

`save()`는 JPA의 영속성 컨텍스트에만 저장하고 실제 INSERT는 트랜잭션 커밋 시점에 실행한다. 커밋까지 수 초가 걸릴 수 있으므로 그 사이에 동일한 `idempotencyKey`로 들어온 Thread B가 "아직 없네"라고 판단하고 또 INSERT를 시도한다.

`saveAndFlush()`는 즉시 DB에 INSERT를 실행한다. UNIQUE 제약 위반이 바로 발생하므로 `DataIntegrityViolationException`을 catch해서 처리할 수 있다.

#### 왜 BigDecimal.compareTo()로 비교하나

```kotlin
if (order.totalAmount.compareTo(request.amount) != 0)
```

`BigDecimal.equals()`는 **스케일(소수점 자릿수)**까지 비교한다:
```kotlin
BigDecimal("10000.00").equals(BigDecimal("10000"))  // false! (스케일 다름)
BigDecimal("10000.00").compareTo(BigDecimal("10000"))  // 0 (같은 수)
```

주문 금액이 `10000.00`이고 클라이언트가 `10000`을 보내면, `equals()`로 비교하면 유효한 결제가 거부된다. `compareTo()`는 수치 값만 비교하므로 스케일 차이를 무시한다.

### 결제 취소 — 3종 롤백 보장

```kotlin
@Transactional
fun cancelPayment(paymentKey: String, request: CancelPaymentRequest): PaymentResponse {
    val payment = paymentRepository.findByPaymentKey(paymentKey)
        ?: throw PaymentNotFoundException()

    payment.cancel(request.cancelReason)    // 1. 결제 취소 (DONE → CANCELLED)
    payment.order.markAsCancelled()         // 2. 주문 취소 (PAID → CANCELLED)
    payment.order.product.increaseStock(payment.order.quantity)  // 3. 재고 복원

    return PaymentResponse.from(payment)
}
```

**왜 하나의 트랜잭션에서 처리하나**: "결제는 취소됐는데 재고가 복원 안 됐다"는 상황이 발생하면 안 된다. `@Transactional` 안에서 세 가지 변경이 모두 성공하거나, 하나라도 실패하면 전부 롤백된다. 이것이 트랜잭션의 원자성(Atomicity)이다.

**EntityGraph가 핵심**: `findByPaymentKey()`에 `@EntityGraph(attributePaths = ["order", "order.product"])`가 설정되어 있어서 한 번의 JOIN 쿼리로 Payment → Order → Product를 모두 가져온다. 없으면 `payment.order` 접근 시 SELECT 1번, `order.product` 접근 시 SELECT 1번, 총 3번 쿼리가 나간다.

---

## 11. 동시성 제어 — 분산 락과 낙관적 락 이중 방어

> 4주차의 핵심. 재고 차감처럼 여러 스레드가 동시에 같은 자원을 수정할 때 데이터 정합성을 지키는 방법.

### 왜 동시성 제어가 필요한가 — 재고 차감 시나리오

재고 10개인 상품에 두 사람이 동시에 주문한다:

```
시각    Thread A (5개 주문)          Thread B (5개 주문)
────────────────────────────────────────────────────
t1     재고 조회: 10개               재고 조회: 10개
t2     10 >= 5 → OK                  10 >= 5 → OK
t3     재고 = 10 - 5 = 5             재고 = 10 - 5 = 5  ← 둘 다 5!
t4     DB 저장: stock = 5            DB 저장: stock = 5
```

**결과**: 총 10개가 주문됐는데 재고가 5로 기록된다. 5개가 증발했다. 이를 **Lost Update(갱신 손실)** 문제라 한다.

### 해결 전략: 3단계 방어

```
[1단계] Redisson 분산 락 ─── 99.9% 충돌 방지 (대기열 방식)
         │
[2단계] JPA 낙관적 락(@Version) ─── 0.1% 엣지 케이스 차단 (버전 비교)
         │
[3단계] DB UNIQUE 제약 ─── 최종 안전망 (멱등성 키 중복 차단)
```

### 1단계: Redisson 분산 락

```kotlin
@Component
class DistributedLockManager(private val redissonClient: RedissonClient) {
    companion object {
        private const val WAIT_TIME_SECONDS = 5L   // 락 획득 대기 시간
        private const val LEASE_TIME_SECONDS = 10L  // 락 자동 만료 시간
    }

    fun <T> withLock(lockKey: String, block: () -> T): T {
        val lock = redissonClient.getLock("lock:$lockKey")
        val acquired = lock.tryLock(WAIT_TIME_SECONDS, LEASE_TIME_SECONDS, TimeUnit.SECONDS)

        if (!acquired) throw LockAcquisitionFailedException()

        try {
            return block()
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }
}
```

**왜 Redisson인가 (Lettuce와 비교)**:
- Lettuce: Spring Data Redis의 기본 클라이언트. 캐싱에 적합하지만 분산 락 기능이 없다. 직접 구현하면 SETNX + busy-wait(무한 폴링) 방식이 된다.
- Redisson: Redis Pub/Sub으로 락 해제를 감지한다. 락을 기다리는 스레드가 "알림 올 때까지 대기"하므로 CPU를 낭비하지 않는다.

**왜 두 클라이언트를 동시에 사용하나**: Lettuce는 `@Cacheable` 캐싱에, Redisson은 분산 락에 사용한다. 각자의 연결 풀을 독립적으로 관리하므로 서로 간섭하지 않는다.

#### 락 키 설계: `product:{productId}`

```kotlin
distributedLockManager.withLock("product:${request.productId}") {
    // 재고 조회 + 차감 + 주문 저장
}
```

**왜 productId 단위로 락을 거나**: 상품 A를 주문하는 것과 상품 B를 주문하는 것은 서로 관련이 없다. "전체 주문"에 락을 걸면 상품 B 주문이 상품 A 주문 완료를 기다려야 한다. productId 단위 락은 같은 상품에 대한 동시 주문만 직렬화하고, 다른 상품 주문은 병렬로 처리된다.

#### WAIT_TIME과 LEASE_TIME

- **WAIT_TIME (5초)**: 락을 획득하지 못하면 최대 5초 대기. 5초 후에도 못 얻으면 409 `LOCK_ACQUISITION_FAILED` 반환. 클라이언트는 재시도할 수 있다.
- **LEASE_TIME (10초)**: 락을 획득한 후 10초가 지나면 자동 만료. 서버 장애로 `unlock()`이 호출되지 않아도 락이 영원히 잠기지 않는다.

#### isHeldByCurrentThread 체크가 중요한 이유

```kotlin
finally {
    if (lock.isHeldByCurrentThread) {
        lock.unlock()
    }
}
```

LEASE_TIME(10초)이 지나면 락이 자동 만료된다. 이후 다른 스레드가 같은 락을 획득할 수 있다. 원래 스레드의 `finally` 블록이 뒤늦게 실행되어 `unlock()`을 호출하면, 다른 스레드의 락을 풀어버리는 사고가 발생한다. `isHeldByCurrentThread` 체크로 "아직 내가 가진 락인지" 확인 후 해제한다.

### 2단계: JPA 낙관적 락 (@Version)

```kotlin
@Entity
class Product {
    @Version
    var version: Long = 0
        protected set
}
```

JPA는 `@Version` 필드가 있으면 UPDATE SQL에 자동으로 버전 조건을 추가한다:

```sql
-- JPA가 생성하는 SQL
UPDATE products SET stock = 5, version = 1
WHERE id = 42 AND version = 0;  -- 내가 읽은 버전과 같은지 확인

-- 영향받은 행이 0이면 → 다른 트랜잭션이 이미 수정한 것 → 예외 발생
```

**왜 분산 락이 있는데 낙관적 락도 필요한가**: 분산 락이 해제된 후 트랜잭션이 커밋되기 전 찰나의 순간에 다른 스레드가 끼어들 수 있다.

```
Thread A: 분산 락 획득 → 재고 차감 → 분산 락 해제 → ... → 트랜잭션 커밋
Thread B:                                        ↑ 이 틈에 분산 락 획득 → 재고 조회
```

낙관적 락은 이 0.1% 엣지 케이스에서 DB 레벨에서 Lost Update를 차단한다. 두 겹의 방어가 서로 다른 시점의 위험을 커버한다.

### Redisson 설정 — 왜 starter가 아닌 plain 라이브러리를 사용했나

```kotlin
// build.gradle.kts
implementation("org.redisson:redisson:3.40.0")  // starter가 아닌 core 라이브러리
```

`redisson-spring-boot-starter`는 Spring Boot의 자동 설정(auto-configuration)에 의존한다. Spring Boot 4.x(Spring Framework 7.x)는 큰 변경이 있어서 3.x용 starter가 호환되지 않을 수 있다. plain 라이브러리를 사용하고 `RedissonConfig`에서 수동으로 빈을 등록하면 버전 호환성 문제를 피할 수 있다.

```kotlin
@Configuration
class RedissonConfig(
    @Value("\${spring.data.redis.host}") private val host: String,
    @Value("\${spring.data.redis.port}") private val port: Int,
) {
    @Bean(destroyMethod = "shutdown")
    fun redissonClient(): RedissonClient {
        val config = Config()
        config.useSingleServer().setAddress("redis://$host:$port")
        return Redisson.create(config)
    }
}
```

**destroyMethod = "shutdown"**: 앱 종료 시 Redisson의 Netty 스레드 풀을 정상 종료한다. 이게 없으면 스레드가 좀비로 남아 자원 누수가 발생한다.

### 동시성 통합 테스트

```kotlin
@Disabled("로컬 Docker 환경 필요")
@SpringBootTest
class ConcurrencyTest {
    @Test
    fun `100개 동시 주문에서 재고 10개만 성공한다`() {
        // 재고 10개 상품 생성
        val product = productRepository.save(Product("테스트", BigDecimal("1000"), 10))

        val threadCount = 100
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        repeat(threadCount) {
            executor.submit {
                try {
                    orderService.createOrder(CreateOrderRequest(
                        productId = product.productId,
                        quantity = 1,
                        customerName = "고객$it"
                    ))
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        assertEquals(10, successCount.get())  // 정확히 10개만 성공
        assertEquals(90, failCount.get())     // 90개는 재고 부족으로 실패
    }
}
```

**왜 @Disabled인가**: 이 테스트는 실제 MySQL과 Redis가 실행 중이어야 한다. Docker가 없는 CI 환경에서 실행하면 실패하므로 기본적으로 비활성화하고, Docker를 띄운 후 수동으로 실행한다.

**CountDownLatch의 역할**: 100개 스레드가 모두 준비된 후 동시에 시작하게 한다. latch 없이 submit하면 먼저 생성된 스레드가 먼저 실행되어 동시성 시나리오가 아니라 순차 시나리오가 된다.

---

## 12. Redis 캐싱 전략

> 4주차에 동시성 제어와 함께 구현. 상품 조회의 DB 부하를 줄이기 위한 캐싱 전략.

### 왜 캐싱이 필요한가

상품 목록은 모든 사용자가 자주 조회하지만 내용은 자주 바뀌지 않는다. 매번 DB에 SELECT 쿼리를 날리면 불필요한 부하가 발생한다. Redis에 결과를 캐시하면 DB를 거치지 않고 바로 응답할 수 있다.

### CacheConfig — 캐시별 TTL 분리

```kotlin
@Configuration
@EnableCaching
class CacheConfig {
    companion object {
        const val PRODUCT_LIST = "productList"
        const val PRODUCT_DETAIL = "productDetail"

        private val PRODUCT_LIST_TTL = Duration.ofMinutes(5)    // 목록: 5분
        private val PRODUCT_DETAIL_TTL = Duration.ofMinutes(10)  // 상세: 10분
    }
}
```

**왜 TTL이 다른가**:
- 상품 **목록**: 새 상품 등록, 재고 변경 등으로 자주 바뀐다 → 5분으로 짧게.
- 상품 **상세**: 특정 상품의 이름이나 가격은 자주 바뀌지 않는다 → 10분으로 길게.
- TTL이 길수록 캐시 히트율은 높지만 데이터 신선도는 떨어진다. 도메인 특성에 맞게 조절한다.

### @Cacheable / @CacheEvict — 캐시 어노테이션 활용

```kotlin
@Cacheable(cacheNames = [CacheConfig.PRODUCT_LIST])
fun getProducts(): List<ProductResponse> =
    productRepository.findAll().map { ProductResponse.from(it) }

@Cacheable(cacheNames = [CacheConfig.PRODUCT_DETAIL], key = "#productId")
fun getProduct(productId: String): ProductResponse { ... }

@Transactional
@CacheEvict(cacheNames = [CacheConfig.PRODUCT_LIST], allEntries = true)
fun createProduct(request: CreateProductRequest): ProductResponse { ... }
```

**동작 원리**:
1. `getProducts()` 첫 호출: DB 쿼리 실행 → 결과를 Redis에 저장 → 응답 반환
2. `getProducts()` 두 번째 호출: Redis에서 캐시 조회 → DB 쿼리 없이 바로 반환
3. `createProduct()` 호출: 상품 생성 후 `@CacheEvict`로 목록 캐시를 삭제 → 다음 `getProducts()` 호출 시 DB에서 최신 데이터를 가져옴

**key = "#productId"**: SpEL(Spring Expression Language)로 메서드 파라미터를 캐시 키에 사용한다. 상품별로 개별 캐시 항목이 생긴다 (`productDetail::550e8400-...`).

### 캐시 무효화 전략 — 재고 변경 시

상품 조회 캐시에 재고 수량이 포함되어 있다. 주문(재고 차감)이나 결제 취소(재고 복원) 시 캐시를 무효화하지 않으면 틀린 재고 정보가 응답된다.

```kotlin
// OrderService.createOrder()
@Caching(evict = [
    CacheEvict(cacheNames = [CacheConfig.PRODUCT_LIST], allEntries = true),
    CacheEvict(cacheNames = [CacheConfig.PRODUCT_DETAIL], allEntries = true),
])
fun createOrder(request: CreateOrderRequest): OrderResponse { ... }

// PaymentService.cancelPayment()
@Caching(evict = [
    CacheEvict(cacheNames = [CacheConfig.PRODUCT_LIST], allEntries = true),
    CacheEvict(cacheNames = [CacheConfig.PRODUCT_DETAIL], allEntries = true),
])
fun cancelPayment(paymentKey: String, request: CancelPaymentRequest): PaymentResponse { ... }
```

**왜 allEntries = true인가**: 주문 시 어떤 상품의 재고가 변했는지 특정할 수 있지만, 목록 캐시는 모든 상품을 포함하므로 전체를 무효화해야 한다. 상세 캐시도 안전하게 전체 무효화한다.

### JDK 직렬화를 선택한 이유

```kotlin
// CacheConfig에서 값 직렬화 방식 설정
.serializeValuesWith(
    RedisSerializationContext.SerializationPair.fromSerializer(
        RedisSerializer.java()  // JDK 기본 직렬화
    )
)
```

**Jackson 직렬화 대안**: `GenericJackson2JsonRedisSerializer`를 사용하면 Redis에 JSON으로 저장되어 `redis-cli`에서 읽을 수 있다. 하지만 Spring Boot 4.x(Jackson 3.x)에서 API 호환성 이슈가 있을 수 있다.

**JDK 직렬화 장단점**:
- 장점: Java/Kotlin 버전에 무관하게 안정적. 별도 설정 불필요.
- 단점: Redis에 바이너리로 저장되어 `redis-cli`에서 사람이 읽을 수 없다.
- 이 프로젝트에서는 안정성을 우선시하여 JDK 직렬화를 선택했다.

---

## 13. 단위 테스트 전략

> 3주차에 전 도메인 Service 단위 테스트를 작성. DB 없이 빠르게 비즈니스 로직을 검증한다.

### 왜 단위 테스트가 필요한가

- **빠른 피드백**: 통합 테스트는 Spring Context 로딩 + DB 연결에 10초 이상 걸린다. 단위 테스트는 1초 이내로 실행된다.
- **격리된 검증**: DB, Redis, 네트워크 상태와 무관하게 순수 비즈니스 로직만 검증한다.
- **리팩토링 안전망**: 코드를 수정해도 테스트가 통과하면 동작이 바뀌지 않았음을 보장한다.

### MockK — Kotlin용 모킹 프레임워크

```kotlin
class OrderServiceTest {
    private lateinit var orderRepository: OrderRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var distributedLockManager: DistributedLockManager
    private lateinit var orderService: OrderService

    @BeforeEach
    fun setUp() {
        orderRepository = mockk()
        productRepository = mockk()
        distributedLockManager = mockk()

        // 분산 락을 모킹: 실제 Redis 없이 block을 바로 실행
        @Suppress("UNCHECKED_CAST")
        every { distributedLockManager.withLock(any<String>(), any<() -> Any>()) } answers {
            (args[1] as () -> Any).invoke()
        }

        orderService = OrderService(orderRepository, productRepository, paymentRepository, distributedLockManager)
    }
}
```

**왜 Mockito가 아닌 MockK인가**: Kotlin 클래스는 기본적으로 `final`이다. Mockito는 `final` 클래스를 모킹할 수 없다 (프록시 기반이라 상속이 필요). MockK는 Kotlin 바이트코드를 직접 조작해 `final` 클래스도 모킹할 수 있다.

**분산 락 모킹이 핵심**: 단위 테스트에서는 Redis가 없으므로 분산 락을 모킹한다. `withLock(key, block)`이 호출되면 block을 그냥 실행하도록 설정한다. 이렇게 하면 실제 락 없이 비즈니스 로직만 테스트할 수 있다.

**`any<() -> Any>()`의 의미**: Kotlin 제네릭 함수의 타입 추론 문제. `withLock<T>`의 `T`를 컴파일러가 추론할 수 없으므로 람다 파라미터 타입을 명시적으로 지정한다.

### 리플렉션으로 테스트 엔티티 생성

```kotlin
object TestFixtures {
    fun setField(target: Any, fieldName: String, value: Any) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    fun createProduct(id: Long = 1L, name: String = "테스트 상품",
                      price: BigDecimal = BigDecimal("15000.00"), stock: Int): Product {
        val product = Product(name = name, price = price, stock = stock)
        setField(product, "id", id)            // DB가 생성해야 하는 값
        setField(product, "createdAt", LocalDateTime.now())  // 감사(Auditing) 값
        setField(product, "updatedAt", LocalDateTime.now())
        return product
    }
}
```

**왜 리플렉션이 필요한가**: JPA 엔티티의 `id`는 `@GeneratedValue`로 DB가 자동 생성한다. `createdAt`은 `@CreatedDate`로 Spring Auditing이 설정한다. 단위 테스트에는 DB도 Spring도 없으므로 이 값들이 null이다. `OrderResponse.from(order)`에서 `requireNotNull(order.id)`가 실패한다. 리플렉션으로 직접 값을 세팅해 DB 저장을 시뮬레이션한다.

### 테스트 케이스 설계 — 정상/예외/경계값

```kotlin
@Test
fun `상품을 정상적으로 등록한다`() {
    // 정상 케이스: 기본 동작 검증
    val request = CreateProductRequest(name = "노트북", price = BigDecimal("1500000.00"), stock = 100)
    every { productRepository.save(any<Product>()) } returns savedProduct

    val response = productService.createProduct(request)
    assertEquals("노트북", response.name)
}

@Test
fun `존재하지 않는 상품 조회 시 예외가 발생한다`() {
    // 예외 케이스: 에러 경로 검증
    every { productRepository.findByProductId("non-existent") } returns null

    assertThrows<ProductNotFoundException> {
        productService.getProduct("non-existent")
    }
}

@Test
fun `재고 부족 시 예외가 발생한다`() {
    // 경계값 케이스: 재고 3개에 5개 주문
    val product = createProduct(stock = 3)
    val request = CreateOrderRequest(productId = product.productId, quantity = 5, customerName = "홍길동")
    every { productRepository.findByProductId(product.productId) } returns product

    assertThrows<OutOfStockException> {
        orderService.createOrder(request)
    }
}

@Test
fun `서버에서 총 금액을 계산하므로 클라이언트 금액은 무시된다`() {
    // 보안 케이스: 서버 측 계산 검증
    val product = createProduct(price = BigDecimal("10000.00"), stock = 100)
    every { productRepository.findByProductId(product.productId) } returns product
    every { orderRepository.save(any<Order>()) } answers { ... }

    val response = orderService.createOrder(request)
    assertEquals(BigDecimal("30000.00"), response.totalAmount)  // 10000 × 3
}
```

**테스트 메서드명이 한글인 이유**: `` `재고 부족 시 예외가 발생한다`() `` 형식은 테스트가 실패했을 때 "무엇이 잘못됐는지"를 한눈에 알 수 있다. `testCreateOrderWithInsufficientStock`보다 직관적이다.

### 전체 테스트 현황

| 테스트 파일 | 테스트 수 | 검증 항목 |
|------------|:---------:|----------|
| ProductServiceTest | 4 | 등록, 목록 조회, 상세 조회, 없는 상품 예외 |
| OrderServiceTest | 5 | 주문 생성+재고 차감, 상품 없음, 재고 부족, orderId 조회, 서버 금액 계산 |
| PaymentServiceTest | 6 | 결제 승인, 멱등성 키 중복, 주문 없음, 금액 불일치, 취소+재고 복원, 상태 전이 예외 |
| ConcurrencyTest | 1 | 100스레드 동시 주문 → 재고 정확성 (@Disabled) |

---

## 14. DB 스키마 설계

> schema.sql로 수동 관리. `ddl-auto: none`으로 Hibernate가 스키마를 건드리지 않는다.

### 왜 schema.sql을 수동으로 관리하나

개발 초기에는 `ddl-auto: create`로 Hibernate가 자동으로 테이블을 만들었다. 하지만 3주차 이후 `none`으로 변경한 이유:

1. **운영 환경과 같은 방식**: 운영에서는 절대 Hibernate가 스키마를 변경하지 않는다. `schema.sql`로 관리하면 운영과 동일한 방식으로 개발한다.
2. **스키마 버전 관리**: `schema.sql`은 git으로 추적되므로 스키마 변경 이력을 확인할 수 있다.
3. **안전성**: `ddl-auto: update`는 컬럼 추가는 하지만 삭제나 타입 변경은 하지 않는다. 의도와 다른 스키마가 만들어질 수 있다.

### 주요 설계 결정

```sql
CREATE TABLE IF NOT EXISTS products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id VARCHAR(36) NOT NULL,
    price DECIMAL(15, 2) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_id (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

**DECIMAL(15, 2)**: 금액은 절대 FLOAT/DOUBLE로 저장하지 않는다. DB에서도 정확한 십진수 연산을 보장한다.

**version BIGINT DEFAULT 0**: 낙관적 락용. JPA의 `@Version`과 매핑된다.

**UNIQUE KEY uk_product_id**: UUID 기반 외부 식별자의 유일성을 DB 레벨에서 보장한다.

**InnoDB**: ACID 트랜잭션 지원, 행 레벨 잠금, 외래 키 지원. MyISAM은 트랜잭션을 지원하지 않으므로 결제 시스템에 부적합하다.

**utf8mb4**: 한글, 이모지 등 4바이트 문자를 지원한다. `utf8`(3바이트)은 이모지를 저장할 수 없다.

```sql
CREATE TABLE IF NOT EXISTS payments (
    idempotency_key VARCHAR(255) NOT NULL,
    cancel_reason VARCHAR(500) NULL,
    UNIQUE KEY uk_idempotency_key (idempotency_key),
);
```

**idempotency_key UNIQUE**: 멱등성의 최종 안전장치. 애플리케이션 코드의 `saveAndFlush()` + catch 로직이 실패하더라도 DB가 물리적으로 중복 INSERT를 막는다.

**cancel_reason NULL 허용**: 결제 생성 시에는 취소 사유가 없고, 취소 시에만 설정된다.

---

## 15. 포트 충돌 트러블슈팅 — 진짜 원인은 MariaDB였다

> 1주차 환경 세팅에서 겪은 두 번째 대형 트러블슈팅. 에러 메시지만 보면 Connector/J 문제처럼 보이지만, 실제 원인은 전혀 달랐다.

### 증상

앱이 DB에 연결하면 `auth_gssapi_client` 플러그인 로드 오류 발생. Connector/J 버전을 바꿔도 해결되지 않았다.

### 핵심 발견

포트 3307에 raw TCP 연결을 시도해봤다:

```bash
timeout 2 bash -c 'cat < /dev/tcp/localhost/3307' 2>/dev/null | od -c | head -3
```

응답에 `11.4.5-MariaDB`가 나왔다. **Docker MySQL이 아닌 로컬 MariaDB**가 응답하고 있었다!

**원인 체인**:
1. 호스트 머신에 MariaDB 11.4.5가 포트 3307에서 실행 중이었다
2. Docker MySQL도 호스트 포트 3307로 매핑했다
3. 앱이 `localhost:3307`에 연결하면 로컬 MariaDB가 먼저 연결을 처리했다
4. MariaDB가 핸드쉐이크에서 `auth_gssapi_client` 인증을 요청했다
5. Connector/J 9.x가 이 플러그인을 찾지 못해 오류 발생

### 해결

```yaml
# docker-compose.yml
ports:
  - "3308:3306"  # 3307(MariaDB 충돌) → 3308

# application.yml
url: jdbc:mysql://localhost:3308/payment
```

### 교훈

- **에러 메시지를 맹신하지 말 것**: `auth_gssapi_client` 오류는 Connector/J 문제처럼 보이지만, 실제로는 "잘못된 서버에 연결"한 것이었다.
- **raw 핸드쉐이크로 서버 신원 확인**: Docker 컨테이너 내부(`docker exec`)로 확인하면 정상이지만, 호스트에서 접근하면 다른 서버가 응답할 수 있다.
- **포트 점유 확인을 습관화**: `netstat -ano | findstr :포트번호`로 먼저 확인한다.

---

## 16. 코드 리뷰에서 발견한 14가지 이슈와 해결

> 3주차 완료 후 전체 코드를 리뷰하며 발견한 14건의 이슈. 치명적 3건, 경고 5건, 권고 6건으로 분류하고 우선순위 순으로 수정했다.

### 치명적 이슈 3건

#### 1. schema.sql이 실행되지 않는 문제

**증상**: `ddl-auto: none`으로 바꿨는데 테이블이 생성되지 않았다.

**원인**: Spring Boot는 기본적으로 embedded DB(H2 등)에서만 `schema.sql`을 실행한다. 외부 DB(MySQL)에서는 `spring.sql.init.mode: always`를 명시해야 한다. 또한 JPA 초기화보다 SQL이 먼저 실행되면 외래 키 참조 오류가 발생하므로 `defer-datasource-initialization: true`도 필요하다.

```yaml
spring:
  sql:
    init:
      mode: always  # 외부 DB에서도 schema.sql 실행
  jpa:
    defer-datasource-initialization: true  # JPA 초기화 후 SQL 실행
```

#### 2. 멱등성 키 Race Condition

**증상**: 동시에 같은 `idempotencyKey`로 두 요청이 들어오면 둘 다 "없다"고 판단해 이중 결제 발생.

**원인**: `save()`는 트랜잭션 커밋까지 INSERT를 지연한다. 커밋 전에 두 번째 요청이 조회하면 첫 번째 결제를 찾지 못한다.

**해결**: `save()` → `saveAndFlush()` + `DataIntegrityViolationException` catch 후 재조회. [10장 참고](#10-payment-도메인-구현--멱등성과-트랜잭션-안전성)

#### 3. cancelReason 필드 누락

**증상**: 결제 취소 시 취소 사유를 받지만 DB에 저장하지 않았다.

**해결**: Payment 엔티티에 `cancelReason: String?` 필드, `cancel(reason: String)` 시그니처, schema에 `cancel_reason` 컬럼 추가.

### 경고 이슈 5건

#### 4. PaymentService에 미사용 ProductRepository

생성자에서 `productRepository`를 주입받았지만 사용하지 않았다. 재고 복원은 `order.product`로 접근하므로 직접 의존이 불필요하다. 제거했다.

#### 5. Product에 UUID 외부 식별자 없음

Order와 Payment에는 `orderId`, `paymentKey`라는 UUID가 있었지만 Product에는 없었다. API에서 내부 PK(`id: Long`)를 노출하고 있어 보안 문제와 API 일관성 문제가 있었다. `productId: String(UUID)` 추가.

#### 6. 이미 결제된 주문 재결제 미검증

`confirmPayment`에서 멱등성 키 체크 후 바로 결제를 진행했다. 같은 주문에 대해 다른 멱등성 키로 결제를 시도하면 이중 결제가 가능했다. `order.status == PAID` 체크 추가.

#### 7. LAZY 프록시 문제

`OrderRepository.findByOrderId()`에 `@EntityGraph`가 없어서 `order.product.name` 접근 시 `LazyInitializationException` 발생 가능. `@EntityGraph(attributePaths = ["product"])` 추가.

#### 8. Order/Payment에 낙관적 락 없음

Product에만 `@Version`이 있었다. 동시에 같은 주문/결제의 상태를 변경하면 Lost Update 발생 가능. 두 엔티티에 `@Version` 추가.

### 권고 이슈 6건

| # | 이슈 | 해결 |
|---|------|------|
| 9 | 멱등성 재요청 시 orderId/amount 검증 미비 | `validateIdempotencyRequest()` 추가 |
| 10 | 테스트 헬퍼 함수 중복 | `TestFixtures.kt` 공통 파일 생성 |
| 11 | 재고 0개 상품 등록 허용 | `@Min(0)` → `@Min(1)` |
| 12-13 | 미사용 import | 제거 |
| 14 | 통합 테스트 Docker 의존 | `@Disabled` 추가 |

### 이 리뷰에서 배운 것

- **방어적 코딩은 "정상 케이스"만으로는 부족하다**: 이미 결제된 주문 재결제, 멱등성 키 값 불일치 같은 예외 시나리오를 놓치기 쉽다.
- **의존성 정리**: 사용하지 않는 주입은 즉시 제거한다. 코드를 읽는 사람이 "이 Repository가 여기서 어떤 역할을 하지?"라고 혼란스러워한다.
- **일관성**: Product만 UUID가 없으면 "왜 Product만 다르지?"라는 의문이 생긴다. 설계 원칙을 모든 도메인에 일관되게 적용한다.

---

*이 파일은 프로젝트 전 과정(1~5주차)의 구현 결정과 이유를 기록한 복습 자료다. 코드가 무엇을 하는지보다 왜 그렇게 짰는지를 이해하는 것이 목표다.*
