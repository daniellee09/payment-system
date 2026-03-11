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
| 프론트엔드 | 대시보드 UI + API 버그 수정 | ✅ 완료 |
| POS 프론트엔드 | 실무형 POS 결제 단말기 UI | ✅ 완료 |
| 4주차 | Redis 캐싱 + 동시성 제어 | ✅ 완료 |
| 5주차 | 기능 확장 + 버그 수정 + 운영 개선 | ✅ 완료 |

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

## [버그 수정] MySQL 런타임 연결 오류 해결 — 2026-03-06

> **목표**: `auth_gssapi_client` 인증 플러그인 오류로 DB 연결이 완전히 실패하던 문제 해결

### ▶ 근본 원인 분석 및 수정

**왜 이 작업을 했나**: 앱 기동 시 `Unable to load authentication plugin 'auth_gssapi_client'` 오류로 HikariPool 초기화 실패 → 모든 API 요청이 INTERNAL_ERROR 반환

#### 오류 추적 과정

| 단계 | 시도 | 결과 |
|------|------|------|
| 1 | `disabledAuthenticationPlugins=auth_gssapi_client` URL 파라미터 | 실패 - 플러그인 이름이 서버에서 동적으로 전달됨 |
| 2 | JNA 5.15.0 + jna-platform 의존성 추가 | 실패 |
| 3 | `authenticationPlugins` 화이트리스트 | 실패 |
| 4 | Connector/J 8.0.33 핀 버전 | 실패 - HikariCP 7.x(Spring Boot 4.x)와 비호환 |
| 5 | 포트 3307 raw 핸드쉐이크 캡처 | **근본 원인 발견** |

#### 핵심 발견

포트 3307에 연결하면 → `11.4.5-MariaDB` 가 응답. 호스트 Windows 머신에 **로컬 MariaDB 11.4.5**가 포트 3307에서 이미 실행 중이었고, Docker MySQL 컨테이너보다 먼저 연결을 처리하고 있었다. MariaDB가 핸드쉐이크에서 `auth_gssapi_client`를 요청하는데, Connector/J 9.6.0이 해당 플러그인을 찾지 못해 오류 발생.

#### 적용한 수정

| 파일 | 변경 내용 |
|------|-----------|
| `docker-compose.yml` | MySQL 호스트 포트 `3307` → `3308` (로컬 MariaDB 충돌 회피) |
| `application.yml` | JDBC URL 포트 `3307` → `3308`, 불필요 파라미터 제거 |
| `build.gradle.kts` | Connector/J 버전 언핀(Spring Boot 4.x BOM 관리 9.6.0 사용), JNA 의존성 제거 |

#### DB 스키마 업데이트 (수동)

포트 변경으로 Docker 컨테이너를 재생성하면서 `product_id` 컬럼 누락 확인:
```sql
ALTER TABLE products ADD COLUMN product_id VARCHAR(36) NOT NULL AFTER id;
ALTER TABLE products ADD UNIQUE KEY uk_product_id (product_id);
```

> 앞으로 `schema.sql`을 항상 최신 엔티티 구조와 동기화할 것

---

## [프론트엔드] 대시보드 UI 구현 + API 버그 수정 — 2026-03-06

> **목표**: 백엔드 결제 시스템 API 전체 플로우를 시각적으로 테스트할 수 있는 대시보드 구현

### ▶ 프론트엔드 구현 (3개 파일)

**왜 만들었나**: 백엔드 API를 브라우저에서 직접 테스트하고, 상품등록 → 주문 → 결제승인 → 결제취소 전체 플로우를 시각적으로 확인하기 위함

| 파일 | 내용 |
|------|------|
| `frontend/index.html` | SPA 스타일 대시보드. 탭 네비게이션(상품/주문/결제) + 플로우 시각화 + 응답 뷰어 |
| `frontend/style.css` | 토스 스타일 디자인 시스템. 카드 기반 UI, 인디고 블루 액센트, 상태별 컬러 배지 |
| `frontend/app.js` | API 통신, 폼 핸들링, 토스트 알림, 확인 모달, JSON 하이라이팅, 스켈레톤 로더 |

**핵심 결정: 빌드 도구 없는 순수 HTML/CSS/JS**
브라우저에서 바로 열 수 있어야 하므로 React/Vite 등을 사용하지 않았다. `index.html`을 더블클릭하거나 Live Server로 열면 즉시 동작한다.

**UI 기능 목록**:
- 상품 등록/목록/상세 조회
- 상품 선택 → 주문 생성 (수량/고객명 입력, 예상 금액 자동 계산)
- 주문 결과에서 "이 주문으로 결제하기" 버튼으로 결제 탭 자동 전환
- 결제 승인 (멱등성 키 자동 생성, 재생성 버튼)
- 결제 취소 (확인 모달로 파괴적 액션 보호)
- 모든 API 응답을 JSON 하이라이팅으로 실시간 표시
- 성공/실패 토스트 알림
- 상태 배지 (CREATED/PAID/CANCELLED → 노랑/초록/빨강)

### ▶ 백엔드 CORS 설정 추가

| 파일 | 내용 |
|------|------|
| `common/config/WebConfig.kt` | `/api/**` 경로에 모든 Origin 허용 (개발 환경 전용) |

### ▶ CreateOrderRequest.productId: Long → String 변경 (API 버그 수정)

**왜 바꿨나**: `CreateOrderRequest.productId`가 내부 DB PK(`Long`)를 요구했지만, Product API는 외부용 UUID(`productId: String`)만 노출한다. 프론트엔드에서 Long id를 알 수 있는 방법이 없어, 외부 클라이언트가 주문을 생성할 수 없는 상태였다.

| 파일 | 변경 내용 |
|------|-----------|
| `order/dto/CreateOrderRequest.kt` | `productId: Long` → `productId: String`, `@NotNull` → `@NotBlank` |
| `order/service/OrderService.kt` | `findById(Long)` → `findByProductId(String)` |
| `OrderServiceTest.kt` | 모든 `productId = 1L` → `productId = product.productId`, 모킹 대상 `findById` → `findByProductId` |

**빌드 검증**: `./gradlew test` — BUILD SUCCESSFUL, 전체 테스트 통과 ✅

---

## [POS 프론트엔드] 실무형 POS 결제 단말기 UI 구현 — 2026-03-07

> **목표**: 개발자용 대시보드(`frontend/`)와 별도로, 현장 직원(점원/카운터 담당자)이 사용하는 태블릿/데스크톱 POS 스타일 프론트엔드 구축

### ▶ POS 프론트엔드 구현 (3개 파일)

**왜 만들었나**: 기존 `frontend/`는 API 테스트 도구이고, 이번에는 실사용자가 상품 선택 -> 수량 조절 -> 고객명 입력 -> 결제 -> 영수증 확인까지를 하나의 화면에서 빠르게 처리할 수 있는 실무형 인터페이스가 필요했다.

| 파일 | 내용 |
|------|------|
| `frontend-pos/index.html` | 3단 분할 POS 레이아웃 (상품 그리드 / 장바구니 / 결제 패널) |
| `frontend-pos/style.css` | 다크 테마 디자인 시스템. POS 단말기 느낌의 딥 네이비 배경, 인디고 블루 액센트 |
| `frontend-pos/app.js` | 상품 선택, 수량 조절, 결제 프로세스(주문 생성 -> 결제 승인), 영수증, 결제 취소 |

**핵심 결정: 3단 분할 레이아웃**
왼쪽(상품 그리드) + 중앙(장바구니/주문서) + 오른쪽(결제)으로 나눠, 시선 이동 없이 전체 결제 흐름을 한 화면에서 처리한다. POS 단말기의 실제 사용 패턴을 반영했다.

**핵심 결정: 단일 상품 주문 제약의 자연스러운 처리**
백엔드 API가 단일 상품 주문만 지원하므로, 장바구니에는 하나의 상품만 담긴다. 새 상품을 클릭하면 기존 상품을 교체한다. 같은 상품을 다시 클릭하면 수량만 1 증가한다.

**핵심 결정: 주문+결제를 하나의 버튼으로 통합**
사용자는 "결제하기" 버튼 하나만 누르면 내부적으로 주문 생성(POST /orders) -> 결제 승인(POST /payments/confirm)이 순차 실행된다. 점원 입장에서 주문과 결제는 하나의 행위이므로 이를 분리하지 않았다.

**UI 기능 목록**:
- 상품 그리드: 카드형 레이아웃, 재고 표시(색상 점), 품절 상품 비활성화
- 장바구니: +/- 버튼 수량 조절, 소계/합계 실시간 업데이트, 초기화 버튼
- 결제 패널: 고객명 입력, 결제 수단 토글(카드/계좌이체/간편결제), 결제 요약
- 영수증 오버레이: 결제 완료/취소 상태별 색상, 상세 정보, 결제 취소 버튼
- 결제 취소: 확인 모달 -> 취소 API 호출 -> 재고 복원 반영 -> 취소 영수증 표시
- 에러 토스트: 화면 하단 중앙에 3초간 표시
- 스켈레톤 로딩: 상품 목록 로딩 중 스켈레톤 UI 표시

---

## [4주차] Redis 캐싱 + 분산 락 + 동시성 제어 — 2026-03-07

> **목표**: 상품 조회에 Redis 캐싱을 적용해 DB 부하를 줄이고, Redisson 분산 락으로 재고 차감 동시성 문제를 해결한다.

### ▶ Redis 캐싱 (3개 파일)

| 파일 | 핵심 결정 사항 |
|------|--------------|
| `common/config/CacheConfig.kt` | `@EnableCaching` 분리. PRODUCT_LIST(5분), PRODUCT_DETAIL(10분) TTL 개별 설정. 키는 StringRedisSerializer로 사람이 읽을 수 있게 |
| `product/dto/ProductResponse.kt` | `Serializable` 구현 추가. JDK 직렬화 기반 Redis 캐시에 저장하려면 필수 |
| `product/service/ProductService.kt` | `getProducts()` → `@Cacheable(PRODUCT_LIST)`, `getProduct()` → `@Cacheable(PRODUCT_DETAIL, key="#productId")`, `createProduct()` → `@CacheEvict(PRODUCT_LIST, allEntries=true)` |

**핵심 결정: JDK 직렬화 선택 이유**
Jackson 기반 직렬화(`GenericJackson2JsonRedisSerializer`)는 Spring Boot 4.x(Jackson 3.x)에서 API 호환성 이슈가 있을 수 있다. JDK 직렬화는 버전 무관하게 안정적으로 동작하므로 이 프로젝트에서 채택했다. 단, 직렬화된 바이트 형식은 Redis CLI에서 바로 읽기 어렵다는 단점이 있다.

### ▶ Redisson 분산 락 (3개 파일)

| 파일 | 핵심 결정 사항 |
|------|--------------|
| `build.gradle.kts` | `org.redisson:redisson:3.40.0` 추가. `redisson-spring-boot-starter` 대신 plain 라이브러리 사용 — Spring Boot 4.x 자동 설정과의 충돌 가능성 회피 |
| `common/config/RedissonConfig.kt` | `RedissonClient` 빈을 수동 등록. `spring.data.redis.host/port`를 재사용해 Redis 설정을 한 곳에서 관리. `destroyMethod="shutdown"`으로 Netty 스레드 정상 종료 |
| `common/lock/DistributedLockManager.kt` | `withLock(key, block)` 유틸리티. 대기 5초, 점유 10초. `isHeldByCurrentThread` 검사로 만료 후 중복 해제(IllegalMonitorStateException) 방지 |

**핵심 결정: 분산 락 + 낙관적 락 이중 방어**
분산 락만 사용하면 Redis 장애 시 동시성 보호가 무너진다. `@Version` 낙관적 락을 그대로 유지해 분산 락이 실패하거나 극히 짧은 레이스 윈도우(락 해제 → TX 커밋 사이)가 발생해도 DB 레벨에서 Lost Update를 차단한다.

**핵심 결정: Lettuce + Redisson 동시 사용**
Spring Data Redis(Lettuce)는 `@Cacheable` 캐싱에, Redisson은 분산 락에 사용한다. 두 클라이언트는 각자의 Netty 연결 풀을 유지하므로 서로 간섭하지 않는다. 분산 락에 Lettuce 대신 Redisson을 선택한 이유는, Redisson RLock이 Redis Pub/Sub으로 락 해제를 구독해 busy-wait 없이 대기하기 때문이다.

**핵심 결정: 락 키를 productId 단위로 설정**
`"product:{productId}"` 형태로 상품별 락을 분리한다. 상품 A와 B의 주문은 서로 관계없으므로 동시에 처리 가능하고, 같은 상품에 대한 주문만 직렬화된다.

### ▶ 에러 코드 추가

| 항목 | 내용 |
|------|------|
| `ErrorCode.LOCK_ACQUISITION_FAILED` | 409 Conflict. 락 획득 타임아웃 시 반환 |
| `LockAcquisitionFailedException` | `BusinessException` 상속. GlobalExceptionHandler가 자동 처리 |

### ▶ 동시성 통합 테스트

| 파일 | 내용 |
|------|------|
| `ConcurrencyTest.kt` | 재고 10개 상품에 100개 스레드 동시 주문 → 정확히 10개 성공, 90개 실패 검증 |

**핵심 결정: @Disabled 기본 설정**
`ConcurrencyTest`는 실제 DB와 Redis가 필요한 통합 테스트이므로, Docker 환경 없이 실행하면 실패한다. 일반 단위 테스트 빌드에서 제외하고, Docker 환경에서 `@Disabled`를 제거 후 실행한다.

### ▶ OrderServiceTest 수정

MockK로 `DistributedLockManager.withLock`을 모킹할 때 제네릭 함수의 타입 추론 문제 해결:
```kotlin
every { distributedLockManager.withLock(any<String>(), any<() -> Any>()) } answers {
    (args[1] as () -> Any).invoke()
}
```
`any<() -> Any>()`로 람다 타입을 명시해야 Kotlin 컴파일러가 `withLock<T>`의 `T`를 추론할 수 있다.

### ▶ 빌드 검증

```
./gradlew test → BUILD SUCCESSFUL
단위 테스트 17개 전부 통과 (ConcurrencyTest는 @Disabled)
```

---
