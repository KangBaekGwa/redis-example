package com.baekgwa.spring.example;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Component;

/**
 * PackageName : com.baekgwa.spring.example
 * FileName    : CouponFailureExample
 * Author      : Baekgwa
 * Date        : 26. 3. 4.
 * Description :
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2026-03-04     Baekgwa               Initial creation
 **/
@Component
public class CouponFailureExample {

	private static final Logger log = LoggerFactory.getLogger(CouponFailureExample.class);

	// 쿠폰 재고
	private static final int INITIAL_COUPON = 10;
	// 동시 요청 수 (재고보다 훨씬 많아야 경쟁 조건이 잘 드러납니다)
	private static final int THREAD_COUNT = 50;

	private final RedisTemplate<String, String> redisTemplate;

	public CouponFailureExample(RedisTemplate<String, String> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public void run() throws InterruptedException {
		redisTemplate.execute((RedisCallback<Object>)connection -> {
			connection.serverCommands().flushAll();
			return null;
		});

		// runPipelineFailureExample();
		runTransactionFailureExample();
	}

	private void runPipelineFailureExample() throws InterruptedException {
		log.info("=========파이프라이닝으로 선착순 쿠폰 발급 시도 (실패 사례)=========");
		log.info("쿠폰 재고: {}, 동시 요청: {}", INITIAL_COUPON, THREAD_COUNT);

		redisTemplate.opsForValue().set("coupon:pipe:count", String.valueOf(INITIAL_COUPON));

		AtomicInteger issuedCount = new AtomicInteger(0);
		ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
		CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(THREAD_COUNT);

		for (int i = 0; i < THREAD_COUNT; i++) {
			executor.submit(() -> {
				ready.countDown();
				try {
					start.await(); // 모든 스레드가 준비되면 동시에 출발

					String countStr = redisTemplate.opsForValue().get("coupon:pipe:count");

					// 재고가 있으면 쿠폰 발급 로직 실행
					if (countStr != null && Integer.parseInt(countStr) > 0) {
						redisTemplate.executePipelined(new SessionCallback<>() {
							@Override
							public Object execute(RedisOperations ops) throws DataAccessException {
								ops.opsForValue().decrement("coupon:pipe:count");
								return null;
							}
						});

						issuedCount.incrementAndGet();
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

		String finalCount = redisTemplate.opsForValue().get("coupon:pipe:count");
		log.info("발급된 쿠폰 수: {}, 최종 재고: {}", issuedCount.get(), finalCount);

		int remaining = Integer.parseInt(finalCount);
		if (remaining < 0) {
			log.info("→ 초과 발급 발생! 재고가 {}개인데 {}장이 발급됨", INITIAL_COUPON, issuedCount.get());
		} else {
			log.info("→ 이번 실행에서는 초과 발급이 발생하지 않았으나, Race Condition 구조적으로 존재합니다.");
		}
	}

	private void runTransactionFailureExample() throws InterruptedException {
		log.info("=========트랜잭션(MULTI/EXEC)으로 선착순 쿠폰 발급 시도 (실패 사례)=========");

		// [사전 확인] MULTI 내부에서 get() 반환값이 실제로 null 인지 확인
		log.info("--- MULTI 내부 get() 반환값 확인 ---");
		redisTemplate.opsForValue().set("coupon:tx:count", String.valueOf(INITIAL_COUPON));

		redisTemplate.execute(new SessionCallback<List<Object>>() {
			@Override
			public List<Object> execute(RedisOperations ops) throws DataAccessException {
				ops.multi();

				// MULTI 내부에서 get() 호출 → 큐에만 쌓이고 즉시 실행되지 않으므로 null 반환
				String countInMulti = (String) ops.opsForValue().get("coupon:tx:count");
				log.info("MULTI 내부 get() 반환값: {} → 조건 분기 불가", countInMulti);

				// countInMulti 가 null 이므로 if (Integer.parseInt(countInMulti) > 0) 불가
				// 결국 조건 없이 무조건 차감할 수밖에 없음
				ops.opsForValue().decrement("coupon:tx:count");

				return ops.exec();
			}
		});

		log.info("단일 실행 후 재고: {}", redisTemplate.opsForValue().get("coupon:tx:count"));

		// [동시 요청] 50개 스레드가 모두 조건 없이 DECR
		log.info("--- 동시 {}개 요청: 모든 스레드가 조건 없이 DECR ---", THREAD_COUNT);
		redisTemplate.opsForValue().set("coupon:tx:count", String.valueOf(INITIAL_COUPON));

		ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
		CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(THREAD_COUNT);

		for (int i = 0; i < THREAD_COUNT; i++) {
			executor.submit(() -> {
				ready.countDown();
				try {
					start.await();

					redisTemplate.execute(new SessionCallback<List<Object>>() {
						@Override
						public List<Object> execute(RedisOperations ops) throws DataAccessException {
							ops.multi();

							// MULTI 내부: get() → null → 조건 분기 불가 → 무조건 차감
							ops.opsForValue().decrement("coupon:tx:count");

							return ops.exec();
						}
					});

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

		String finalCount = redisTemplate.opsForValue().get("coupon:tx:count");
		// INITIAL_COUPON(10) - THREAD_COUNT(50) = -40 예상
		log.info("쿠폰 재고: {}, 동시 요청: {}, 최종 재고: {}", INITIAL_COUPON, THREAD_COUNT, finalCount);

		int remaining = Integer.parseInt(finalCount);
		if (remaining < 0) {
			log.info("→ 초과 발급 발생! MULTI 내부에서 재고를 읽어도 null → 조건 분기 불가 → {}장 초과 발급",
				Math.abs(remaining));
		}
	}
}
