package com.baekgwa.example;

import java.util.List;

import com.baekgwa.connect.RedisConnect;

import io.lettuce.core.Range;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * PackageName : com.baekgwa.example
 * FileName    : ZSetExample
 * Author      : Baekgwa
 * Date        : 26. 3. 1.
 * Description :
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2026-03-01     Baekgwa               Initial creation
 **/

// =====================================================================================================================
// Redis Sorted Set (ZSet) 이란?
// =====================================================================================================================
// 각 멤버에 score(실수) 를 부여하여 score 기준으로 자동 정렬되는 집합
// 동일 score 이면 멤버 이름의 사전순(lexicographic) 으로 정렬
// 내부 구조: skiplist + hashtable 조합
//   - skiplist: score 순 범위 조회 O(log N)
//   - hashtable: 특정 멤버 score 조회 O(1)
//
// =====================================================================================================================
// 핵심 특징
// =====================================================================================================================
//  - 멤버 중복 불가 (Set 특성), score 중복은 허용
//  - score 는 double 타입 (정수도 내부적으로 double 로 저장)
//  - 순위(rank) 는 0-based: 0이 가장 낮은 score
//
// =====================================================================================================================
// 대표 활용 사례
// =====================================================================================================================
//  1. 게임 리더보드 — score = 점수, zrevrange 로 상위 N명 조회
//  2. 우선순위 큐  — score = 우선순위, zpopmin 으로 가장 낮은(긴급한) 것부터 처리
//  3. 최근 활동 피드 — score = Unix timestamp, 최근 N개만 유지
//  4. 범위 검색    — 특정 점수대 사용자만 필터링 (zrangebyscore)

public class ZSetExample {

	public static void start() {
		RedisConnect redisConnect = new RedisConnect();

		try (StatefulRedisConnection<String, String> connection = redisConnect.getConnection()) {
			RedisCommands<String, String> command = connection.sync();

			// 0. 안의 내용들 모두 초기화.
			command.flushdb();

			// =========================================================
			// 기본 명령어
			// =========================================================

			// 1. zadd
			// zadd 명령어는 score 와 함께 멤버를 추가합니다.
			// 반환값은 새로 추가된 멤버 수 (이미 있는 멤버는 score 만 업데이트, 반환값 미포함)
			// zadd key score member [score member ...]
			System.out.println("=========zadd=========");
			Long added = command.zadd("scores", 1500.0, "Alice");
			System.out.println("추가된 멤버 수 : " + added);                // 1
			// ScoredValue 로 여러 명 한번에 추가
			Long addedMany = command.zadd("scores",
					ScoredValue.just(3200.0, "Bob"),
					ScoredValue.just(2800.0, "Charlie"),
					ScoredValue.just(4100.0, "Dave"),
					ScoredValue.just(2800.0, "Eve"),    // Charlie 와 동일 score
					ScoredValue.just(900.0,  "Frank")
			);
			System.out.println("추가된 멤버 수 : " + addedMany);            // 5

			// 이미 있는 멤버 score 업데이트 → 반환값 0
			Long updated = command.zadd("scores", 5000.0, "Alice");
			System.out.println("기존 멤버 score 업데이트 시 반환값 : " + updated); // 0

			// 2. zscore
			// zscore 명령어는 멤버의 score 를 조회합니다.
			// 존재하지 않는 멤버면 null 반환
			System.out.println("=========zscore=========");
			Double aliceScore = command.zscore("scores", "Alice");
			System.out.println("Alice score : " + aliceScore);             // 5000.0
			Double ghostScore = command.zscore("scores", "Ghost");
			System.out.println("없는 멤버 score : " + ghostScore);         // null

			// 3. zrank / zrevrank
			// zrank : score 오름차순 기준 순위 (0-based, 낮은 score = 0위)
			// zrevrank: score 내림차순 기준 순위 (0-based, 높은 score = 0위)
			// 현재 score 정렬: Frank(900) < Charlie(2800) = Eve(2800) < Bob(3200) < Dave(4100) < Alice(5000)
			System.out.println("=========zrank / zrevrank=========");
			Long aliceRank    = command.zrank("scores", "Alice");
			Long aliceRevRank = command.zrevrank("scores", "Alice");
			System.out.println("Alice 오름차순 순위 : " + aliceRank);      // 5 (가장 높은 score 라 마지막)
			System.out.println("Alice 내림차순 순위 : " + aliceRevRank);   // 0 (1등)
			Long frankRank    = command.zrank("scores", "Frank");
			System.out.println("Frank 오름차순 순위 : " + frankRank);      // 0 (가장 낮은 score)

			// 4. zrange / zrevrange (with scores)
			// zrange   : 오름차순으로 index 범위 내 멤버 반환
			// zrevrange: 내림차순으로 index 범위 내 멤버 반환
			// WithScores 를 붙이면 ScoredValue<String> 으로 score 도 함께 반환
			System.out.println("=========zrange / zrevrange=========");
			List<String> ascending = command.zrange("scores", 0, -1);
			System.out.println("오름차순 전체 : " + ascending);

			List<ScoredValue<String>> topThree = command.zrevrangeWithScores("scores", 0, 2);
			System.out.println("상위 3명 (내림차순, score 포함):");
			for (int i = 0; i < topThree.size(); i++) {
				ScoredValue<String> sv = topThree.get(i);
				System.out.printf("  %d위: %s (%.0f점)%n", i + 1, sv.getValue(), sv.getScore());
			}

			// 5. zrangebyscore
			// score 범위로 멤버를 오름차순 조회합니다.
			// Range.create(min, max): 양 끝 포함
			// 양 끝을 제외하려면 Range.from(Boundary.excluding(min), Boundary.excluding(max))
			System.out.println("=========zrangebyscore=========");
			// 2000 이상 4000 이하인 멤버
			List<String> midRange = command.zrangebyscore("scores", Range.create(2000, 4000));
			System.out.println("2000~4000 점 멤버 : " + midRange);

			List<ScoredValue<String>> midWithScore = command.zrangebyscoreWithScores("scores", Range.create(2000, 4000));
			System.out.println("2000~4000 점 멤버 (score 포함):");
			midWithScore.forEach(sv ->
					System.out.printf("  %s: %.0f점%n", sv.getValue(), sv.getScore()));

			// 6. zcard / zcount
			// zcard : 전체 멤버 수
			// zcount: 특정 score 범위 내 멤버 수
			System.out.println("=========zcard / zcount=========");
			Long totalCount = command.zcard("scores");
			System.out.println("전체 멤버 수 : " + totalCount);             // 6
			Long countInRange = command.zcount("scores", Range.create(2800, 5000));
			System.out.println("2800~5000 점 멤버 수 : " + countInRange);  // 5

			// 7. zincrby
			// zincrby 명령어는 멤버의 score 를 지정한 값만큼 증가시킵니다.
			// 반환값은 증가 후의 새 score
			// 감소는 음수를 넘기면 됩니다.
			System.out.println("=========zincrby=========");
			Double newBobScore = command.zincrby("scores", 500.0, "Bob");
			System.out.println("Bob 500점 추가 후 score : " + newBobScore); // 3700.0
			Double newFrankScore = command.zincrby("scores", -200.0, "Frank");
			System.out.println("Frank 200점 차감 후 score : " + newFrankScore); // 700.0

			// 8. zrem
			// zrem 명령어는 멤버를 제거합니다.
			// 반환값은 실제로 제거된 멤버 수
			System.out.println("=========zrem=========");
			Long removed = command.zrem("scores", "Frank");
			System.out.println("Frank 제거, 제거된 수 : " + removed);       // 1
			Long notExist = command.zrem("scores", "Ghost");
			System.out.println("없는 멤버 제거 시도 : " + notExist);        // 0

			// 9. zpopmin / zpopmax
			// zpopmin: 가장 낮은 score 의 멤버를 꺼내 제거
			// zpopmax: 가장 높은 score 의 멤버를 꺼내 제거
			// count 지정 시 여러 개를 한번에 꺼낼 수 있습니다.
			System.out.println("=========zpopmin / zpopmax=========");
			ScoredValue<String> lowestPopped = command.zpopmin("scores");
			System.out.printf("가장 낮은 score 꺼내기: %s (%.0f점)%n",
					lowestPopped.getValue(), lowestPopped.getScore());

			List<ScoredValue<String>> top2Popped = command.zpopmax("scores", 2);
			System.out.println("가장 높은 score 2개 꺼내기:");
			top2Popped.forEach(sv ->
					System.out.printf("  %s: %.0f점%n", sv.getValue(), sv.getScore()));

			// =========================================================
			// 실전 예제 1: 게임 리더보드
			// - 점수 등록/갱신, 상위 랭킹 조회, 특정 유저 순위 확인
			// =========================================================
			System.out.println("\n=========실전 예제 1: 게임 리더보드=========");
			command.del("leaderboard");

			// 유저 점수 등록
			command.zadd("leaderboard",
					ScoredValue.just(8500, "PlayerA"),
					ScoredValue.just(7200, "PlayerB"),
					ScoredValue.just(9100, "PlayerC"),
					ScoredValue.just(6800, "PlayerD"),
					ScoredValue.just(9100, "PlayerE"),  // PlayerC 와 동점 → 사전순
					ScoredValue.just(5500, "PlayerF")
			);

			// 플레이 중 점수 갱신 (zadd 는 기존 score 를 덮어씀)
			command.zadd("leaderboard", 9500, "PlayerB");
			System.out.println("PlayerB 점수 갱신 완료");

			// 상위 3명 조회 (내림차순)
			List<ScoredValue<String>> top3 = command.zrevrangeWithScores("leaderboard", 0, 2);
			System.out.println("[TOP 3]");
			for (int i = 0; i < top3.size(); i++) {
				ScoredValue<String> sv = top3.get(i);
				System.out.printf("  %d위: %s (%,.0f점)%n", i + 1, sv.getValue(), sv.getScore());
			}

			// 특정 유저 순위 및 점수 확인
			String targetPlayer = "PlayerC";
			Long rank  = command.zrevrank("leaderboard", targetPlayer);
			Double score = command.zscore("leaderboard", targetPlayer);
			System.out.printf("%s → %d위 / %.0f점%n", targetPlayer, rank + 1, score);

			// 특정 점수 구간 인원 수 (예: 7000점 이상)
			Long qualifiedCount = command.zcount("leaderboard", Range.create(7000, Double.MAX_VALUE));
			System.out.println("7000점 이상 유저 수 : " + qualifiedCount);

			// =========================================================
			// 실전 예제 2: 우선순위 큐
			// - score 가 낮을수록 우선순위가 높음 (긴급도)
			// - zpopmin 으로 항상 가장 긴급한 작업부터 처리
			// =========================================================
			System.out.println("\n=========실전 예제 2: 우선순위 큐=========");
			command.del("task:queue");

			// score 1 = 긴급, score 5 = 낮은 우선순위
			command.zadd("task:queue",
					ScoredValue.just(3, "이메일 발송"),
					ScoredValue.just(1, "결제 처리"),       // 가장 긴급
					ScoredValue.just(5, "리포트 생성"),
					ScoredValue.just(1, "재고 부족 알림"),  // 결제 처리와 동일 → 사전순
					ScoredValue.just(2, "주문 확인")
			);

			System.out.println("작업 처리 순서 (긴급도 높은 것부터):");
			ScoredValue<String> task;
			while ((task = command.zpopmin("task:queue")) != null && task.hasValue()) {
				System.out.printf("  [우선순위 %.0f] %s 처리 완료%n", task.getScore(), task.getValue());
			}
		}
	}
}
