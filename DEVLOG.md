# 개발 로그

작업 내역과 핵심 결정 사항을 기록한다.
큰 작업(도메인/기능 단위)과 작은 작업(파일 단위)으로 구분한다.

---

## 진행 현황

| 주차 | 내용 | 상태 |
|------|------|------|
| 1주차 | 환경 세팅 | ✅ 완료 |
| 2주차 | Product + Order 도메인 | ✅ 완료 |
| 2주차 테스트 | contextLoads + 컨테이너 연결 검증 | ✅ 완료 |
| 3주차 | Payment + 에러 처리 + 단위 테스트 | ✅ 완료 |
| 3주차 디버그 | 코드 리뷰 기반 14건 이슈 수정 | ✅ 완료 |
| 4주차 | Redis 캐싱 + 동시성 제어 | ⬜ 대기 |

---

## [1주차] 환경 세팅 — 2026-03-01

> **목표**: 앱이 뜨고 MySQL + Redis에 연결되는 상태까지

### ▶ build.gradle.kts 의존성 수정

**왜 수정했나**: Spring Initializr 자동 생성 의존성 중 명세서와 맞지 않는 것, 존재하지 않는 스타터가 포함되어 있었음

| 항목 | 변경 내용 |
|------|-----------|
| `spring-boot-starter-webmvc` | → `spring-boot-starter-web` (표준 이름) |
| `spring-boot-starter-data-redis-reactive` | → `spring-boot-starter-data-redis` (명세서는 동기 Redis) |
| `reactor-kotlin-extensions`, `coroutines-reactor` | 제거 (reactive 제거 후 불필요) |
| `spring-boot-starter-cache` | 추가 (`@Cacheable` 동작에 필요) |
| `redisson-spring-boot-starter:3.25.0` | 추가 (4주차 분산 락 대비) |
| `io.mockk:mockk:1.13.8` | 추가 (서비스 단위 테스트용) |
| `*-test` 스타터들 (존재하지 않음) | → `spring-boot-starter-test` 로 통합 |

---

### ▶ docker-compose.yml 생성

**왜 만들었나**: 로컬 MySQL / Redis를 도커로 관리해 팀원 간 환경 차이를 없애기 위함

- **MySQL 8.0**: `payment` 데이터베이스, 포트 3306, `utf8mb4` 문자셋
- **Redis 7-alpine**: 포트 6379, 경량 이미지 사용

```bash
# 컨테이너 시작
docker-compose up -d

# 컨테이너 종료
docker-compose down
```

---

### ▶ application.yml 작성

**왜 properties → yml로 전환했나**: 계층 구조가 명확하게 보여 가독성이 좋고, 명세서 예시가 yml 형식임

**핵심 설정 결정 사항**:

| 항목 | 값 | 이유 |
|------|-----|------|
| `ddl-auto` | `create` | 개발 초기 테이블 자동 생성 편의. **2주차 이후 `validate`로 변경 예정** |
| `open-in-view` | `false` | Controller 레이어에서 실수로 Lazy Loading이 발생하는 것을 방지 |
| Redis TTL | `300000ms` (5분) | 상품 목록 기본 캐시 주기 (상품 상세는 Config에서 10분으로 개별 설정 예정) |
| SQL 로그 | `debug` | 실행 쿼리와 바인딩 파라미터를 개발 중 눈으로 확인하기 위함 |

---

## [2주차] Product + Order 도메인 구현 — 2026-03-04

> **목표**: 상품 등록/조회, 주문 생성(재고 차감) API 구현. 낙관적 락으로 동시성 방어.

### ▶ common 레이어 구현 (5개 파일)

**왜 먼저 만들었나**: Product/Order 모든 도메인이 공통 응답 형식과 예외 처리에 의존하므로 기반부터 구축

| 파일 | 내용 |
|------|------|
| `common/response/ApiResponse.kt` | `success/data/error` 구조의 공통 응답 래퍼 + `ErrorResponse` |
| `common/exception/ErrorCode.kt` | HTTP 상태 코드와 기본 메시지를 enum 한 곳에서 관리 |
| `common/exception/BusinessException.kt` | abstract 기반 + 도메인별 구체 예외 클래스 전체 |
| `common/exception/GlobalExceptionHandler.kt` | BusinessException, 낙관적 락 충돌, Validation, 그 외 예외 일괄 처리 |
| `common/config/JpaConfig.kt` | `@EnableJpaAuditing` 분리 — `@SpringBootTest` 슬라이스 테스트 충돌 방지 |

### ▶ Product 도메인 구현 (6개 파일)

| 파일 | 핵심 결정 사항 |
|------|--------------|
| `product/domain/Product.kt` | `@Version` 필드로 낙관적 락 적용. `decreaseStock`/`increaseStock`을 엔티티 내부로 캡슐화 |
| `product/repository/ProductRepository.kt` | JpaRepository 상속, 추가 쿼리 없음 |
| `product/dto/CreateProductRequest.kt` | `@field:` 접두사로 Kotlin 프로퍼티에 Bean Validation 적용 |
| `product/dto/ProductResponse.kt` | `requireNotNull`로 `!!` 대신 명확한 오류 메시지 제공 |
| `product/service/ProductService.kt` | 클래스 레벨 `readOnly = true`, 쓰기만 `@Transactional` 개별 선언 |
| `product/controller/ProductController.kt` | `@Valid`로 요청 검증, `ApiResponse`로 응답 래핑만 담당 |

### ▶ Order 도메인 구현 (7개 파일)

| 파일 | 핵심 결정 사항 |
|------|--------------|
| `order/domain/OrderStatus.kt` | CREATED → PAID → CANCELLED 전이 방향 명시 |
| `order/domain/Order.kt` | `orderId`(UUID)와 DB PK(`id`) 분리. `markAsPaid`/`markAsCancelled`로 상태 전이 캡슐화 |
| `order/repository/OrderRepository.kt` | `findByOrderId(String): Order?` — null 반환으로 호출부 명시적 처리 유도 |
| `order/dto/CreateOrderRequest.kt` | 수량 최솟값 1 검증 |
| `order/dto/OrderResponse.kt` | `order.product.name` 접근이 트랜잭션 내부에서만 발생하도록 주석으로 명시 |
| `order/service/OrderService.kt` | 재고 차감 + 주문 저장을 단일 트랜잭션으로 묶어 롤백 보장. 총 금액은 서버에서 계산 |
| `order/controller/OrderController.kt` | POST 201 / GET 200 |

### ▶ 빌드 이슈 및 해결

**문제**: `allOpen` 플러그인이 `@Entity` 클래스를 `open`으로 만들어, `private set`이 컴파일 오류 발생
(`Private setters for open properties are prohibited`)

**원인**: Kotlin은 `open` 클래스의 프로퍼티에 `private set`을 허용하지 않는다.

**해결**: `private set` → `protected set`으로 변경. JPA 프록시(엔티티를 상속하는 클래스)는 `protected`까지 접근 가능하므로 JPA 동작에 영향 없음.

### ▶ 2주차 테스트 — 2026-03-05

**목표**: 2주차 구현 코드가 실제 인프라와 정상 연동되는지 확인

| 항목 | 결과 |
|------|------|
| Docker 컨테이너 (MySQL 9.6.0, Redis 7-alpine) | 정상 기동 ✅ |
| `./gradlew test` | BUILD SUCCESSFUL (1m 17s) ✅ |
| `contextLoads()` | Spring Context 로딩 + DB/Redis 연결 정상 ✅ |

**현재 테스트 범위**: Spring Context 로딩 및 인프라 연결 확인까지만 커버.
ProductService / OrderService 단위 테스트는 3주차에 작성 예정.

---

## [1주차] 1주차 테스트 — 2026-03-01

> **목표**: 컨테이너 기동 + Spring Boot 정상 구동 확인

### ▶ Docker Compose 포트 변경 (MySQL 3306 → 3307)

**왜 바꿨나**: 로컬에 MySQL(`mysqld.exe`)이 이미 3306을 점유 중이어서 Docker MySQL과 충돌 발생

- `docker-compose.yml` 호스트 포트 `3306` → `3307`
- `application.yml` JDBC URL 포트도 동일하게 수정

### ▶ MySQL 버전 업그레이드 (`mysql:8.0` → `mysql:latest`)

**왜 올렸나**: `auth_gssapi_client` 에러의 원인이 MySQL 버전인지 확인하기 위해 업그레이드.
결과적으로 MySQL이 원인이 아님을 확인 (MySQL 9.6.0에서도 동일 에러).

### ▶ Connector/J `auth_gssapi_client` 버그 우회

**왜 발생했나**: Connector/J 9.6.0이 Hibernate 메타데이터 조회 연결 시,
서버 핸드셰이크에 포함된 `auth_gssapi_client` 플러그인 클래스를 로드하려다 실패.
**서버가 아닌 Connector/J 9.6.0 쪽 버그**로 확정.

| 시도 | 결과 |
|------|------|
| MySQL 버전 업그레이드 (8.0 → 9.6.0) | 동일 에러 — MySQL이 원인이 아님 확인 |
| Connector/J 다운그레이드 (8.4.0, 8.0.33) | `mysql_native_password` 관련 다른 에러 발생 |
| `--default-authentication-plugin` 옵션 | 효과 없음 |
| **`hibernate.boot.allow_jdbc_metadata_access: false`** | **해결** ✅ |

**최종 해결**: `application.yml`에 Hibernate가 시작 시 JDBC 메타데이터 조회 연결을 생성하지 않도록 설정.
Dialect와 DDL 설정이 이미 명시되어 있으므로 메타데이터 조회 없이도 정상 동작.

### ▶ 최종 기동 결과

| 항목 | 값 |
|------|----|
| MySQL | `mysql:latest` (9.6.0), 포트 3307 |
| Redis | `redis:7-alpine`, 포트 6379 |
| Connector/J | 9.6.0 (Spring Boot BOM 기본값) |
| 앱 기동 | `Started PaymentApplicationKt in 3.601 seconds` — 에러 없음 ✅ |
| HTTP 응답 | 정상 (404는 아직 엔드포인트가 없어서) ✅ |

---

---

## [3주차] Payment 도메인 + 단위 테스트 구현 — 2026-03-05

> **목표**: 결제 승인/취소 API 구현, 멱등성 키 중복 방지, 금액 검증, 재고 복원. 전 도메인 Service 단위 테스트 작성.

### ▶ Payment 도메인 구현 (9개 파일)

**왜 이 작업을 했나**: Product/Order까지 완성된 상태에서, 결제 도메인을 추가해 전체 주문-결제 흐름을 완성한다.

| 파일 | 핵심 결정 사항 |
|------|--------------|
| `payment/domain/PaymentStatus.kt` | READY → DONE → CANCELLED 전이 규칙. Order의 상태 전이와 대칭적 구조 |
| `payment/domain/PaymentMethod.kt` | CARD, VIRTUAL_ACCOUNT, EASY_PAY, TRANSFER — 토스페이먼츠 결제 수단 명칭 참고 |
| `payment/domain/Payment.kt` | paymentKey(UUID) 외부 노출, idempotencyKey(UNIQUE)로 DB 레벨 중복 차단. approve()/cancel() 캡슐화 |
| `payment/repository/PaymentRepository.kt` | findByPaymentKey (취소용), findByIdempotencyKey (멱등성 조회용) |
| `payment/dto/ConfirmPaymentRequest.kt` | orderId, amount, paymentMethod, idempotencyKey 4개 필드 모두 필수 |
| `payment/dto/CancelPaymentRequest.kt` | cancelReason 필수 — 이력 관리와 고객 안내 용도 |
| `payment/dto/PaymentResponse.kt` | from() 정적 팩토리 — order.orderId 접근에 LAZY 프록시 주의 명시 |
| `payment/service/PaymentService.kt` | 멱등성 키 → 주문 조회 → 금액 검증 → 승인 순서. 취소 시 결제+주문+재고 3종 롤백 보장 |
| `payment/controller/PaymentController.kt` | POST /confirm (200), POST /{paymentKey}/cancel (200) |

**핵심 결정: 결제 승인이 200인 이유**
멱등성 키가 이미 존재하면 새 리소스를 생성하지 않고 기존 결과를 반환한다. "새로 만들었을 수도 있고 아닐 수도 있는" 응답에 201 Created는 부적절하므로 200 OK를 사용한다.

**핵심 결정: BigDecimal 비교에 compareTo 사용**
`BigDecimal.equals()`는 스케일까지 비교해 `10000.00 != 10000`이 된다. `compareTo()`는 수치만 비교하므로 금액 검증에 적합하다.

### ▶ schema.sql 업데이트

- `payments` 테이블 추가 (payment_key UNIQUE, idempotency_key UNIQUE, order_id FK)

### ▶ 단위 테스트 작성 (3개 파일, 총 15개 테스트)

**왜 단위 테스트를 추가했나**: Service 레이어의 비즈니스 로직이 의도대로 동작하는지 DB 없이 빠르게 검증

| 파일 | 테스트 케이스 수 | 핵심 검증 항목 |
|------|:---:|------------|
| `ProductServiceTest.kt` | 4 | 상품 등록, 목록 조회, 상세 조회, 존재하지 않는 상품 예외 |
| `OrderServiceTest.kt` | 5 | 주문 생성 + 재고 차감, 상품 없음 예외, 재고 부족 예외, orderId 조회, 서버 금액 계산 검증 |
| `PaymentServiceTest.kt` | 6 | 결제 승인, 멱등성 키 중복 처리, 주문 없음 예외, 금액 불일치 예외, 결제 취소 + 재고 복원, 상태 전이 예외 |

**핵심 결정: 리플렉션으로 테스트 엔티티 생성**
JPA 엔티티의 `id`, `createdAt`은 DB가 설정하는 값이다. 단위 테스트에서는 DB가 없으므로 리플렉션으로 직접 세팅한다. MockK의 `every { save(any()) } answers { firstArg() }` 패턴과 결합해 실제 저장 없이 테스트한다.

**빌드 결과**: `./gradlew test` — BUILD SUCCESSFUL (16s), 15개 테스트 전부 통과 ✅

---

---

## [3주차] 디버그 수정 — 2026-03-05

> **목표**: 코드 리뷰에서 발견된 14건의 이슈(치명적 3, 경고 5, 권고 6)를 우선순위 순으로 수정하고 빌드 검증

### ▶ 치명적 오류 수정 (3건)

| # | 이슈 | 수정 내용 | 핵심 결정 사항 |
|---|------|-----------|--------------|
| 1 | schema.sql 미실행 | `application.yml`에 `spring.sql.init.mode: always` + `defer-datasource-initialization: true` 추가 | embedded가 아닌 외부 DB(MySQL)는 `mode: always` 명시 필수. `defer-datasource-initialization`이 없으면 JPA 초기화 전에 SQL이 실행되어 실패 |
| 2 | 멱등성 키 Race Condition | `save` → `saveAndFlush`로 변경, `DataIntegrityViolationException` catch 후 재조회 | save는 트랜잭션 커밋까지 INSERT를 지연하므로 UNIQUE 위반을 즉시 감지 못함. saveAndFlush로 즉시 DB 반영하여 동시 요청 감지 |
| 3 | cancelReason 누락 | Payment 엔티티에 `cancelReason: String?` 필드, `cancel(reason: String)` 시그니처, schema에 `cancel_reason` 컬럼 추가 | 취소 사유를 DB에 기록해 이력 관리와 고객 안내에 활용. PaymentResponse에도 반영 |

### ▶ 경고 수정 (5건)

| # | 이슈 | 수정 내용 |
|---|------|-----------|
| 4 | PaymentService `ProductRepository` 미사용 | 생성자에서 `productRepository` 제거. 재고 복원은 `order.product`로 접근하므로 직접 의존 불필요 |
| 5 | Product에 UUID 외부 식별자 없음 | `productId: String(UUID)` 추가, ProductResponse `id: Long` → `productId: String`, Controller/Service/Repository 전체 변경, schema에 `product_id VARCHAR(36)` + UNIQUE 추가 |
| 6 | 이미 결제된 주문 재결제 미검증 | `confirmPayment`에서 주문 조회 직후 `order.status == PAID`이면 `AlreadyPaidException` throw. markAsPaid()보다 앞에서 명확한 에러 제공 |
| 7 | OrderRepository LAZY 프록시 문제 | `findByOrderId`에 `@EntityGraph(attributePaths = ["product"])` 추가. OSIV=false 환경에서 트랜잭션 밖 접근 시 LazyInitializationException 방지 |
| 8 | Order/Payment 낙관적 락 없음 | 두 엔티티에 `@Version var version: Long = 0` 추가, schema에 `version BIGINT NOT NULL DEFAULT 0` 컬럼 추가 |

### ▶ 권고 수정 (6건)

| # | 이슈 | 수정 내용 |
|---|------|-----------|
| 9 | 멱등성 재요청 값 검증 미비 | `validateIdempotencyRequest()` 추가: orderId/amount 불일치 시 `PaymentAmountMismatchException` throw |
| 10 | 테스트 헬퍼 중복 | `TestFixtures.kt` 공통 파일 생성, 3개 테스트에서 private 헬퍼 제거 후 공통 호출로 전환 |
| 11 | 재고 0 등록 허용 | `CreateProductRequest` `@Min(0)` → `@Min(1)` (구매 불가 상품 등록 방지) |
| 12 | `ProductServiceTest` 미사용 import | `io.mockk.slot` 제거 |
| 13 | `OrderServiceTest` 미사용 import | `io.mockk.verify` 제거 |
| 14 | `PaymentApplicationTests` 로컬 Docker 의존 | `@Disabled("로컬 Docker 환경 필요")` 추가 |

### ▶ 빌드 검증

```
./gradlew test → BUILD SUCCESSFUL (43s)
테스트: 17개 통과 (기존 15개 + 신규 2개: AlreadyPaid 검증, 멱등성 값 검증)
```

---

### ▶ 개인 공부 노트 작성 (docs/study-notes.md)

**왜 만들었나**: 1주차 작업에서 내린 결정들의 "왜"를 체계적으로 정리해 복습하기 위함

- Gradle 의존성 선택 이유, `implementation` vs `runtimeOnly` 스코프 차이
- Kotlin + JPA 조합의 특이점 (`allOpen`, `plugin.jpa`, data class 사용 금지 이유)
- Docker Compose로 로컬 인프라 관리하는 이유
- `application.yml` 핵심 설정들의 의도 (OSIV, ddl-auto, TTL 등)
- Connector/J 버그 트러블슈팅 전 과정
- 앞으로 구현할 아키텍처 개념 미리 정리 (ApiResponse, ErrorCode, 낙관적 락, 멱등성 등)

---
