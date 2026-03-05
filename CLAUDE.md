# Payment System API — Project Rules

## 프로젝트 개요

Kotlin + Spring Boot 기반 결제 시스템 API.
`docs/Phase1_결제시스템API_명세서.docx`를 기준으로 개발한다.

## 기술 스택

- Kotlin, JDK 17
- Spring Boot 3.x, Spring MVC, Spring Data JPA
- MySQL 8.0, Redis 7.x (Lettuce, Redisson)
- Gradle (Kotlin DSL)
- JUnit 5, MockK

## 패키지 구조

```
src/main/kotlin/com/example/payment/
├── common/
│   ├── config/          # Redis, JPA 등 설정 클래스
│   ├── exception/       # BusinessException, ErrorCode, GlobalExceptionHandler
│   ├── response/        # ApiResponse<T>
│   └── lock/            # 분산 락 유틸리티
├── product/
│   ├── domain/          # Product 엔티티
│   ├── repository/      # ProductRepository
│   ├── service/         # ProductService
│   ├── controller/      # ProductController
│   └── dto/             # CreateProductRequest, ProductResponse 등
├── order/
│   ├── domain/          # Order, OrderStatus
│   ├── repository/
│   ├── service/
│   ├── controller/
│   └── dto/
└── payment/
    ├── domain/          # Payment, PaymentStatus, PaymentMethod
    ├── repository/
    ├── service/
    ├── controller/
    └── dto/
```

## 코딩 컨벤션

### 필수 규칙
- 언어는 Kotlin만 사용한다. Java로 작성하지 않는다.
- 금액은 반드시 BigDecimal로 처리한다. Double, Float 사용 금지.
- 모든 API 응답은 `ApiResponse<T>` 공통 래퍼로 감싼다.
- DTO는 Request/Response를 분리하고 data class로 작성한다.
- 엔티티에 data class를 사용하지 않는다.

### 네이밍
- 클래스: PascalCase (`ProductService`, `CreateOrderRequest`)
- 함수/변수: camelCase (`createOrder`, `totalAmount`)
- 상수: UPPER_SNAKE_CASE (`MAX_RETRY_COUNT`)
- 패키지: lowercase (`product`, `common.exception`)
- API 경로: kebab-case 또는 단일 단어 (`/products`, `/payments/confirm`)

### 엔티티 작성 규칙
- `@Entity` 클래스는 domain/ 패키지에 위치
- ID는 `@GeneratedValue(strategy = GenerationType.IDENTITY)` 사용
- 외부 노출용 ID는 UUID 기반 별도 필드 (orderId, paymentKey)
- 생성/수정 시각은 `@CreatedDate`, `@LastModifiedDate` 사용
- 상태 변경은 엔티티 내부 메서드로 캡슐화 (예: `order.markAsPaid()`)

## 아키텍처 규칙

### 레이어 분리
- Controller: 요청 검증(@Valid), 응답 변환만 담당. 비즈니스 로직 금지.
- Service: 비즈니스 로직 집중. @Transactional 관리.
- Repository: Spring Data JPA 인터페이스 사용.

### 트랜잭션
- 읽기 전용 조회: `@Transactional(readOnly = true)`
- 쓰기 작업: `@Transactional`
- 트랜잭션 범위는 최소화한다.

### 예외 처리
- 비즈니스 예외는 `BusinessException`을 상속한다.
- 에러 코드는 `ErrorCode` enum으로 관리한다.
- `GlobalExceptionHandler`(@RestControllerAdvice)에서 일괄 처리한다.
- 에러 응답 형식:
```json
{
  "success": false,
  "error": {
    "code": "OUT_OF_STOCK",
    "message": "재고가 부족합니다.",
    "timestamp": "2025-02-28T10:05:00"
  }
}
```

## API 규칙

- Base URL: `/api/v1`
- 성공 응답: `ApiResponse(success=true, data=...)`
- 에러 응답: `ApiResponse(success=false, error=...)`
- 결제 승인 시 멱등성 키(idempotencyKey)로 중복 요청 방지
- 상태 전이가 유효하지 않으면 409 Conflict 반환

## 테스트 규칙

- 테스트 메서드명은 한글 백틱 사용: `` `재고 부족 시 예외가 발생한다`() ``
- Service 단위 테스트: MockK로 Repository 모킹
- 동시성 테스트: ExecutorService + CountDownLatch
- 테스트 클래스명: `{대상}Test` (예: `OrderServiceTest`, `ConcurrencyTest`)

## 커밋 메시지 규칙

`docs/commit-convention.md`를 따른다.

## 주석 및 설명 규칙

### 주석은 반드시 한글로 작성한다.
- 클래스 상단: 이 클래스가 왜 존재하는지, 어떤 역할을 하는지 한 줄로 설명
- 함수 상단: 이 함수가 어떤 문제를 해결하는지, 핵심 로직이 왜 이렇게 작성되었는지 설명
- 복잡한 분기/로직: 왜 이런 판단을 했는지 의도를 설명 (무엇을 하는지가 아니라 왜 하는지)
- 단순 getter/setter, 자명한 코드에는 주석을 달지 않는다

### 주석 예시
```kotlin
/**
 * 주문 생성 시 재고를 차감한다.
 * 
 * 재고 차감과 주문 생성을 하나의 트랜잭션으로 묶어서
 * 중간에 실패하면 둘 다 롤백되도록 보장한다.
 * 동시 요청 시 Lost Update를 방지하기 위해 낙관적 락(@Version)을 사용한다.
 */
fun createOrder(request: CreateOrderRequest): OrderResponse {
    // 재고 확인을 먼저 하지 않고 바로 차감을 시도한다.
    // 확인 → 차감 사이에 다른 스레드가 끼어들 수 있기 때문이다. (TOCTOU 문제)
    val product = productRepository.findByIdOrThrow(request.productId)
    product.decreaseStock(request.quantity)
    ...
}
```

### 코드 작성 후 설명
- 코드를 생성한 뒤에는 반드시 핵심 결정 사항을 간단히 설명한다.
- "왜 이 방식을 선택했는지", "다른 방법 대비 어떤 이점이 있는지"를 포함한다.
- 학습 목적의 프로젝트이므로, 코드를 읽는 사람이 의도를 이해할 수 있어야 한다.

## 코드 품질 기준

### 시니어 개발자 수준의 방어적 코딩을 지향한다.
- 정상 케이스만이 아니라 예외, 경계값, 동시성 상황을 항상 고려한다.
- "이런 일은 안 일어나겠지"라는 가정을 하지 않는다.

### 구체적 기준
- **Null Safety**: Kotlin의 타입 시스템을 활용한다. `!!` 사용 금지. nullable이면 명시적으로 처리.
- **입력 검증**: Controller에서 @Valid로 기본 검증, Service에서 비즈니스 규칙 검증. 이중으로 방어한다.
- **상태 전이 검증**: 주문/결제 상태 변경 시 현재 상태에서 전이 가능한지 엔티티 내부에서 검증한다.
- **금액 불일치 방지**: 주문 금액과 결제 금액을 반드시 비교 검증한다.
- **동시성 고려**: 재고 차감 등 공유 자원 접근 시 락 전략을 명시한다.
- **멱등성**: 같은 요청이 두 번 와도 결과가 동일해야 한다. 결제 승인은 idempotencyKey로 보장한다.
- **트랜잭션 경계**: 하나의 비즈니스 작업에 포함된 DB 작업은 반드시 같은 트랜잭션에서 처리한다.
- **실패 복구**: 결제 취소 시 재고 복원이 누락되지 않도록 한다.

### 하지 말아야 할 것
- 의미 없는 변수명 (a, b, temp, data)
- 하나의 함수에서 너무 많은 일을 하는 것 (함수 하나는 하나의 책임)
- 매직 넘버 사용 (상수로 추출)
- catch(Exception)으로 모든 예외를 뭉뚱그려 처리하는 것
- 비즈니스 로직을 Controller에 작성하는 것

## 코드 생성 시 주의사항

- 명세서의 엔티티 필드, 타입, 제약조건을 정확히 따를 것
- 새 코드 작성 전 기존 코드와의 일관성을 먼저 확인할 것
- 한 번에 너무 많은 파일을 생성하지 말고, 레이어별로 나눠서 진행할 것

## 개발 로그 (DEVLOG.md) 규칙

모든 작업이 끝나면 반드시 `DEVLOG.md`를 업데이트한다.

### 기록 대상
- **대작업**: 도메인/기능 단위 작업 (예: "Product 도메인 구현", "Redis 캐싱 적용")
- **소작업**: 파일 단위 변경 (예: "build.gradle.kts 의존성 수정", "application.yml 작성")

### 작성 형식
- 대작업은 `### ▶ 작업명` 헤더로 시작한다
- **왜 이 작업을 했는지** 목적을 한 줄로 먼저 서술한다
- 소작업은 대작업 아래에 표 또는 목록으로 정리한다
- 핵심 결정 사항(왜 이 방식을 선택했는지)을 반드시 포함한다
- 날짜는 `## [N주차] 제목 — YYYY-MM-DD` 형식으로 기록한다

### 업데이트 시점
- 새 파일을 생성했을 때
- 기존 파일을 의미 있게 수정했을 때 (오타 수정 등 사소한 변경은 제외)
- 주차 단위 작업이 완료됐을 때
