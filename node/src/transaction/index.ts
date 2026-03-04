import {
  createRedisClient,
  disconnectRedisClient,
} from "../connection";

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
// ioredis 에서의 트랜잭션
// =====================================================================================================================
// client.multi() 로 Pipeline(ChainableCommander)을 만들고 명령어를 체이닝한 뒤 exec() 합니다.
//
// [차이점] Java Lettuce:       command.multi() 후 동일 커맨드 객체로 명령 → TransactionResult 반환
//          Spring Data Redis:  SessionCallback 내부에서 operations.multi() → List<Object> 반환
//          ioredis:            client.multi() → Pipeline 반환, pipeline.exec() → [error, value][][] 반환
//            → 각 명령어 결과가 [error, value] 튜플로 반환됩니다.
//            → WATCH 충돌로 트랜잭션이 취소되면 exec() 가 null 을 반환합니다.
// =====================================================================================================================

async function runMultiExecExample(): Promise<void> {
  const client = createRedisClient();

  try {
    // 0. 초기화
    await client.flushdb();

    // =====================================================================================================================
    // MULTI / EXEC 기본 트랜잭션
    // =====================================================================================================================
    // 1. client.multi() 호출 → Pipeline(트랜잭션 큐) 반환
    // 2. 이후 pipeline 에 명령어를 체이닝 → 즉시 실행 X, 큐에 쌓임
    // 3. pipeline.exec() 호출 → 큐의 명령어 일괄 실행
    //    반환값: [error, value][] 배열 (각 명령어 결과)
    // =====================================================================================================================
    console.log("=========MULTI / EXEC 기본 트랜잭션=========");

    const pipeline = client.multi();
    pipeline.set("tx:name", "백과");
    pipeline.set("tx:count", "0");
    pipeline.incr("tx:count");
    pipeline.incr("tx:count");

    // results: [error, value][] 배열
    // [[null, "OK"], [null, "OK"], [null, 1], [null, 2]]
    const results = await pipeline.exec();

    console.log("트랜잭션 결과 목록 :");
    results?.forEach(([err, value], index) => {
      console.log(`  [${index}]`, err ? `에러: ${err.message}` : value);
    });

    const txName = await client.get("tx:name");
    const txCount = await client.get("tx:count");
    console.log(`tx:name = ${txName}, tx:count = ${txCount}`);
  } finally {
    await disconnectRedisClient(client);
  }
}

async function runWatchExample(): Promise<void> {
  // =====================================================================================================================
  // WATCH + MULTI/EXEC 낙관적 락 (Optimistic Lock)
  // =====================================================================================================================
  // WATCH 는 지정한 키를 감시합니다.
  // WATCH 이후 ~ EXEC 이전에 해당 키가 외부에서 변경되면 → exec() 가 null 반환 (트랜잭션 취소)
  // WATCH 이후 ~ EXEC 이전에 변경이 없다면             → 정상 실행
  //
  // [차이점] Java Lettuce:       command.watch("key") → 동일 커맨드 객체 사용
  //          Spring Data Redis:  operations.watch("key") → SessionCallback 내부에서 사용
  //          ioredis:            client.watch("key") → Promise<"OK"> 반환
  //            → WATCH 후 client.multi() 로 트랜잭션 시작
  //            → 외부 변경 시뮬레이션을 위해 별도 클라이언트(client2) 사용
  // =====================================================================================================================
  console.log("=========WATCH + MULTI/EXEC 낙관적 락=========");

  const client = createRedisClient();

  try {
    await client.set("watch:stock", "10");
    console.log("초기 재고 :", await client.get("watch:stock"));

    // --- 케이스 1: WATCH 이후 외부 변경 없음 → 트랜잭션 성공 ---
    console.log("--- 케이스 1: 외부 변경 없음 → 트랜잭션 성공 ---");

    await client.watch("watch:stock"); // WATCH: 키 감시 시작
    const pipeline1 = client.multi();
    pipeline1.decr("watch:stock"); // 재고 1 차감
    const successResult = await pipeline1.exec(); // null 이 아니면 성공

    if (successResult !== null) {
      console.log("케이스 1 → 트랜잭션 성공! 결과 :", successResult[0][1]);
      console.log("남은 재고 :", await client.get("watch:stock"));
    } else {
      console.log("케이스 1 → 트랜잭션 취소 (충돌 감지)");
    }

    // --- 케이스 2: WATCH 이후 외부에서 값 변경 → 트랜잭션 취소 ---
    console.log("--- 케이스 2: 외부 변경 발생 → 트랜잭션 취소 ---");

    // 외부 변경 시뮬레이션을 위해 별도 클라이언트 사용
    const client2 = createRedisClient();

    try {
      await client.watch("watch:stock"); // WATCH: 키 감시 시작

      // 다른 클라이언트가 watch:stock 을 변경 (외부 변경 시뮬레이션)
      await client2.set("watch:stock", "999");

      const pipeline2 = client.multi();
      pipeline2.decr("watch:stock");
      const failResult = await pipeline2.exec(); // WATCH 감지 → null 반환 (트랜잭션 취소)

      if (failResult === null) {
        console.log("케이스 2 → 트랜잭션 취소! (외부에서 watch:stock 변경 감지)");
        console.log(
          "현재 재고 (외부 변경값 그대로 유지) :",
          await client.get("watch:stock"),
        );
        console.log(
          "→ 재시도 로직을 추가하여 충돌 시 다시 시도하는 패턴으로 활용합니다.",
        );
      } else {
        console.log("케이스 2 → 트랜잭션 성공 (예상치 못한 결과) :", failResult);
      }
    } finally {
      await disconnectRedisClient(client2);
    }
  } finally {
    await disconnectRedisClient(client);
  }
}

async function main(): Promise<void> {
  await runMultiExecExample();
  await runWatchExample();
}

main().catch(console.error);
