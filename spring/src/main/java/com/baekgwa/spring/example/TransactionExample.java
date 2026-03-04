package com.baekgwa.spring.example;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

/**
 * PackageName : com.baekgwa.spring.example
 * FileName    : TransactionExample
 * Author      : Baekgwa
 * Date        : 26. 3. 4.
 * Description :
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2026-03-04     Baekgwa               Initial creation
 **/
@Component
public class TransactionExample {

	private static final Logger log = LoggerFactory.getLogger(TransactionExample.class);

	private final RedisTemplate<String, String> redisTemplate;

	public TransactionExample(RedisTemplate<String, String> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public void run() {
		// 0. 초기화
		redisTemplate.execute((RedisCallback<Object>)connection -> {
			connection.serverCommands().flushAll();
			return null;
		});

		runMultiExecExample();
		runWatchExample();
	}

	private void runMultiExecExample() {
		log.info("=========MULTI / EXEC 기본 트랜잭션=========");

		List<Object> results = redisTemplate.execute(new SessionCallback<List<Object>>() {
			@Override
			public List<Object> execute(RedisOperations operations) throws DataAccessException {
				operations.multi(); // MULTI: 트랜잭션 시작

				// 이 사이의 명령어들은 큐에 쌓입니다. (즉시 실행 X)
				ValueOperations op = operations.opsForValue();
				op.set("tx:name", "백과");
				op.set("tx:count", "0");
				op.increment("tx:count");
				op.increment("tx:count");

				return operations.exec(); // EXEC: 실제 명령어 일괄 실행
			}
		});

		// results 에는 각 명령어의 실행 결과가 순서대로 담겨있습니다.
		// [set → true, set → true, incr → 1L, incr → 2L]
		log.info("트랜잭션 결과 목록 : {}", results);

		String txName = redisTemplate.opsForValue().get("tx:name");
		String txCount = redisTemplate.opsForValue().get("tx:count");
		log.info("tx:name = {}, tx:count = {}", txName, txCount);
	}

	private void runWatchExample() {
		log.info("=========WATCH + MULTI/EXEC 낙관적 락=========");

		redisTemplate.opsForValue().set("watch:stock", "10");
		log.info("초기 재고 : {}", redisTemplate.opsForValue().get("watch:stock"));

		// --- 케이스 1: WATCH 이후 외부 변경 없음 → 트랜잭션 성공 ---
		log.info("--- 케이스 1: 외부 변경 없음 → 트랜잭션 성공 ---");

		List<Object> successResult = redisTemplate.execute(new SessionCallback<>() {
			@Override
			public List<Object> execute(RedisOperations operations) throws DataAccessException {
				operations.watch("watch:stock"); // WATCH: 키 감시 시작
				operations.multi();
				operations.opsForValue().decrement("watch:stock"); // 재고 1 차감
				return operations.exec(); // null 이 아니면 성공
			}
		});

		if (successResult != null) {
			log.info("케이스 1 → 트랜잭션 성공! 결과 : {}", successResult);
			log.info("남은 재고 : {}", redisTemplate.opsForValue().get("watch:stock"));
		} else {
			log.info("케이스 1 → 트랜잭션 취소 (충돌 감지)");
		}

		// --- 케이스 2: WATCH 이후 외부에서 값 변경 → 트랜잭션 취소 ---
		log.info("--- 케이스 2: 외부 변경 발생 → 트랜잭션 취소 ---");

		List<Object> failResult = redisTemplate.execute(new SessionCallback<List<Object>>() {
			@Override
			public List<Object> execute(RedisOperations operations) throws DataAccessException {
				operations.watch("watch:stock"); // WATCH: 키 감시 시작
				redisTemplate.opsForValue().set("watch:stock", "999"); // 외부 변경 시뮬레이션

				operations.multi();
				operations.opsForValue().decrement("watch:stock");
				return operations.exec(); // WATCH 감지 → null 반환 (트랜잭션 취소)
			}
		});

		if (failResult == null) {
			log.info("케이스 2 → 트랜잭션 취소! (외부에서 watch:stock 변경 감지)");
			log.info("현재 재고 (외부 변경값 그대로 유지) : {}", redisTemplate.opsForValue().get("watch:stock"));
			log.info("→ 재시도 로직을 추가하여 충돌 시 다시 시도하는 패턴으로 활용합니다.");
		} else {
			log.info("케이스 2 → 트랜잭션 성공 (예상치 못한 결과) : {}", failResult);
		}
	}
}
