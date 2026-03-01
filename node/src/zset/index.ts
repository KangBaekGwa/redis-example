import { createRedisClient, disconnectRedisClient } from "../connection";

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

async function main(): Promise<void> {
  const client = createRedisClient();

  try {
    // 0. 안의 내용들 모두 초기화.
    await client.flushdb();

    // =========================================================
    // 기본 명령어
    // =========================================================

    // 1. zadd
    // zadd 명령어는 score 와 함께 멤버를 추가합니다.
    // 반환값은 새로 추가된 멤버 수 (이미 있는 멤버는 score 만 업데이트, 반환값 미포함)
    //
    // [차이점] Java Lettuce: command.zadd(key, score, member) → Long
    //          ioredis:       client.zadd(key, score, member)  → number (동일)
    //          여러 멤버 추가 시:
    //            Java: ScoredValue.just(score, member) 목록을 varargs 로 전달
    //            ioredis: zadd(key, score1, member1, score2, member2, ...) 형태로 평탄화해서 전달
    console.log("=========zadd=========");
    const added: number = await client.zadd("scores", 1500, "Alice");
    console.log("추가된 멤버 수 :", added);                         // 1

    const addedMany: number = await client.zadd(
      "scores",
      3200, "Bob",
      2800, "Charlie",
      4100, "Dave",
      2800, "Eve",      // Charlie 와 동일 score
       900, "Frank",
    );
    console.log("추가된 멤버 수 :", addedMany);                     // 5

    // 이미 있는 멤버 score 업데이트 → 반환값 0
    const updated: number = await client.zadd("scores", 5000, "Alice");
    console.log("기존 멤버 score 업데이트 시 반환값 :", updated);   // 0

    // 2. zscore
    // zscore 명령어는 멤버의 score 를 조회합니다.
    // 존재하지 않는 멤버면 null 반환
    //
    // [차이점] Java Lettuce: Double | null 반환
    //          ioredis:       string | null 반환 (부동소수점 정밀도 보장을 위해 문자열로 반환)
    //            → 숫자로 사용하려면 parseFloat() 변환이 필요합니다.
    console.log("=========zscore=========");
    const aliceScore: string | null = await client.zscore("scores", "Alice");
    console.log("Alice score :", parseFloat(aliceScore!));           // 5000
    const ghostScore: string | null = await client.zscore("scores", "Ghost");
    console.log("없는 멤버 score :", ghostScore);                   // null

    // 3. zrank / zrevrank
    // zrank : score 오름차순 기준 순위 (0-based, 낮은 score = 0위)
    // zrevrank: score 내림차순 기준 순위 (0-based, 높은 score = 0위)
    //
    // [차이점 없음] Java / ioredis 모두 number(Long) | null 반환
    console.log("=========zrank / zrevrank=========");
    const aliceRank: number | null    = await client.zrank("scores", "Alice");
    const aliceRevRank: number | null = await client.zrevrank("scores", "Alice");
    console.log("Alice 오름차순 순위 :", aliceRank);                // 5 (가장 높은 score 라 마지막)
    console.log("Alice 내림차순 순위 :", aliceRevRank);             // 0 (1등)
    const frankRank: number | null = await client.zrank("scores", "Frank");
    console.log("Frank 오름차순 순위 :", frankRank);                // 0 (가장 낮은 score)

    // 4. zrange / zrevrange (with scores)
    // zrange   : 오름차순으로 index 범위 내 멤버 반환
    // zrevrange: 내림차순으로 index 범위 내 멤버 반환
    //
    // [차이점] Java Lettuce: zrangeWithScores → List<ScoredValue<String>> 반환
    //          ioredis:       zrange + 'WITHSCORES' 옵션 → string[] 반환
    //            → WITHSCORES 옵션을 붙이면 [member1, score1, member2, score2, ...] 교대 배열로 반환됩니다.
    //            → ScoredValue 래퍼 없이 배열로 반환되므로 짝수 인덱스=멤버, 홀수 인덱스=score 로 직접 파싱해야 합니다.
    console.log("=========zrange / zrevrange=========");
    const ascending: string[] = await client.zrange("scores", 0, -1);
    console.log("오름차순 전체 :", ascending);

    const topThreeRaw: string[] = await client.zrevrange("scores", 0, 2, "WITHSCORES");
    console.log("상위 3명 (내림차순, score 포함):");
    for (let i = 0; i < topThreeRaw.length; i += 2) {
      const member = topThreeRaw[i];
      const score  = parseFloat(topThreeRaw[i + 1]);
      console.log(`  ${i / 2 + 1}위: ${member} (${score}점)`);
    }

    // 5. zrangebyscore
    // score 범위로 멤버를 오름차순 조회합니다.
    //
    // [차이점] Java Lettuce: Range.create(min, max) 객체를 사용
    //          ioredis:       min, max 를 number 또는 string 으로 직접 전달
    //            → '+inf', '-inf' 로 무한대를 표현합니다.
    //            → Range 객체 없이 직접 숫자를 넘깁니다.
    console.log("=========zrangebyscore=========");
    // 2000 이상 4000 이하인 멤버
    const midRange: string[] = await client.zrangebyscore("scores", 2000, 4000);
    console.log("2000~4000 점 멤버 :", midRange);

    const midWithScoreRaw: string[] = await client.zrangebyscore(
      "scores", 2000, 4000, "WITHSCORES",
    );
    console.log("2000~4000 점 멤버 (score 포함):");
    for (let i = 0; i < midWithScoreRaw.length; i += 2) {
      console.log(`  ${midWithScoreRaw[i]}: ${parseFloat(midWithScoreRaw[i + 1])}점`);
    }

    // 6. zcard / zcount
    // zcard : 전체 멤버 수
    // zcount: 특정 score 범위 내 멤버 수
    //
    // [차이점 없음] Java / ioredis 모두 number(Long) 반환
    console.log("=========zcard / zcount=========");
    const totalCount: number = await client.zcard("scores");
    console.log("전체 멤버 수 :", totalCount);                      // 6
    const countInRange: number = await client.zcount("scores", 2800, 5000);
    console.log("2800~5000 점 멤버 수 :", countInRange);            // 5

    // 7. zincrby
    // zincrby 명령어는 멤버의 score 를 지정한 값만큼 증가시킵니다.
    // 반환값은 증가 후의 새 score. 감소는 음수를 넘기면 됩니다.
    //
    // [차이점] Java Lettuce: Double 반환
    //          ioredis:       string 반환 (부동소수점 정밀도 보장을 위해 문자열로 반환)
    //            → 숫자로 사용하려면 parseFloat() 변환이 필요합니다.
    console.log("=========zincrby=========");
    const newBobScore: string = await client.zincrby("scores", 500, "Bob");
    console.log("Bob 500점 추가 후 score :", parseFloat(newBobScore));   // 3700
    const newFrankScore: string = await client.zincrby("scores", -200, "Frank");
    console.log("Frank 200점 차감 후 score :", parseFloat(newFrankScore)); // 700

    // 8. zrem
    // zrem 명령어는 멤버를 제거합니다.
    // 반환값은 실제로 제거된 멤버 수
    //
    // [차이점 없음] Java / ioredis 모두 number(Long) 반환
    console.log("=========zrem=========");
    const removed: number = await client.zrem("scores", "Frank");
    console.log("Frank 제거, 제거된 수 :", removed);                // 1
    const notExist: number = await client.zrem("scores", "Ghost");
    console.log("없는 멤버 제거 시도 :", notExist);                 // 0

    // 9. zpopmin / zpopmax
    // zpopmin: 가장 낮은 score 의 멤버를 꺼내 제거
    // zpopmax: 가장 높은 score 의 멤버를 꺼내 제거
    //
    // [차이점] Java Lettuce: ScoredValue<String> 또는 List<ScoredValue<String>> 반환
    //          ioredis:       string[] 반환 (member, score 교대 배열)
    //            → zpopmin(key)         → [member, score] (2개짜리 배열)
    //            → zpopmin(key, count)  → [member1, score1, member2, score2, ...] (교대 배열)
    //            → ScoredValue 래퍼 없이 배열로 반환되므로 직접 인덱스로 접근해야 합니다.
    console.log("=========zpopmin / zpopmax=========");
    const lowestPopped: string[] = await client.zpopmin("scores");
    console.log(`가장 낮은 score 꺼내기: ${lowestPopped[0]} (${parseFloat(lowestPopped[1])}점)`);

    const top2Popped: string[] = await client.zpopmax("scores", 2);
    console.log("가장 높은 score 2개 꺼내기:");
    for (let i = 0; i < top2Popped.length; i += 2) {
      console.log(`  ${top2Popped[i]}: ${parseFloat(top2Popped[i + 1])}점`);
    }

    // =========================================================
    // 실전 예제 1: 게임 리더보드
    // - 점수 등록/갱신, 상위 랭킹 조회, 특정 유저 순위 확인
    // =========================================================
    console.log("\n=========실전 예제 1: 게임 리더보드=========");
    await client.del("leaderboard");

    // 유저 점수 등록
    await client.zadd(
      "leaderboard",
      8500, "PlayerA",
      7200, "PlayerB",
      9100, "PlayerC",
      6800, "PlayerD",
      9100, "PlayerE",   // PlayerC 와 동점 → 사전순
      5500, "PlayerF",
    );

    // 플레이 중 점수 갱신 (zadd 는 기존 score 를 덮어씀)
    await client.zadd("leaderboard", 9500, "PlayerB");
    console.log("PlayerB 점수 갱신 완료");

    // 상위 3명 조회 (내림차순)
    const top3Raw: string[] = await client.zrevrange("leaderboard", 0, 2, "WITHSCORES");
    console.log("[TOP 3]");
    for (let i = 0; i < top3Raw.length; i += 2) {
      const score = parseFloat(top3Raw[i + 1]);
      console.log(`  ${i / 2 + 1}위: ${top3Raw[i]} (${score.toLocaleString()}점)`);
    }

    // 특정 유저 순위 및 점수 확인
    const targetPlayer = "PlayerC";
    const rank: number | null         = await client.zrevrank("leaderboard", targetPlayer);
    const scoreStr: string | null     = await client.zscore("leaderboard", targetPlayer);
    console.log(`${targetPlayer} → ${rank! + 1}위 / ${parseFloat(scoreStr!)}점`);

    // 특정 점수 구간 인원 수 (예: 7000점 이상)
    const qualifiedCount: number = await client.zcount("leaderboard", 7000, "+inf");
    console.log("7000점 이상 유저 수 :", qualifiedCount);

    // =========================================================
    // 실전 예제 2: 우선순위 큐
    // - score 가 낮을수록 우선순위가 높음 (긴급도)
    // - zpopmin 으로 항상 가장 긴급한 작업부터 처리
    // =========================================================
    console.log("\n=========실전 예제 2: 우선순위 큐=========");
    await client.del("task:queue");

    // score 1 = 긴급, score 5 = 낮은 우선순위
    await client.zadd(
      "task:queue",
      3, "이메일 발송",
      1, "결제 처리",        // 가장 긴급
      5, "리포트 생성",
      1, "재고 부족 알림",   // 결제 처리와 동일 → 사전순
      2, "주문 확인",
    );

    console.log("작업 처리 순서 (긴급도 높은 것부터):");
    let taskRaw: string[];
    while ((taskRaw = await client.zpopmin("task:queue")).length > 0) {
      console.log(`  [우선순위 ${parseFloat(taskRaw[1])}] ${taskRaw[0]} 처리 완료`);
    }
  } finally {
    await disconnectRedisClient(client);
  }
}

main().catch(console.error);
