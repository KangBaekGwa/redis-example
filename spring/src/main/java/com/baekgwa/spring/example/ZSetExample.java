package com.baekgwa.spring.example;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

/**
 * PackageName : com.baekgwa.spring.example
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
@Component
public class ZSetExample {

	private static final Logger log = LoggerFactory.getLogger(ZSetExample.class);

	private final RedisTemplate<String, String> redisTemplate;

	public ZSetExample(RedisTemplate<String, String> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public void run(String... args) {
		// ZSetOperations: Sorted Set 타입의 명령을 담당하는 객체
		// Java 순수 Lettuce 의 RedisCommands 에서 ZSet 관련 명령만 추출한 것과 유사합니다.
		ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

		// 0. 안의 내용들 모두 초기화.
		// Spring Data Redis 에는 flushDb() 를 직접 호출하는 메서드가 없습니다.
		// RedisCallback 을 통해 low-level connection 에 직접 접근하여 실행합니다.
		// Java 순수 Lettuce: command.flushdb()
		redisTemplate.execute((RedisCallback<Object>) connection -> {
			connection.serverCommands().flushAll();
			return null;
		});

		// =========================================================
		// 기본 명령어
		// =========================================================

		// 1. zadd
		// zadd 명령어는 score 와 함께 멤버를 추가합니다.
		// 반환값은 새로 추가된 멤버 수 (이미 있는 멤버는 score 만 업데이트)
		//
		// [차이점] Java 순수 Lettuce: command.zadd(key, score, member) → Long (신규 추가 수)
		//          Spring Data Redis: zSetOps.add(key, member, score)   → Boolean (신규면 true)
		//            → 메서드 파라미터 순서가 (key, member, score) 로 score 와 member 순서가 반대입니다.
		//            → 단일 추가 시 Long 이 아닌 Boolean 을 반환합니다.
		//          여러 멤버 추가:
		//            Java: ScoredValue.just(score, member) 를 varargs 로 전달 → Long 반환
		//            Spring: Set<TypedTuple<V>> 를 전달 → Long 반환
		//              → DefaultTypedTuple(value, score) 로 생성합니다.
		log.info("=========zadd=========");
		Boolean added = zSetOps.add("scores", "Alice", 1500.0);
		log.info("추가된 멤버 (신규 여부) : {}", added);                          // true

		Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
		tuples.add(new DefaultTypedTuple<>("Bob",     3200.0));
		tuples.add(new DefaultTypedTuple<>("Charlie", 2800.0));
		tuples.add(new DefaultTypedTuple<>("Dave",    4100.0));
		tuples.add(new DefaultTypedTuple<>("Eve",     2800.0));   // Charlie 와 동일 score
		tuples.add(new DefaultTypedTuple<>("Frank",    900.0));
		Long addedMany = zSetOps.add("scores", tuples);
		log.info("추가된 멤버 수 : {}", addedMany);                               // 5

		// 이미 있는 멤버 score 업데이트 → false 반환
		Boolean updated = zSetOps.add("scores", "Alice", 5000.0);
		log.info("기존 멤버 score 업데이트 시 반환값 : {}", updated);              // false

		// 2. zscore
		// zscore 명령어는 멤버의 score 를 조회합니다.
		// 존재하지 않는 멤버면 null 반환
		//
		// [차이점 없음] 두 방식 모두 Double | null 반환
		// Java 순수 Lettuce: command.zscore(key, member)
		// Spring Data Redis: zSetOps.score(key, member)
		//   → 메서드명이 zscore 에서 score 로 변경됩니다.
		log.info("=========zscore=========");
		Double aliceScore = zSetOps.score("scores", "Alice");
		log.info("Alice score : {}", aliceScore);                                   // 5000.0
		Double ghostScore = zSetOps.score("scores", "Ghost");
		log.info("없는 멤버 score : {}", ghostScore);                              // null

		// 3. zrank / zrevrank
		// zrank : score 오름차순 기준 순위 (0-based, 낮은 score = 0위)
		// zrevrank: score 내림차순 기준 순위 (0-based, 높은 score = 0위)
		//
		// [차이점 없음] 두 방식 모두 Long | null 반환
		// Java 순수 Lettuce: command.zrank(key, member) / command.zrevrank(key, member)
		// Spring Data Redis: zSetOps.rank(key, member)  / zSetOps.reverseRank(key, member)
		//   → 메서드명이 zrank 에서 rank, zrevrank 에서 reverseRank 로 변경됩니다.
		log.info("=========zrank / zrevrank=========");
		Long aliceRank    = zSetOps.rank("scores", "Alice");
		Long aliceRevRank = zSetOps.reverseRank("scores", "Alice");
		log.info("Alice 오름차순 순위 : {}", aliceRank);                           // 5 (가장 높은 score 라 마지막)
		log.info("Alice 내림차순 순위 : {}", aliceRevRank);                        // 0 (1등)
		Long frankRank = zSetOps.rank("scores", "Frank");
		log.info("Frank 오름차순 순위 : {}", frankRank);                           // 0 (가장 낮은 score)

		// 4. zrange / zrevrange (with scores)
		// zrange   : 오름차순으로 index 범위 내 멤버 반환
		// zrevrange: 내림차순으로 index 범위 내 멤버 반환
		//
		// [차이점] Java 순수 Lettuce: zrangeWithScores → List<ScoredValue<String>> 반환
		//          Spring Data Redis: rangeWithScores   → Set<TypedTuple<String>> 반환
		//            → List 가 아닌 Set 으로 반환됩니다. (실제로는 LinkedHashSet 으로 순서 유지됨)
		//            → ScoredValue 대신 TypedTuple 을 사용합니다.
		//            → .getValue() 로 멤버, .getScore() 로 score 에 접근합니다. (사용법 동일)
		//          메서드명 변경:
		//            zrange          → range
		//            zrevrange       → reverseRange
		//            zrangeWithScores → rangeWithScores
		//            zrevrangeWithScores → reverseRangeWithScores
		log.info("=========zrange / zrevrange=========");
		Set<String> ascending = zSetOps.range("scores", 0, -1);
		log.info("오름차순 전체 : {}", ascending);

		Set<ZSetOperations.TypedTuple<String>> topThree = zSetOps.reverseRangeWithScores("scores", 0, 2);
		log.info("상위 3명 (내림차순, score 포함):");
		int rankNum = 1;
		for (ZSetOperations.TypedTuple<String> tuple : topThree) {
			log.info("  {}위: {} ({}점)", rankNum++, tuple.getValue(), tuple.getScore().intValue());
		}

		// 5. zrangebyscore
		// score 범위로 멤버를 오름차순 조회합니다.
		//
		// [차이점] Java 순수 Lettuce: Range.create(min, max) 객체를 사용
		//          Spring Data Redis: rangeByScore(key, min, max) 에 double 직접 전달
		//            → Range 객체 없이 double 값으로 범위를 지정합니다.
		//          메서드명 변경:
		//            zrangebyscore            → rangeByScore
		//            zrangebyscoreWithScores  → rangeByScoreWithScores
		log.info("=========zrangebyscore=========");
		// 2000 이상 4000 이하인 멤버
		Set<String> midRange = zSetOps.rangeByScore("scores", 2000, 4000);
		log.info("2000~4000 점 멤버 : {}", midRange);

		Set<ZSetOperations.TypedTuple<String>> midWithScore = zSetOps.rangeByScoreWithScores("scores", 2000, 4000);
		log.info("2000~4000 점 멤버 (score 포함):");
		midWithScore.forEach(t -> log.info("  {}: {}점", t.getValue(), t.getScore().intValue()));

		// 6. zcard / zcount
		// zcard : 전체 멤버 수
		// zcount: 특정 score 범위 내 멤버 수
		//
		// [차이점] Java 순수 Lettuce: command.zcard(key)            → Long
		//                             command.zcount(key, Range)     → Long (Range 객체 사용)
		//          Spring Data Redis: zSetOps.size(key)              → Long
		//                             zSetOps.count(key, min, max)   → Long (double 직접 전달)
		//            → 메서드명이 zcard 에서 size, zcount 에서 count 로 변경됩니다.
		//            → count 는 Range 객체 없이 double min, max 를 직접 전달합니다.
		log.info("=========zcard / zcount=========");
		Long totalCount = zSetOps.size("scores");
		log.info("전체 멤버 수 : {}", totalCount);                                 // 6
		Long countInRange = zSetOps.count("scores", 2800, 5000);
		log.info("2800~5000 점 멤버 수 : {}", countInRange);                       // 5

		// 7. zincrby
		// zincrby 명령어는 멤버의 score 를 지정한 값만큼 증가시킵니다.
		// 반환값은 증가 후의 새 score. 감소는 음수를 넘기면 됩니다.
		//
		// [차이점 없음] 두 방식 모두 Double 반환
		// Java 순수 Lettuce: command.zincrby(key, amount, member)
		// Spring Data Redis: zSetOps.incrementScore(key, member, delta)
		//   → 메서드명이 zincrby 에서 incrementScore 로 변경됩니다.
		//   → 파라미터 순서가 (key, member, delta) 로 member 와 amount 순서가 다릅니다.
		log.info("=========zincrby=========");
		Double newBobScore = zSetOps.incrementScore("scores", "Bob", 500.0);
		log.info("Bob 500점 추가 후 score : {}", newBobScore);                     // 3700.0
		Double newFrankScore = zSetOps.incrementScore("scores", "Frank", -200.0);
		log.info("Frank 200점 차감 후 score : {}", newFrankScore);                 // 700.0

		// 8. zrem
		// zrem 명령어는 멤버를 제거합니다.
		// 반환값은 실제로 제거된 멤버 수
		//
		// [차이점] Java 순수 Lettuce: command.zrem(key, members...) → Long
		//          Spring Data Redis: zSetOps.remove(key, members...) → Long
		//            → 메서드명이 zrem 에서 remove 로 변경됩니다.
		log.info("=========zrem=========");
		Long removed = zSetOps.remove("scores", "Frank");
		log.info("Frank 제거, 제거된 수 : {}", removed);                           // 1
		Long notExist = zSetOps.remove("scores", "Ghost");
		log.info("없는 멤버 제거 시도 : {}", notExist);                            // 0

		// 9. zpopmin / zpopmax
		// zpopmin: 가장 낮은 score 의 멤버를 꺼내 제거
		// zpopmax: 가장 높은 score 의 멤버를 꺼내 제거
		//
		// [차이점] Java 순수 Lettuce: command.zpopmin(key)      → ScoredValue<String>
		//                             command.zpopmax(key, 2)   → List<ScoredValue<String>>
		//          Spring Data Redis: zSetOps.popMin(key)       → TypedTuple<String>
		//                             zSetOps.popMax(key, 2)    → Set<TypedTuple<String>>
		//            → 메서드명이 zpopmin 에서 popMin, zpopmax 에서 popMax 로 변경됩니다.
		//            → 다수를 꺼낼 때 List 가 아닌 Set 으로 반환됩니다.
		log.info("=========zpopmin / zpopmax=========");
		ZSetOperations.TypedTuple<String> lowestPopped = zSetOps.popMin("scores");
		log.info("가장 낮은 score 꺼내기: {} ({}점)",
				lowestPopped.getValue(), lowestPopped.getScore().intValue());

		Set<ZSetOperations.TypedTuple<String>> top2Popped = zSetOps.popMax("scores", 2);
		log.info("가장 높은 score 2개 꺼내기:");
		top2Popped.forEach(t -> log.info("  {}: {}점", t.getValue(), t.getScore().intValue()));

		// =========================================================
		// 실전 예제 1: 게임 리더보드
		// - 점수 등록/갱신, 상위 랭킹 조회, 특정 유저 순위 확인
		// =========================================================
		log.info("\n=========실전 예제 1: 게임 리더보드=========");
		redisTemplate.delete("leaderboard");

		// 유저 점수 등록
		Set<ZSetOperations.TypedTuple<String>> players = new HashSet<>();
		players.add(new DefaultTypedTuple<>("PlayerA", 8500.0));
		players.add(new DefaultTypedTuple<>("PlayerB", 7200.0));
		players.add(new DefaultTypedTuple<>("PlayerC", 9100.0));
		players.add(new DefaultTypedTuple<>("PlayerD", 6800.0));
		players.add(new DefaultTypedTuple<>("PlayerE", 9100.0));  // PlayerC 와 동점 → 사전순
		players.add(new DefaultTypedTuple<>("PlayerF", 5500.0));
		zSetOps.add("leaderboard", players);

		// 플레이 중 점수 갱신 (add 는 기존 score 를 덮어씀)
		zSetOps.add("leaderboard", "PlayerB", 9500.0);
		log.info("PlayerB 점수 갱신 완료");

		// 상위 3명 조회 (내림차순)
		Set<ZSetOperations.TypedTuple<String>> top3 = zSetOps.reverseRangeWithScores("leaderboard", 0, 2);
		log.info("[TOP 3]");
		int topRank = 1;
		for (ZSetOperations.TypedTuple<String> tuple : top3) {
			log.info("  {}위: {} ({}점)", topRank++, tuple.getValue(), tuple.getScore().intValue());
		}

		// 특정 유저 순위 및 점수 확인
		String targetPlayer = "PlayerC";
		Long playerRank  = zSetOps.reverseRank("leaderboard", targetPlayer);
		Double playerScore = zSetOps.score("leaderboard", targetPlayer);
		log.info("{} → {}위 / {}점", targetPlayer, playerRank + 1, playerScore.intValue());

		// 특정 점수 구간 인원 수 (예: 7000점 이상)
		Long qualifiedCount = zSetOps.count("leaderboard", 7000, Double.MAX_VALUE);
		log.info("7000점 이상 유저 수 : {}", qualifiedCount);

		// =========================================================
		// 실전 예제 2: 우선순위 큐
		// - score 가 낮을수록 우선순위가 높음 (긴급도)
		// - popMin 으로 항상 가장 긴급한 작업부터 처리
		// =========================================================
		log.info("\n=========실전 예제 2: 우선순위 큐=========");
		redisTemplate.delete("task:queue");

		// score 1 = 긴급, score 5 = 낮은 우선순위
		Set<ZSetOperations.TypedTuple<String>> taskSet = new HashSet<>();
		taskSet.add(new DefaultTypedTuple<>("이메일 발송",   3.0));
		taskSet.add(new DefaultTypedTuple<>("결제 처리",     1.0));   // 가장 긴급
		taskSet.add(new DefaultTypedTuple<>("리포트 생성",   5.0));
		taskSet.add(new DefaultTypedTuple<>("재고 부족 알림", 1.0));  // 결제 처리와 동일 → 사전순
		taskSet.add(new DefaultTypedTuple<>("주문 확인",     2.0));
		zSetOps.add("task:queue", taskSet);

		log.info("작업 처리 순서 (긴급도 높은 것부터):");
		ZSetOperations.TypedTuple<String> task;
		while ((task = zSetOps.popMin("task:queue")) != null) {
			log.info("  [우선순위 {}] {} 처리 완료", task.getScore().intValue(), task.getValue());
		}
	}
}
