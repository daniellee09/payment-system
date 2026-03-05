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
7. [앞으로 구현할 아키텍처 개념들](#7-앞으로-구현할-아키텍처-개념들)

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

### Redisson을 주석 처리한 이유

```kotlin
// implementation("org.redisson:redisson-spring-boot-starter:3.25.0")
```

Redisson은 분산 락(4주차)에서 사용할 라이브러리다. 주석 처리한 이유는 **Spring Boot 4.x 호환성이 확인되지 않았기 때문**이다. Spring Boot 4.0은 Spring Framework 7.x 기반으로 큰 변경이 있었고, 구버전 서드파티 라이브러리가 동작하지 않을 수 있다. 실제로 필요한 시점(4주차)에 호환 버전을 확인 후 추가하는 게 맞다. 지금 넣었다가 다른 의존성과 충돌이 나면 원인 파악이 어려워진다.

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

## 7. 앞으로 구현할 아키텍처 개념들

> 아직 구현하지 않았지만, 미리 개념을 이해하고 구현할 때 이유를 알고 짜기 위한 메모.

### ApiResponse<T> — 왜 공통 응답 래퍼가 필요한가

API를 사용하는 클라이언트(앱, 프론트엔드) 입장에서는 응답 형식이 일관되어야 처리하기 쉽다. 어떤 API는 데이터를 바로 반환하고, 어떤 API는 `{ data: ... }`로 감싸고, 어떤 API는 에러가 나면 HTML을 반환한다면 클라이언트 코드가 복잡해진다.

```json
// 성공
{ "success": true, "data": { "productId": "abc", "name": "노트북" } }

// 실패
{ "success": false, "error": { "code": "PRODUCT_NOT_FOUND", "message": "상품을 찾을 수 없습니다." } }
```

`success` 필드 하나로 성공/실패를 구분하고, 나머지는 항상 같은 위치에 있다. 클라이언트는 항상 동일한 방식으로 응답을 파싱할 수 있다.

---

### BusinessException + ErrorCode — 왜 예외를 계층화하나

```
Exception
└── RuntimeException
    └── BusinessException       ← 우리가 만드는 최상위 비즈니스 예외
        ├── OutOfStockException  (재고 부족)
        └── AlreadyPaidException (이미 결제됨)
```

**안 좋은 방식**:
```kotlin
throw RuntimeException("재고가 부족합니다")   // 어떤 에러인지 구분 불가
throw IllegalStateException("재고 부족")     // HTTP 상태 코드를 어떻게 결정?
```

**좋은 방식**:
```kotlin
// ErrorCode에 HTTP 상태 코드와 에러 메시지를 함께 정의
enum class ErrorCode(val status: HttpStatus, val message: String) {
    OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다"),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다")
}

throw BusinessException(ErrorCode.OUT_OF_STOCK)
```

`GlobalExceptionHandler`는 `BusinessException`을 잡아서 `ErrorCode`에 정의된 HTTP 상태 코드와 메시지로 `ApiResponse`를 만들어 응답한다. 새로운 에러를 추가할 때 `ErrorCode`에 항목 하나만 추가하면 된다. 여기저기 흩어진 try-catch와 응답 생성 코드를 중앙에서 관리한다.

---

### 낙관적 락 vs 비관적 락 — 재고 차감에는 무엇을 쓰나

재고 100개인 상품에 100명이 동시에 주문하면? 모두 "재고 있음"을 확인하고 동시에 차감하면 재고가 음수가 된다.

**비관적 락(Pessimistic Lock)**: DB에서 `SELECT ... FOR UPDATE`로 해당 행을 잠근다. 다른 트랜잭션은 잠금이 풀릴 때까지 기다린다. 동시 요청이 많으면 대기가 쌓여 성능이 떨어진다.

**낙관적 락(Optimistic Lock)**: DB를 잠그지 않는다. 대신 엔티티에 `@Version` 필드를 두고, 업데이트 시 내가 읽은 버전과 현재 버전이 같은지 확인한다. 다르면 `OptimisticLockException`을 던지고 재시도한다. 충돌이 드문 경우 성능이 더 좋다.

```kotlin
@Entity
class Product {
    @Version
    val version: Long = 0  // JPA가 자동으로 관리. UPDATE 시 version = version + 1
}
```

결제 시스템에서는 재고 차감처럼 **충돌이 자주 발생하는 경우** 낙관적 락의 재시도가 오히려 부하를 키울 수 있다. 이 프로젝트에서는 4주차에 Redis 분산 락으로 더 세밀하게 제어할 예정이다.

---

### 멱등성(Idempotency) — 왜 결제 승인에 필수인가

클라이언트가 결제 승인 요청을 보냈는데 응답이 안 온다. 네트워크 오류인가, 서버가 처리했는데 응답만 못 받은 건가? 클라이언트는 모른다. 그래서 재시도한다.

서버가 이미 처리한 요청을 또 처리하면 **이중 결제**가 발생한다.

**멱등성**: 같은 연산을 여러 번 수행해도 결과가 같은 성질.

```
클라이언트 → "idempotencyKey=abc123으로 결제 승인"
서버 → 처리 완료, DB에 저장

클라이언트 → (재시도) "idempotencyKey=abc123으로 결제 승인"
서버 → "abc123은 이미 처리된 키다" → 이전 결과 그대로 반환, 실제 처리 안 함
```

`idempotencyKey`는 클라이언트가 생성한 UUID다. 서버는 이 키를 기준으로 이미 처리된 요청인지 판단한다. 결제처럼 "두 번 하면 안 되는" 작업에 반드시 필요하다.

---

### 상태 전이 검증을 엔티티 내부에서 하는 이유

```kotlin
// 나쁜 예: Service에서 상태 체크
fun cancelPayment(paymentId: String) {
    val payment = paymentRepository.findByPaymentKey(paymentId)
    if (payment.status != PaymentStatus.PAID) {
        throw BusinessException(ErrorCode.INVALID_PAYMENT_STATUS)
    }
    payment.status = PaymentStatus.CANCELLED  // 외부에서 직접 상태 변경
}
```

```kotlin
// 좋은 예: 엔티티가 상태 전이를 책임진다
// Payment 엔티티 내부
fun cancel() {
    // 취소 가능한 상태인지 엔티티 스스로 검증
    check(status == PaymentStatus.PAID) {
        throw BusinessException(ErrorCode.INVALID_PAYMENT_STATUS)
    }
    this.status = PaymentStatus.CANCELLED
}

// Service
fun cancelPayment(paymentId: String) {
    val payment = paymentRepository.findByPaymentKey(paymentId)
    payment.cancel()  // 검증 + 상태 변경을 엔티티에 위임
}
```

엔티티가 자신의 상태 전이 규칙을 알고 있다. `cancel()`을 어디서 호출하든 검증이 항상 실행된다. Service가 검증을 깜빡할 수 없다. 도메인 규칙이 코드로 명확하게 표현된다(도메인 모델 패턴).

---

*이 파일은 프로젝트가 진행되면서 계속 업데이트된다.*
