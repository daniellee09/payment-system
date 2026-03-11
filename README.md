# Payment System API

> Kotlin + Spring Boot 기반 결제 시스템 API
>
> 동시성 제어, 멱등성 보장, Redis 캐싱, 분산 락을 실제로 구현하며 학습한 프로젝트

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Kotlin 2.x, JDK 17 |
| Framework | Spring Boot 4.x, Spring MVC, Spring Data JPA |
| Database | MySQL 8.0 (Docker) |
| Cache / Lock | Redis 7.x — Lettuce(캐싱), Redisson(분산 락) |
| Build | Gradle Kotlin DSL |
| Infra | Docker, Docker Compose |
| Test | JUnit 5, MockK |

---

## 구현 기능

### 상품 (Product)
- 상품 등록 / 목록 조회 / 상세 조회 / 삭제
- 재고 관리 — 주문 시 차감, 결제 취소 시 복원
- Redis 캐싱 — 목록 5분 TTL, 상세 10분 TTL
- 주문이 있는 상품은 삭제 불가 (409 PRODUCT_HAS_ORDERS)

### 주문 (Order)
- 주문 생성 — 재고 차감을 단일 트랜잭션으로 처리
- 주문 목록 / 상세 조회 — 결제 키(paymentKey) 포함
- 상태 머신 — `CREATED` → `PAID` → `CANCELLED`
- 총 금액 서버 계산 — 클라이언트 금액 신뢰 안 함

### 결제 (Payment)
- 결제 승인 — 멱등성 키로 이중 결제 방지
- 결제 취소 — 결제 취소 + 주문 취소 + 재고 복원을 하나의 트랜잭션으로 처리
- 금액 검증 — 주문 금액과 결제 금액 불일치 시 400 거부

### 동시성 제어 (이중 방어)
- **Redisson 분산 락** — 상품별 락 키(`product:{productId}`)로 동시 주문 직렬화
- **JPA 낙관적 락** (`@Version`) — 분산 락 해제 후 찰나의 레이스 조건도 DB 레벨에서 차단
- 100개 스레드 동시 주문 테스트 → 재고 10개만 정확히 성공 검증

---

## API 엔드포인트

Base URL: `http://localhost:8080/api/v1`

| Method | Endpoint | 상태 코드 | 설명 |
|--------|----------|:---------:|------|
| `POST` | `/products` | 201 | 상품 등록 |
| `GET` | `/products` | 200 | 상품 목록 조회 (캐시) |
| `GET` | `/products/{productId}` | 200 | 상품 상세 조회 (캐시) |
| `DELETE` | `/products/{productId}` | 204 | 상품 삭제 |
| `POST` | `/orders` | 201 | 주문 생성 + 재고 차감 |
| `GET` | `/orders` | 200 | 주문 목록 조회 |
| `GET` | `/orders/{orderId}` | 200 | 주문 상세 조회 |
| `POST` | `/payments/confirm` | 200 | 결제 승인 (멱등성) |
| `POST` | `/payments/{paymentKey}/cancel` | 200 | 결제 취소 + 재고 복원 |

### 응답 형식

모든 API는 `ApiResponse<T>` 공통 래퍼로 응답한다.

```json
// 성공
{
  "success": true,
  "data": { "productId": "550e8400-...", "name": "노트북", "price": 1500000.00, "stock": 99 }
}

// 실패
{
  "success": false,
  "error": {
    "code": "OUT_OF_STOCK",
    "message": "재고가 부족합니다. 현재 재고: 0",
    "timestamp": "2026-03-09T12:00:00"
  }
}
```

### 에러 코드

| 코드 | HTTP | 설명 |
|------|:----:|------|
| `PRODUCT_NOT_FOUND` | 404 | 상품 없음 |
| `ORDER_NOT_FOUND` | 404 | 주문 없음 |
| `PAYMENT_NOT_FOUND` | 404 | 결제 없음 |
| `PRODUCT_HAS_ORDERS` | 409 | 주문 있는 상품 삭제 시도 |
| `OUT_OF_STOCK` | 409 | 재고 부족 |
| `ALREADY_PAID` | 409 | 이미 결제된 주문 |
| `INVALID_ORDER_STATUS` | 409 | 유효하지 않은 주문 상태 전이 |
| `INVALID_PAYMENT_STATUS` | 409 | 유효하지 않은 결제 상태 전이 |
| `PAYMENT_AMOUNT_MISMATCH` | 400 | 결제 금액 불일치 |
| `CONCURRENT_UPDATE` | 409 | 낙관적 락 충돌 (재시도 가능) |
| `LOCK_ACQUISITION_FAILED` | 409 | 분산 락 타임아웃 (재시도 가능) |

---

## 실행 방법

### 사전 조건

- Docker Desktop 실행 중
- JDK 17+

### 포트 확인

```bash
# 3308 (MySQL), 6379 (Redis), 8080 (앱) 포트가 비어 있어야 함
# Windows
netstat -ano | findstr :3308
netstat -ano | findstr :6379
netstat -ano | findstr :8080
```

> 로컬에 MySQL(3306), MariaDB(3307)가 설치된 경우 Docker MySQL을 3308로 매핑한다.
> 포트 충돌 시 `docker-compose.yml`의 호스트 포트를 변경하고 `application.yml`의 JDBC URL도 함께 수정한다.

### 실행

```bash
# 1. 인프라 실행 (MySQL:3308, Redis:6379)
docker-compose up -d

# 2. 앱 실행
./gradlew bootRun
```

앱이 시작되면 `schema.sql`이 자동으로 실행되어 테이블이 생성된다.

### 접속

| URL | 설명 |
|-----|------|
| `http://localhost:8080/` | 랜딩 페이지 (서비스 현황, API 목록) |
| `http://localhost:8080/dashboard/` | 개발자 대시보드 (API 테스트 UI) |
| `http://localhost:8080/pos/` | POS 결제 단말기 UI |
| `http://localhost:8080/api/v1/...` | REST API |

---

## 프론트엔드

Spring Boot 정적 자원(`src/main/resources/static/`)으로 통합되어 별도 서버 없이 동작한다.

### 개발자 대시보드 (`/dashboard/`)

백엔드 API 전체 플로우를 브라우저에서 테스트하는 도구.

- 상품 등록 / 목록 / 상세 / 삭제
- 주문 생성 (상품 선택 → 수량 입력 → 예상 금액 자동 계산)
- 결제 승인 (멱등성 키 자동 생성)
- 결제 취소 (확인 모달)
- 주문 관리 탭 (상태 필터, 직접 취소)
- 모든 API 응답을 JSON으로 실시간 표시

### POS 단말기 UI (`/pos/`)

현장 직원이 사용하는 3단 분할 레이아웃 인터페이스.

- 상품 그리드 (재고 상태 표시, 품절 비활성화)
- 장바구니 (수량 조절, 소계 실시간 업데이트)
- 결제 패널 (고객명, 결제 수단 선택)
- 영수증 오버레이 (결제 완료/취소 상태별 색상)
- 주문 이력 패널 (당일 주문 목록, 직접 취소)

---

## 테스트

```bash
# 전체 단위 테스트
./gradlew test

# Docker 환경에서 동시성 통합 테스트 (@Disabled 제거 후 실행)
./gradlew test --tests "*ConcurrencyTest*"
```

| 구분 | 파일 | 케이스 수 |
|------|------|:---------:|
| Product 단위 테스트 | `ProductServiceTest` | 4 |
| Order 단위 테스트 | `OrderServiceTest` | 5 |
| Payment 단위 테스트 | `PaymentServiceTest` | 6 |
| 동시성 통합 테스트 | `ConcurrencyTest` | 1 (@Disabled) |

---

## 프로젝트 구조

```
src/main/kotlin/com/example/payment/
├── common/
│   ├── config/       # JpaConfig, CacheConfig, RedissonConfig, WebConfig
│   ├── exception/    # ErrorCode, BusinessException, GlobalExceptionHandler
│   ├── response/     # ApiResponse<T>
│   └── lock/         # DistributedLockManager (Redisson)
├── product/
│   ├── domain/       # Product (@Version, decreaseStock/increaseStock)
│   ├── repository/   # ProductRepository
│   ├── service/      # ProductService (@Cacheable, @CacheEvict)
│   ├── controller/   # ProductController
│   └── dto/          # CreateProductRequest, ProductResponse (Serializable)
├── order/
│   ├── domain/       # Order (상태 머신: CREATED→PAID→CANCELLED)
│   ├── repository/   # OrderRepository (@EntityGraph)
│   ├── service/      # OrderService (분산 락 + 서버 측 금액 계산)
│   ├── controller/   # OrderController
│   └── dto/          # CreateOrderRequest, OrderResponse
└── payment/
    ├── domain/       # Payment (멱등성 키, READY→DONE→CANCELLED)
    ├── repository/   # PaymentRepository (@EntityGraph)
    ├── service/      # PaymentService (멱등성, 금액 검증, 3종 롤백)
    ├── controller/   # PaymentController
    └── dto/          # ConfirmPaymentRequest, CancelPaymentRequest, PaymentResponse

src/main/resources/
├── static/
│   ├── index.html    # 랜딩 페이지
│   ├── dashboard/    # 개발자 대시보드 UI
│   └── pos/          # POS 단말기 UI
├── schema.sql        # 테이블 DDL (수동 관리)
└── application.yml
```

---

## 핵심 구현 포인트

### 1. 분산 락 + 낙관적 락 이중 방어

재고 차감 시 Redisson 분산 락(1차)과 JPA `@Version` 낙관적 락(2차)을 함께 사용한다.

```kotlin
fun createOrder(request: CreateOrderRequest): OrderResponse {
    return distributedLockManager.withLock("product:${request.productId}") {
        val product = productRepository.findByProductId(request.productId)
            ?: throw ProductNotFoundException()
        product.decreaseStock(request.quantity)  // @Version으로 Lost Update 차단
        ...
    }
}
```

### 2. 멱등성 보장 (이중 결제 방지)

```kotlin
fun confirmPayment(request: ConfirmPaymentRequest): PaymentResponse {
    // 같은 키로 재시도하면 기존 결과를 그대로 반환
    val existing = paymentRepository.findByIdempotencyKey(request.idempotencyKey)
    if (existing != null) return PaymentResponse.from(existing)

    // saveAndFlush: 즉시 INSERT로 UNIQUE 제약 위반을 즉시 감지
    val payment = try {
        paymentRepository.saveAndFlush(Payment(...))
    } catch (e: DataIntegrityViolationException) {
        // 동시 요청이 먼저 INSERT한 경우 — 재조회 후 반환
        return PaymentResponse.from(
            paymentRepository.findByIdempotencyKey(request.idempotencyKey)!!
        )
    }
    ...
}
```

### 3. 결제 취소 시 3종 원자적 롤백

```kotlin
@Transactional
fun cancelPayment(paymentKey: String, request: CancelPaymentRequest): PaymentResponse {
    val payment = paymentRepository.findByPaymentKey(paymentKey)!!  // order.product까지 JOIN
    payment.cancel(request.cancelReason)     // 결제: DONE → CANCELLED
    payment.order.markAsCancelled()          // 주문: PAID → CANCELLED
    payment.order.product.increaseStock(payment.order.quantity)  // 재고 복원
    // 셋 중 하나라도 실패하면 전체 롤백
}
```

---

## 트러블슈팅 기록

주요 트러블슈팅은 `docs/study-notes.md`와 `DEVLOG.md`에 상세히 기록되어 있다.

| 이슈 | 원인 | 해결 |
|------|------|------|
| `auth_gssapi_client` 에러 | 포트 3307에 로컬 MariaDB가 응답 (포트 충돌) | Docker MySQL 포트를 3308로 변경 |
| Hibernate 메타데이터 접근 오류 | Connector/J 9.x 버그 | `hibernate.boot.allow_jdbc_metadata_access: false` |
| 캐시 미무효화 | 재고 변경 시 @CacheEvict 누락 | createOrder, cancelPayment에 추가 |
| N+1 쿼리 | PaymentRepository @EntityGraph 미적용 | `["order", "order.product"]` EntityGraph 추가 |
| 낙관적 락 충돌 (테스트) | MockK 제네릭 람다 타입 추론 실패 | `any<() -> Any>()` 명시 |

---

## 로드맵

- [x] Phase 1 — 모놀리식 결제 API (Spring Boot + JPA + MySQL + Redis)
- [ ] Phase 2 — MSA 분리 + Kafka 이벤트 드리븐 + Spring Batch 정산
- [ ] Phase 3 — API Gateway + OpenAPI 설계 + 모니터링 (Prometheus, Grafana)
