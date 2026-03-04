package com.baekgwa.example;

import com.baekgwa.connect.RedisConnect;

import io.lettuce.core.TransactionResult;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * PackageName : com.baekgwa.example
 * FileName    : TransactionExample
 * Author      : Baekgwa
 * Date        : 26. 3. 4.
 * Description :
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2026-03-04     Baekgwa               Initial creation
 **/

// =====================================================================================================================
// Redis 트랜잭션이란?
// =====================================================================================================================
// Redis 트랜잭션은 MULTI / EXEC 명령어를 통해 여러 명령어를 하나의 원자적 단위로 실행합니다.
//
// MULTI  : 트랜잭션 시작. 이후 명령어는 즉시 실행되지 않고 큐에 쌓입니다.
// EXEC   : 큐에 쌓인 명령어를 일괄 실행합니다.
// DISCARD: 큐에 쌓인 명령어를 모두 취소합니다.
// WATCH  : 지정한 키를 감시합니다. EXEC 전에 해당 키가 변경되면 트랜잭션을 취소합니다. (낙관적 락)
//
// =====================================================================================================================
// 주의: Redis 트랜잭션은 RDB 트랜잭션과 다릅니다.
// - 명령어 문법 오류 → EXEC 시 전체 취소
// - 런타임 오류 (예: 잘못된 타입) → 해당 명령어만 실패, 나머지는 정상 실행
// - 즉, 부분 실패가 가능하므로 롤백 개념이 없습니다.
// =====================================================================================================================
//
// =====================================================================================================================
// Lettuce 에서의 트랜잭션
// =====================================================================================================================
// Lettuce sync 커맨드에서 multi() / exec() 를 직접 호출합니다.
// exec() 의 반환값은 TransactionResult 이며, WATCH 충돌로 트랜잭션이 취소되면 null 을 반환합니다.
// =====================================================================================================================
public class TransactionExample {

	public static void start() {
		RedisConnect redisConnect = new RedisConnect();

		try (StatefulRedisConnection<String, String> connection = redisConnect.getConnection()) {
			RedisCommands<String, String> command = connection.sync();

			// 0. 초기화
			command.flushdb();

			runMultiExecExample(command);
			runWatchExample(command);
		}
	}

	private static void runMultiExecExample(RedisCommands<String, String> command) {
		System.out.println("=========MULTI / EXEC 기본 트랜잭션=========");

		command.multi(); // MULTI: 트랜잭션 시작

		// 이 사이의 명령어들은 큐에 쌓입니다. (즉시 실행 X)
		command.set("tx:name", "백과");
		command.set("tx:count", "0");
		command.incr("tx:count");
		command.incr("tx:count");

		TransactionResult results = command.exec(); // EXEC: 실제 명령어 일괄 실행

		// results 에는 각 명령어의 실행 결과가 순서대로 담겨있습니다.
		// [set → "OK", set → "OK", incr → 1L, incr → 2L]
		System.out.println("트랜잭션 결과 목록 :");
		for (int i = 0; i < results.size(); i++) {
			System.out.println("  [" + i + "] " + results.get(i));
		}

		System.out.println("tx:name = " + command.get("tx:name") + ", tx:count = " + command.get("tx:count"));
	}

	private static void runWatchExample(RedisCommands<String, String> command) {
		System.out.println("=========WATCH + MULTI/EXEC 낙관적 락=========");

		command.set("watch:stock", "10");
		System.out.println("초기 재고 : " + command.get("watch:stock"));

		// --- 케이스 1: WATCH 이후 외부 변경 없음 → 트랜잭션 성공 ---
		System.out.println("--- 케이스 1: 외부 변경 없음 → 트랜잭션 성공 ---");

		command.watch("watch:stock"); // WATCH: 키 감시 시작
		command.multi();
		command.decr("watch:stock"); // 재고 1 차감
		TransactionResult successResult = command.exec(); // null 이 아니면 성공

		if (successResult != null) {
			System.out.println("케이스 1 → 트랜잭션 성공! 결과 : " + successResult.get(0));
			System.out.println("남은 재고 : " + command.get("watch:stock"));
		} else {
			System.out.println("케이스 1 → 트랜잭션 취소 (충돌 감지)");
		}

		// --- 케이스 2: WATCH 이후 외부에서 값 변경 → 트랜잭션 취소 ---
		System.out.println("--- 케이스 2: 외부 변경 발생 → 트랜잭션 취소 ---");

		command.watch("watch:stock"); // WATCH: 키 감시 시작

		// 외부 변경 시뮬레이션: watch 중인 키를 다른 커넥션에서 변경한 것처럼 동일 커넥션으로 직접 수정
		// 실제 환경에서는 다른 클라이언트가 변경하는 상황입니다.
		// 주의: Lettuce 에서 동일 커넥션으로 MULTI 밖에서 명령어를 실행하면 WATCH 감지가 트리거됩니다.
		command.set("watch:stock", "999"); // 외부 변경 시뮬레이션

		command.multi();
		command.decr("watch:stock");
		TransactionResult failResult = command.exec(); // WATCH 감지 → null 반환 (트랜잭션 취소)

		if (failResult == null) {
			System.out.println("케이스 2 → 트랜잭션 취소! (외부에서 watch:stock 변경 감지)");
			System.out.println("현재 재고 (외부 변경값 그대로 유지) : " + command.get("watch:stock"));
			System.out.println("→ 재시도 로직을 추가하여 충돌 시 다시 시도하는 패턴으로 활용합니다.");
		} else {
			System.out.println("케이스 2 → 트랜잭션 성공 (예상치 못한 결과) : " + failResult);
		}
	}
}
