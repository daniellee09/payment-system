# 💳 Payment System API

> Kotlin + Spring Boot 기반 결제 시스템 API — 동시성 제어, 멱등성 보장, MSA 확장 대비
> claude code agent 활용

---

## 🛠 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Kotlin (JDK 17) |
| Framework | Spring Boot 3.x, Spring MVC, Spring Data JPA |
| Database | MySQL 8.0 |
| Cache / Lock | Redis 7.x (Lettuce, Redisson) |
| Build | Gradle (Kotlin DSL) |
| Infra | Docker, Docker Compose |
| Test | JUnit 5, MockK |

---

## 📋 주요 기능

**상품 관리** — 상품 CRUD, Redis 캐싱 (TTL 기반 자동 만료)

**주문 처리** — 주문 생성 시 재고 차감 (단일 트랜잭션), 상태 관리 (`CREATED` → `PAID` → `CANCELLED`)

**결제** — 결제 승인/취소, 멱등성 키를 통한 중복 결제 방지, 주문-결제 금액 검증

**동시성 제어** — JPA 낙관적 락 vs Redis 분산 락 비교 구현 및 성능 측정

---

## 🚀 실행 방법

```bash
# 인프라 실행
docker-compose up -d

# 애플리케이션 실행
./gradlew bootRun
```

---

## 📡 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| `POST` | `/api/v1/products` | 상품 등록 |
| `GET` | `/api/v1/products` | 상품 목록 조회 |
| `GET` | `/api/v1/products/{productId}` | 상품 상세 조회 |
| `POST` | `/api/v1/orders` | 주문 생성 |
| `GET` | `/api/v1/orders/{orderId}` | 주문 상세 조회 |
| `POST` | `/api/v1/payments/confirm` | 결제 승인 |
| `POST` | `/api/v1/payments/{paymentKey}/cancel` | 결제 취소 |

---

## 🧪 테스트

```bash
./gradlew test
./gradlew test --tests "*ConcurrencyTest*"
```

| 구분 | 내용 |
|------|------|
| Unit | Service 비즈니스 로직 (MockK) |
| Integration | API 엔드포인트 통합 테스트 |
| Concurrency | 멀티스레드 재고 정합성 검증 |

---

## 🗺 로드맵

- [x] Phase 1 — 모놀리식 결제 API (Spring Boot + JPA + MySQL + Redis)
- [ ] Phase 2 — MSA 분리 + Kafka 이벤트 드리븐 + Spring Batch 정산
- [ ] Phase 3 — API Gateway + OpenAPI 설계 + 모니터링
