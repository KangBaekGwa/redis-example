package com.baekgwa.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RunnableFuture;

import com.baekgwa.connect.RedisConnect;

import io.lettuce.core.LettuceFutures;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * PackageName : com.baekgwa.example
 * FileName    : PipeliningExample
 * Author      : Baekgwa
 * Date        : 26. 3. 4.
 * Description :
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2026-03-04     Baekgwa               Initial creation
 **/

// =====================================================================================================================
// Redis 파이프라이닝이란?
// =====================================================================================================================
// 파이프라이닝은 여러 명령어를 개별 요청-응답 없이 한 번에 묶어서 서버로 전송하는 기법입니다.
//
// 일반 방식: [요청1] → [응답1] → [요청2] → [응답2] → ... (RTT 마다 대기)
// 파이프라이닝: [요청1, 요청2, 요청3, ...] → [응답1, 응답2, 응답3, ...] (RTT 1회)
//
// =====================================================================================================================
// 트랜잭션(MULTI/EXEC)과의 차이
// =====================================================================================================================
// - 파이프라이닝  : 단순 네트워크 최적화. 원자성 보장 X, 중간에 다른 클라이언트 명령 끼어들 수 있음.
// - 트랜잭션(MULTI/EXEC) : 원자성 보장. EXEC 전까지 큐에 쌓이며, 실행은 일괄 처리.
//
// =====================================================================================================================
// Lettuce 에서의 파이프라이닝
// =====================================================================================================================
// Lettuce 는 기본적으로 비동기 방식이며, 명령어를 자동으로 즉시 전송합니다. (autoFlush = true)
// 파이프라이닝을 위해서는 autoFlush 를 false 로 설정하고, 명령어를 쌓은 뒤 flushCommands() 를 호출합니다.
//
// connection.setAutoFlushCommands(false); // 자동 전송 비활성화 (큐에만 쌓음)
// ... async 명령어 발행 ...
// async.flushCommands();                  // 큐에 쌓인 명령어를 한 번에 전송
// LettuceFutures.awaitAll(...);           // 모든 결과 수신 대기
// connection.setAutoFlushCommands(true);  // 복원
// =====================================================================================================================
public class PipeliningExample {

	public static void start() throws Exception {
		RedisConnect redisConnect = new RedisConnect();

		try (StatefulRedisConnection<String, String> connection = redisConnect.getConnection()) {
			RedisCommands<String, String> sync = connection.sync();

			// 0. 초기화
			sync.flushdb();

			runBasicPipelineExample(connection);
		}
	}

	private static void runBasicPipelineExample(StatefulRedisConnection<String, String> connection) throws Exception {
		System.out.println("=========기본 파이프라이닝=========");

		// 파이프라이닝은 async 커맨드를 통해 사용합니다.
		RedisAsyncCommands<String, String> commands = connection.async();

		// 자동 전송을 끄면 이후 발행된 명령어들이 즉시 전송되지 않고 로컬 버퍼에 쌓입니다.
		connection.setAutoFlushCommands(false);

		// 명령어 발행 (아직 서버에 전송되지 않은 상태)
		List<RedisFuture<?>> futures = new ArrayList<>();
		futures.add(commands.set("pipe:name", "백과"));
		futures.add(commands.set("pipe:city", "서울"));
		futures.add(commands.get("pipe:name"));
		futures.add(commands.get("pipe:city"));
		futures.add(commands.incr("pipe:count"));
		futures.add(commands.incr("pipe:count"));
		futures.add(commands.incr("pipe:count"));

		// 버퍼에 쌓인 모든 명령어를 서버에 한 번에 전송
		connection.flushCommands();

		// 모든 Future 가 완료될 때까지 대기 (기본 타임아웃: 1분)
		LettuceFutures.awaitAll(60, java.util.concurrent.TimeUnit.SECONDS,
			futures.toArray(new RedisFuture[0]));

		// 각 Future 에서 결과 추출
		// set → String "OK", get → String, incr → Long
		System.out.println("파이프라인 결과 목록 :");
		for (int i = 0; i < futures.size(); i++) {
			System.out.println("  [" + i + "] " + futures.get(i).get());
		}

		// 파이프라이닝 완료 후 autoFlush 를 반드시 true 로 복원합니다.
		// 복원하지 않으면 이후 sync 명령어도 전송되지 않는 버그가 발생합니다.
		connection.setAutoFlushCommands(true);
	}
}
