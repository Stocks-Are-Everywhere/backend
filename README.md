# 📦 Order-service (2차)

### 🔖 서비스 개요
- **주제**: 주문 처리 및 체결 엔진 고도화
- **목표**: 다중 종목 + 다중 사용자 환경에서의 처리 성능 확보
- **기술 스택**: Spring Boot, MySQL, WebSocket, JPA, HikariCP, Concurrent Collection

</br>

### 🧩 주요 기능

#### 1️⃣ 주문 생성 및 검증
- 사용자로부터 주문 요청을 수신
- 가격 유효성, 보유 자산 검증 (Account/Asset 서비스 연동)
- 검증 통과 시 주문 등록

#### 2️⃣ 주문 체결 로직
- 종목별로 `ConcurrentSkipListMap<Price, ConcurrentSkipListSet<Order>>` 사용
- 가격 우선 → 시간 순 우선 체결 방식
- 체결된 주문은 `TradeHistory`에 저장

#### 3️⃣ 실시간 차트 및 체결 정보 전송
- 주문 체결 시 WebSocket을 통해 클라이언트로 실시간 데이터 전송
- `@Scheduled`를 활용한 15초 단위 Candle 갱신

#### 4️⃣ 거래 내역 및 차트 초기화
- 서버 시작 시 DB에서 거래 내역을 로딩 후 차트 초기화
- 종목별 Lock (`ReentrantReadWriteLock`)을 활용한 병렬 접근 제어

</br>

---

### ⚙️ 성능 개선 테스트 (2차)

### ✅ 테스트 조건
- 종목 수: 20개  
- 사용자 수: 1,000명  
- 동작 시간: 10분  
- 테스트 도구: NGrinder  
- 총 요청 수: 약 140,000건

---

## 📊 성능 개선 조합별 테스트

### ✅ 2차 성능 테스트 Before
- 기존 TreeMap + PriorityQueue 사용

<img width="964" alt="2차 성능 테스트 Before" src="https://github.com/user-attachments/assets/22b5e24c-c45c-4501-adfd-71084620ebed" />

---

### ✅ Case 1
- Order: `ReentrantLock`  
- Account: 비관적 락 (Pessimistic Lock)

<img width="1000" alt="2차 성능 테스트 case1 After" src="https://github.com/user-attachments/assets/39ec8ac3-79b6-49a1-ad91-e9c0f0334562" />

---

### ✅ Case 1 + 쿼리 튜닝
- Order: `ReentrantLock`  
- Account: 비관적 락 (Pessimistic Lock)
- 쿼리 튜닝

<img width="1005" alt="2차 성능테스트 case1 + 쿼리 튜닝 After" src="https://github.com/user-attachments/assets/94a84546-c01e-447d-98f0-b7d17bcd2186" />

---
### ✅ Case 1 + 쿼리 튜닝, 커넥션 풀 튜닝
- Order: `ReentrantLock`  
- Account: 비관적 락 (Pessimistic Lock)
- 쿼리 튜닝
- 커넥션 풀 튜닝 

<img width="1021" alt="2차 성능테스트 case1 + 쿼리 튜닝 + 커넥션 풀 튜닝 After" src="https://github.com/user-attachments/assets/af19db65-a2b1-4ec7-bd58-6a7a3524eb46" />

---

### ✅ Case 2  
- Order: `synchronized`  
- Account: 낙관적 락 (Optimistic Lock)  

<img width="909" alt="2차 성능테스트 case2 After" src="https://github.com/user-attachments/assets/33e577c5-5ea2-498a-abea-fdddae9d91bd" />

---

### ✅ Case 2  
- Order: `synchronized`  
- Account: 낙관적 락 (Optimistic Lock)
- 쿼리 튜닝


<img width="995" alt="2차 성능테스트 case2 + 쿼리튜닝 After" src="https://github.com/user-attachments/assets/b2acaf0f-1076-4375-8f05-eba369cc22ae" />

---

### ✅ Case 2  
- Order: `synchronized`  
- Account: 낙관적 락 (Optimistic Lock)
- 쿼리 튜닝
- 커넥션 풀 튜닝


<img width="1000" alt="2차 성능테스트 case2 + 쿼리튜닝 + 커넥션 풀 튜닝 After" src="https://github.com/user-attachments/assets/c7c10a9d-157a-4796-a80b-ea994138bded" />

</br>

## 📈 최종 성능 개선 정리
| 구분       | TPS   | 최고 TPS | 에러 |
|------------|--------|-----------|--------|
| **Before** | 145.5  | 191.0     | 7,677     |
| **After**  | 235.7  | 276.0     | 0   |
- **Before**: TreeMap + PriorityQueue
- **After**: `ConcurrentSkipListMap` + `ConcurrentSkipListSet` + 쿼리 튜닝 + 커넥션 풀 튜닝
- **결과**: 종목 20개, 유저 1000명 기준 → **약 62% 성능 향상**

</br>

---

### 🛠 트러블슈팅 & 개선 이슈

#### ⚠ 체결 로직 동시성 이슈
- 해결: `ConcurrentSkipListMap` + `ConcurrentSkipListSet` + Lock 기반 제어

#### ⚠ 커넥션 풀 부족
- 해결: HikariCP max pool → 30, MySQL max_connections → 300

#### ⚠ N+1 문제
- 해결: 조건부 Fetch Join 적용으로 쿼리 수 감소

</br>

---

> 체결 로직과 Account 동기화를 모두 고려하며 다양한 락 전략 및 구조 조합을 테스트했습니다.  
> 실제 트래픽 환경을 기반으로 성능을 수치화하고, 분산 구조로의 확장을 위한 기반을 완성한 단계였습니다.
