package com.baekgwa.spring.example;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * PackageName : com.baekgwa.spring.example
 * FileName    : LuaScriptExample
 * Author      : Baekgwa
 * Date        : 26. 3. 4.
 * Description : Redis Lua Script 예제
 *               1) Lua Script 기본 학습 (EVAL, KEYS, ARGV, 조건 분기, 원자적 카운터)
 *               2) Lua Script 로 선착순 쿠폰 발급 Race Condition 해결
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2026-03-04     Baekgwa               Initial creation
 **/
@Component
public class LuaScriptExample {

	private static final Logger log = LoggerFactory.getLogger(LuaScriptExample.class);

	// 쿠폰 재고
	private static final int INITIAL_COUPON = 10;
	// 동시 요청 수
	private static final int THREAD_COUNT = 50;

	private final RedisTemplate<String, String> redisTemplate;

	public LuaScriptExample(RedisTemplate<String, String> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public void run() throws InterruptedException {
		redisTemplate.execute((RedisCallback<Object>)connection -> {
			connection.serverCommands().flushAll();
			return null;
		});

		runBasicLuaScriptExample();
		runCouponWithLuaScriptExample();
		runLuaScriptFromFileExample();
	}

	/**
	 * Lua Script 기본 학습 예제
	 *
	 * Redis EVAL 명령어 핵심 개념:
	 *   EVAL <script> <numkeys> [key ...] [arg ...]
	 *   - KEYS[1], KEYS[2], ... : Redis 키 목록 (클러스터 라우팅 힌트로도 사용)
	 *   - ARGV[1], ARGV[2], ... : 추가 인자
	 *   - redis.call()          : Redis 명령 실행 (에러 시 예외 발생)
	 *   - 스크립트 전체가 단일 원자적 작업으로 실행 → 중간에 다른 명령 끼어들기 불가
	 */
	private void runBasicLuaScriptExample() {
		log.info("=========Lua Script 기본 학습 예제=========");

		// ── 예제 1: 단순 문자열 반환 ──────────────────────────────────────────
		// Lua 스크립트가 실행되고 결과를 Java 로 반환하는 가장 단순한 형태
		log.info("--- 예제 1: Lua 에서 단순 값 반환 ---");
		DefaultRedisScript<String> helloScript = new DefaultRedisScript<>();
		helloScript.setScriptText("return 'Hello from Lua!'");
		helloScript.setResultType(String.class);

		String hello = redisTemplate.execute(helloScript, Collections.emptyList());
		log.info("Lua 반환값: {}", hello);

		// ── 예제 2: KEYS, ARGV 를 이용한 SET/GET ─────────────────────────────
		// KEYS[1]에 키 이름, ARGV[1]에 값을 전달해 SET 후 GET 결과를 반환
		// → 두 명령이 하나의 원자적 블록으로 실행됨
		log.info("--- 예제 2: KEYS, ARGV 를 이용한 SET/GET ---");
		DefaultRedisScript<String> setGetScript = new DefaultRedisScript<>();
		setGetScript.setScriptText(
			"redis.call('SET', KEYS[1], ARGV[1]) " +
			"return redis.call('GET', KEYS[1])"
		);
		setGetScript.setResultType(String.class);

		String setGetResult = redisTemplate.execute(
			setGetScript,
			List.of("lua:test:key"),
			"lua-value"
		);
		log.info("SET 후 GET 결과: {}", setGetResult);
		// CouponFailureExample 의 핵심 문제: MULTI 내부에서 GET 은 null 을 반환해 조건 분기 불가
		// → Lua 에서는 redis.call('GET', ...) 결과를 즉시 변수에 담아 조건 분기 가능
		log.info("  ※ MULTI/EXEC 와 달리 Lua 내부에서 GET 결과를 즉시 변수로 사용할 수 있습니다.");

		// ── 예제 3: 조건 분기 (재고 확인 후 원자적 차감) ─────────────────────
		// Lua 에서 tonumber() 로 문자열 → 숫자 변환 후 if 분기 가능
		// stock > 0 이면 DECR 후 차감 전 재고 반환, 아니면 0 반환
		log.info("--- 예제 3: 조건 분기 (GET 결과를 즉시 활용해 재고 차감) ---");
		redisTemplate.opsForValue().set("lua:stock", "3");

		DefaultRedisScript<Long> conditionalScript = new DefaultRedisScript<>();
		conditionalScript.setScriptText(
			"local stock = tonumber(redis.call('GET', KEYS[1])) " +
			"if stock == nil then return -1 end " +
			"if stock > 0 then " +
			"  redis.call('DECR', KEYS[1]) " +
			"  return stock " +    // 차감 전 재고 반환
			"else " +
			"  return 0 " +        // 재고 없음
			"end"
		);
		conditionalScript.setResultType(Long.class);

		for (int i = 1; i <= 5; i++) {
			Long stockBefore = redisTemplate.execute(conditionalScript, List.of("lua:stock"));
			String stockAfter = redisTemplate.opsForValue().get("lua:stock");
			if (stockBefore != null && stockBefore > 0) {
				log.info("  시도 {}: 차감 전 재고={}, 차감 후 재고={}", i, stockBefore, stockAfter);
			} else {
				log.info("  시도 {}: 재고 없음 (반환값={}), 현재 재고={}", i, stockBefore, stockAfter);
			}
		}

		// ── 예제 4: 원자적 카운터 (최대값 제한) ──────────────────────────────
		// INCR 전에 현재 값을 확인해 최대값 초과 시 거부하는 패턴
		// → GET + INCR 을 원자적으로 처리하므로 Race Condition 없음
		log.info("--- 예제 4: 원자적 카운터 (최대값 제한) ---");
		redisTemplate.opsForValue().set("lua:counter", "0");

		DefaultRedisScript<Long> counterScript = new DefaultRedisScript<>();
		counterScript.setScriptText(
			"local current = tonumber(redis.call('GET', KEYS[1])) " +
			"local max = tonumber(ARGV[1]) " +
			"if current < max then " +
			"  return redis.call('INCR', KEYS[1]) " +
			"else " +
			"  return -1 " +       // 최대값 도달 → 거부
			"end"
		);
		counterScript.setResultType(Long.class);

		int maxCount = 3;
		for (int i = 1; i <= 5; i++) {
			Long count = redisTemplate.execute(counterScript, List.of("lua:counter"), String.valueOf(maxCount));
			if (count != null && count >= 0) {
				log.info("  시도 {}: 카운터 증가 → {}", i, count);
			} else {
				log.info("  시도 {}: 최대값({}) 도달, 증가 거부", i, maxCount);
			}
		}

		log.info("Lua Script 기본 학습 완료");
	}

	/**
	 * Lua Script 로 선착순 쿠폰 발급 Race Condition 해결
	 *
	 * CouponFailureExample 의 문제점 요약:
	 *   - Pipeline : GET 과 DECR 사이에 다른 스레드가 끼어들어 음수 재고 발생
	 *   - Transaction(MULTI/EXEC) : MULTI 내부 GET 이 null → 조건 분기 불가 → 무조건 DECR
	 *
	 * Lua Script 해결 원리:
	 *   Redis 는 단일 스레드로 Lua 스크립트를 실행하므로
	 *   "재고 확인 → 조건 분기 → 차감" 전체가 하나의 원자적 연산이 됨
	 *   → 어떤 클라이언트도 스크립트 실행 도중 끼어들 수 없음
	 */
	private void runCouponWithLuaScriptExample() throws InterruptedException {
		log.info("=========Lua Script 로 선착순 쿠폰 발급 (Race Condition 해결)=========");
		log.info("쿠폰 재고: {}, 동시 요청: {}", INITIAL_COUPON, THREAD_COUNT);

		redisTemplate.opsForValue().set("coupon:lua:count", String.valueOf(INITIAL_COUPON));

		// 재고 확인 + 차감을 하나의 원자적 Lua 스크립트로 처리
		// 반환값: 1 = 발급 성공, 0 = 재고 소진으로 발급 실패
		DefaultRedisScript<Long> couponScript = new DefaultRedisScript<>();
		couponScript.setScriptText(
			"local stock = tonumber(redis.call('GET', KEYS[1])) " +
			"if stock == nil or stock <= 0 then " +
			"  return 0 " +         // 발급 실패
			"end " +
			"redis.call('DECR', KEYS[1]) " +
			"return 1"              // 발급 성공
		);
		couponScript.setResultType(Long.class);

		AtomicInteger issuedCount = new AtomicInteger(0);
		AtomicInteger failedCount = new AtomicInteger(0);
		ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
		CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(THREAD_COUNT);

		for (int i = 0; i < THREAD_COUNT; i++) {
			executor.submit(() -> {
				ready.countDown();
				try {
					start.await(); // 모든 스레드가 준비되면 동시에 출발

					Long result = redisTemplate.execute(couponScript, List.of("coupon:lua:count"));
					if (result != null && result == 1L) {
						issuedCount.incrementAndGet(); // 발급 성공
					} else {
						failedCount.incrementAndGet(); // 재고 소진
					}

				} catch (Exception ignored) {
				} finally {
					done.countDown();
				}
			});
		}

		ready.await();
		start.countDown(); // 동시 시작
		done.await();
		executor.shutdown();

		String finalCount = redisTemplate.opsForValue().get("coupon:lua:count");
		log.info("발급 성공: {}장, 발급 거부: {}건, 최종 재고: {}", issuedCount.get(), failedCount.get(), finalCount);

		int remaining = Integer.parseInt(finalCount);
		if (remaining == 0 && issuedCount.get() == INITIAL_COUPON) {
			log.info("→ 정확히 {}장만 발급됨. Lua Script 로 Race Condition 완전 해결!", INITIAL_COUPON);
		} else if (remaining < 0) {
			log.info("→ 초과 발급 발생 (예상치 못한 오류)");
		} else {
			log.info("→ 발급 수: {}, 남은 재고: {} (재고가 남은 채로 요청이 끝남)", issuedCount.get(), remaining);
		}
	}

	/**
	 * .lua 파일로 분리된 스크립트를 ClassPathResource 로 로드하는 예제
	 *
	 * 인라인 스트링 방식의 단점:
	 *   - 문자열 이어붙이기로 가독성이 떨어짐
	 *   - IDE 의 Lua 문법 지원(하이라이팅, 자동완성)을 받을 수 없음
	 *   - 스크립트 재사용이 어려움
	 *
	 * 파일 분리 방식의 장점:
	 *   - src/main/resources/scripts/*.lua 로 관리 → IDE Lua 플러그인 지원 가능
	 *   - setLocation(ClassPathResource) 으로 로드 → 빌드 시 classpath 에 포함됨
	 *   - 여러 곳에서 같은 .lua 파일을 재사용 가능
	 *   - 단, 컴파일 타임 검증은 여전히 불가 (파일 없으면 런타임 IOException)
	 */
	private void runLuaScriptFromFileExample() throws InterruptedException {
		log.info("========= .lua 파일 분리 로드 예제 =========");

		// ── set-and-get.lua 로드 ──────────────────────────────────────────────
		// scripts/set-and-get.lua 파일을 ClassPathResource 로 지정
		// DefaultRedisScript 는 최초 execute() 시점에 파일을 읽어 캐싱함
		log.info("--- set-and-get.lua 로드 ---");
		DefaultRedisScript<String> setAndGetScript = new DefaultRedisScript<>();
		setAndGetScript.setLocation(new ClassPathResource("scripts/set-and-get.lua"));
		setAndGetScript.setResultType(String.class);

		String result = redisTemplate.execute(
			setAndGetScript,
			List.of("lua:file:key"),
			"hello-from-file"
		);
		log.info("set-and-get.lua 실행 결과: {}", result);

		// ── coupon-issue.lua 로드 ─────────────────────────────────────────────
		// 인라인 스트링으로 작성했던 쿠폰 스크립트를 파일로 분리한 버전
		log.info("--- coupon-issue.lua 로드 ({}장 재고, {}개 요청) ---", INITIAL_COUPON, THREAD_COUNT);
		redisTemplate.opsForValue().set("coupon:lua:file:count", String.valueOf(INITIAL_COUPON));

		DefaultRedisScript<Long> couponScript = new DefaultRedisScript<>();
		couponScript.setLocation(new ClassPathResource("scripts/coupon-issue.lua"));
		couponScript.setResultType(Long.class);

		AtomicInteger issuedCount = new AtomicInteger(0);
		AtomicInteger failedCount = new AtomicInteger(0);
		ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
		CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(THREAD_COUNT);

		for (int i = 0; i < THREAD_COUNT; i++) {
			executor.submit(() -> {
				ready.countDown();
				try {
					start.await();

					Long issued = redisTemplate.execute(couponScript, List.of("coupon:lua:file:count"));
					if (issued != null && issued == 1L) {
						issuedCount.incrementAndGet();
					} else {
						failedCount.incrementAndGet();
					}

				} catch (Exception ignored) {
				} finally {
					done.countDown();
				}
			});
		}

		ready.await();
		start.countDown();
		done.await();
		executor.shutdown();

		String finalCount = redisTemplate.opsForValue().get("coupon:lua:file:count");
		log.info("발급 성공: {}장, 발급 거부: {}건, 최종 재고: {}", issuedCount.get(), failedCount.get(), finalCount);
		log.info("→ 파일 분리 방식도 동일하게 Race Condition 없이 정확히 동작합니다.");
	}
}
