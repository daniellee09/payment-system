# Commit Message Convention

## Format

```
<type>(<scope>): <subject>

<body>
```

## Type

| Type | 사용 시점 |
|------|-----------|
| feat | 새로운 기능 추가 |
| fix | 버그 수정 |
| refactor | 기능 변경 없는 코드 구조 개선 |
| test | 테스트 추가 또는 수정 |
| docs | 문서 변경 (README, CLAUDE.md 등) |
| chore | 빌드 설정, 의존성, CI 등 코드 외 변경 |
| style | 포맷팅, 세미콜론 등 로직 변경 없는 수정 |
| perf | 성능 개선 |

## Scope

변경 대상 도메인 또는 레이어를 괄호 안에 명시한다. 생략 가능.

- `product`, `order`, `payment` — 도메인
- `common` — 공통 모듈 (exception, response, config)
- `infra` — Docker, CI/CD, 설정 파일
- `test` — 테스트 전반

## Subject (제목)

- 영문 소문자로 시작한다
- 50자 이내로 작성한다
- 명령문(imperative)으로 작성한다: "add", "fix", "implement" (added, fixed ❌)
- 끝에 마침표를 붙이지 않는다

## Body (본문)

- 제목과 본문 사이에 빈 줄 하나를 넣는다
- **무엇을** 바꿨는지가 아니라 **왜** 바꿨는지를 설명한다
- 72자에서 줄바꿈한다
- 영문으로 작성한다

## Examples

### 기능 추가
```
feat(order): implement order creation API

Add order creation endpoint with stock validation.
Stock is decreased within the same transaction to prevent
inconsistency on failure.
```

### 버그 수정
```
fix(payment): prevent duplicate payment approval

Check idempotency key before processing payment.
Previously, concurrent requests with the same key could
result in double charges.
```

### 리팩토링
```
refactor(product): extract stock validation to domain method

Move stock check logic from service to Product entity
to encapsulate domain rules and improve testability.
```

### 테스트 추가
```
test(concurrency): add multithreaded stock deduction test

Verify that 100 concurrent order requests against
stock of 100 result in exactly 0 remaining stock
using CountDownLatch and ExecutorService.
```

### 설정 변경
```
chore(infra): add Redis to docker-compose

Add Redis 7 Alpine container for caching and
distributed lock support.
```

### 성능 개선
```
perf(product): apply Redis caching to product list query

Cache product list with 5-minute TTL to reduce
database load on repeated read requests.
```

## Rules

- 하나의 커밋에 하나의 목적만 담는다. 기능 추가와 리팩토링을 섞지 않는다.
- 동작하는 상태에서만 커밋한다. 빌드가 깨진 상태로 커밋하지 않는다.
- 본문에는 코드 변경의 동기와 맥락을 적는다. diff를 보면 알 수 있는 내용을 반복하지 않는다.
