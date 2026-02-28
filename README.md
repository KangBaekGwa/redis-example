# Redis String 타입 예제

Redis의 활용법을 Java(Lettuce), Node.js(ioredis), Spring Boot(Spring Data Redis) 3가지 환경에서 동일하게 구현한 예제 프로젝트입니다.

---

## 1. Redis 시작하기

### Docker로 Redis 실행

```bash
cd infra/single
docker compose up -d
```

### 접속 확인

```bash
docker exec -it redis-single redis-cli ping
# 출력: PONG
```

### Redis 중지

```bash
cd infra/single
docker-compose down
```

---

## 2. 참조 하면 좋은 공식 문서

[redis.io](https://redis.io/docs/latest/develop/clients/lettuce/)
[Spring Data Redis](https://docs.spring.io/spring-data/redis/reference/redis/getting-started.html)

> Spring Data Redis 는 Spring Boot 의 Auto Configuration 을 활용하였습니다.
> [알아두면 쓸만한 개발 잡학사전](https://devel-repository.tistory.com/82#Spring%20Data%20Redis%20Properties) 에 관련된 내용이 잘 설명되어있습니다.

## 3. JAVA 예제 실행

### 실행

Main.java 에서 실행하고 싶은 메서드만 주석 해제 합니다.

`Main.java`

```java
public class Main {

	public static void main(String[] args) {

		// 1. String 예제
		StringExample.start();

		// 2. List 예제
		//ListExample.start();
	}
}
```

이후, 다음과 같이 실행합니다.

```bash
cd java
./gradlew run
```

---

## 4. Node.js 예제 실행

### 사전 요구사항

- Node.js 18 이상
- npm

### 실행

```bash
cd node
npm install
npm start ${실행할 스크립트}
```

실행할 스크립트는 ...\package.json 에 `scripts` 에 작성되어있습니다.
string 예시는 `npm start string` 으로 실행하면 됩니다.

---

## 5. Spring Boot 예제 실행

### 실행

...\spring\ExampleRunner.java 의 `run()` 메서드에서 실행할 테스트만 주석 해제

`ExampleRunner.java`

```java
@Component
public class ExampleRunner implements CommandLineRunner {

	private final StringExample stringExample;

	public ExampleRunner(StringExample stringExample) {
		this.stringExample = stringExample;
	}

	@Override
	public void run(String... args) {
		stringExample.run();

        // listExample.run();
	}
}

```

```bash
cd spring
./gradlew bootRun
```

---

## 6. 프로젝트 구조

```
redis-example/
├── README.md
├── infra/
│   └── single/
│       └── docker-compose.yml          # Redis 7.2-alpine, 포트 6379
├── java/                               # Lettuce 동기 클라이언트 예제
│   ├── build.gradle
│   ├── settings.gradle
│   └── src/main/java/com/baekgwa/
│       ├── connect/                    # Redis 연결 설정
│       ├── Main.java                   # 진입점
│       └── *Example.java              # 타입별 예제
├── node/                               # ioredis TypeScript 예제
│   ├── package.json
│   ├── tsconfig.json
│   └── src/
│       ├── connection/                 # Redis 연결 설정
│       └── {type}/                    # 타입별 예제 폴더
└── spring/                             # Spring Boot + Spring Data Redis 예제
    ├── build.gradle
    ├── settings.gradle
    └── src/main/
        ├── java/com/baekgwa/spring/
        │   ├── Application.java        # Spring Boot 진입점
        │   ├── ExampleRunner.java      # 예제 실행 진입점
        │   ├── config/                 # Redis 설정
        │   └── example/               # 타입별 예제
        └── resources/
            └── application.yml        # Redis 연결 설정
```
