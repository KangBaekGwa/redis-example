package com.baekgwa.spring.example;

import java.util.List;

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
 * FileName    : PipeliningExample
 * Author      : Baekgwa
 * Date        : 26. 3. 4.
 * Description :
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2026-03-04     Baekgwa               Initial creation
 **/
@Component
public class PipeliningExample {

	private static final Logger log = LoggerFactory.getLogger(PipeliningExample.class);

	private final RedisTemplate<String, String> redisTemplate;

	public PipeliningExample(RedisTemplate<String, String> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public void run() {
		// 0. 초기화
		redisTemplate.execute((RedisCallback<Object>)connection -> {
			connection.serverCommands().flushAll();
			return null;
		});

		runBasicPipelineExample();
		runPipelineVsNoPipelineExample();
	}

	private void runBasicPipelineExample() {
		log.info("=========기본 파이프라이닝=========");

		List<Object> results = redisTemplate.executePipelined(new SessionCallback<Object>() {
			@Override
			public Object execute(RedisOperations operations) throws DataAccessException {
				// 이 안의 명령어들은 모두 큐에 쌓였다가 한 번에 서버로 전송됩니다.
				operations.opsForValue().set("pipe:name", "백과");
				operations.opsForValue().set("pipe:city", "서울");
				operations.opsForValue().get("pipe:name");  // 콜백 내부에서는 null 반환
				operations.opsForValue().get("pipe:city");  // 콜백 내부에서는 null 반환
				operations.opsForValue().increment("pipe:count");
				operations.opsForValue().increment("pipe:count");
				operations.opsForValue().increment("pipe:count");

				return null; // executePipelined 규약: 반드시 null 반환
			}
		});

		// results: [true, true, "백과", "서울", 1L, 2L, 3L]
		// set → Boolean, get → String, increment → Long
		log.info("파이프라인 결과 목록 : {}", results);
	}

	private void runPipelineVsNoPipelineExample() {
		// =========================================================
		// 예제 2: 파이프라이닝 유무에 따른 실행 시간 비교
		// =========================================================
		// 파이프라이닝의 실질적인 효과를 확인하기 위해
		// 동일한 작업을 일반 방식(N번 개별 요청)과 파이프라이닝(1번 일괄 요청)으로 비교합니다.
		//
		// 로컬 환경에서는 RTT 가 매우 짧아 차이가 크지 않을 수 있습니다.
		// 실제 네트워크 지연이 있는 환경(예: 클라이언트 ↔ 원격 Redis)에서 차이가 두드러집니다.
		log.info("=========파이프라이닝 유무 실행 시간 비교=========");

		final int count = 100;

		// 일반 방식: 명령어마다 개별 요청-응답 반복
		long start1 = System.currentTimeMillis();
		for (int i = 0; i < count; i++) {
			redisTemplate.opsForValue().set("no-pipe:key:" + i, "value:" + i);
		}
		long elapsed1 = System.currentTimeMillis() - start1;
		log.info("일반 방식 ({}회 개별 요청) : {}ms", count, elapsed1);

		// 파이프라이닝 방식: 명령어를 모아 한 번에 전송
		long start2 = System.currentTimeMillis();
		redisTemplate.executePipelined(new SessionCallback<Object>() {
			@Override
			public Object execute(RedisOperations operations) throws DataAccessException {
				for (int i = 0; i < count; i++) {
					operations.opsForValue().set("pipe:key:" + i, "value:" + i);
				}
				return null;
			}
		});
		long elapsed2 = System.currentTimeMillis() - start2;
		log.info("파이프라이닝 방식 ({}회 일괄 요청) : {}ms", count, elapsed2);

		log.info("→ 네트워크 지연이 클수록 파이프라이닝의 효과가 더욱 두드러집니다.");
	}
}
