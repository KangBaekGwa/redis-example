import { createRedisClient, disconnectRedisClient } from "../connection";

// ioredis는 기본이 비동기(Promise) 모델입니다.
// Redis List 는 Doubly Linked List 구조
// 양 끝(Left/Right) 에서 O(1) 으로 삽입/삭제 가능
// 인덱스 기반 접근은 O(N)
// Queue(큐): rpush → lpop  (오른쪽에 넣고, 왼쪽에서 꺼내기)
// Stack(스택): lpush → lpop  (왼쪽에 넣고, 왼쪽에서 꺼내기)
async function main(): Promise<void> {
  const client = createRedisClient();

  try {
    // 0. 안의 내용들 모두 초기화.
    await client.flushdb();

    // =========================================================
    // Queue 처럼 사용하기 (FIFO: First In, First Out)
    // rpush → lpop
    // =========================================================

    // 1. rpush
    // rpush 명령어는 리스트의 오른쪽(tail) 에 값을 추가합니다.
    // 반환값은 명령어를 수행한 후 리스트의 길이입니다.
    // 키가 없으면 새로운 리스트를 만들어 추가합니다.
    // 여러 값을 한번에 추가할 수 있으며, 왼쪽에서 오른쪽 순서로 쌓입니다.
    //
    // [차이점 없음] Java / ioredis 모두 number(Long) 반환
    console.log("=========rpush=========");
    const rpushCount1: number = await client.rpush("queue", "task1");
    console.log("rpush 후 리스트 길이 :", rpushCount1);
    const rpushCount2: number = await client.rpush("queue", "task2", "task3");
    console.log("rpush 2개 후 리스트 길이 :", rpushCount2);
    // 현재 상태: [task1, task2, task3]

    // 2. lrange
    // lrange 명령어는 리스트의 특정 범위를 조회합니다.
    // lrange key start stop
    // 인덱스는 0부터 시작하며, -1 은 마지막 요소를 의미합니다.
    // -1 을 사용하면 끝까지 조회합니다.
    //
    // [차이점 없음] Java / ioredis 모두 string[] 반환
    console.log("=========lrange=========");
    const queueList: string[] = await client.lrange("queue", 0, -1);
    console.log("전체 조회 :", queueList);
    const partialList: string[] = await client.lrange("queue", 0, 1);
    console.log("0~1 범위 조회 :", partialList);

    // 3. lpop (Queue 의 dequeue)
    // lpop 명령어는 리스트의 왼쪽(head) 에서 값을 꺼냅니다.
    // rpush 로 오른쪽에 넣고, lpop 으로 왼쪽에서 꺼내면 → FIFO 구조
    // 반환값은 꺼낸 값이며, 리스트가 비어있으면 null 반환
    //
    // [차이점 없음] Java / ioredis 모두 string | null 반환
    console.log("=========lpop (Queue dequeue)=========");
    const firstTask: string | null = await client.lpop("queue");
    console.log("꺼낸 작업 :", firstTask); // task1
    console.log("남은 큐 :", await client.lrange("queue", 0, -1));
    // 현재 상태: [task2, task3]

    // =========================================================
    // Stack 처럼 사용하기 (LIFO: Last In, First Out)
    // lpush → lpop
    // =========================================================

    // 4. lpush
    // lpush 명령어는 리스트의 왼쪽(head) 에 값을 추가합니다.
    // 반환값은 명령어를 수행한 후 리스트의 길이입니다.
    // 여러 값을 한번에 추가할 수 있으며, 마지막 인자가 가장 왼쪽에 위치합니다.
    // lpush key v1 v2 v3 → 결과: [v3, v2, v1]
    //
    // [차이점 없음] Java / ioredis 모두 number(Long) 반환
    console.log("=========lpush=========");
    await client.del("stack");
    const lpushCount1: number = await client.lpush("stack", "page1");
    console.log("lpush 후 리스트 길이 :", lpushCount1);
    const lpushCount2: number = await client.lpush("stack", "page2", "page3");
    console.log("lpush 2개 후 리스트 길이 :", lpushCount2);
    console.log("스택 상태 :", await client.lrange("stack", 0, -1));
    // 현재 상태: [page3, page2, page1]

    // 5. lpop (Stack 의 pop)
    // lpush 로 왼쪽에 넣고, lpop 으로 왼쪽에서 꺼내면 → LIFO 구조
    console.log("=========lpop (Stack pop)=========");
    const topPage: string | null = await client.lpop("stack");
    console.log("꺼낸 페이지 (가장 최근) :", topPage); // page3
    console.log("남은 스택 :", await client.lrange("stack", 0, -1));

    // 6. rpop
    // rpop 명령어는 리스트의 오른쪽(tail) 에서 값을 꺼냅니다.
    // 리스트가 비어있으면 null 반환
    //
    // [차이점 없음] Java / ioredis 모두 string | null 반환
    console.log("=========rpop=========");
    await client.rpush("mylist", "a", "b", "c");
    const lastItem: string | null = await client.rpop("mylist");
    console.log("오른쪽에서 꺼낸 값 :", lastItem); // c
    console.log("남은 리스트 :", await client.lrange("mylist", 0, -1));

    // 7. llen
    // llen 명령어는 리스트의 길이를 반환합니다.
    // 키가 없으면 0 반환
    //
    // [차이점 없음] Java / ioredis 모두 number(Long) 반환
    console.log("=========llen=========");
    const listLen: number = await client.llen("mylist");
    console.log("mylist 길이 :", listLen);
    const notExistLen: number = await client.llen("notExistKey");
    console.log("없는 키의 길이 :", notExistLen);

    // 8. lindex
    // lindex 명령어는 특정 인덱스의 값을 조회합니다.
    // 인덱스는 0부터 시작하며, -1 은 마지막 요소
    // 범위를 벗어나면 null 반환
    //
    // [차이점 없음] Java / ioredis 모두 string | null 반환
    console.log("=========lindex=========");
    // 현재 mylist: [a, b]
    const firstItem: string | null = await client.lindex("mylist", 0);
    console.log("0번 인덱스 :", firstItem); // a
    const lastIndex: string | null = await client.lindex("mylist", -1);
    console.log("-1번 인덱스(마지막) :", lastIndex); // b
    const outOfRange: string | null = await client.lindex("mylist", 99);
    console.log("범위 초과 인덱스 :", outOfRange); // null

    // 9. linsert
    // linsert 명령어는 특정 값의 앞(BEFORE) 또는 뒤(AFTER) 에 새 값을 삽입합니다.
    // 반환값은 삽입 후 리스트 길이
    // 기준 값이 없으면 -1 반환
    //
    // [차이점] Java Lettuce:  linsert(key, true, pivot, value) → boolean true = BEFORE
    //          ioredis:       linsert(key, "BEFORE" | "AFTER", pivot, value)
    //            → ioredis 는 boolean 대신 문자열 "BEFORE" / "AFTER" 를 사용합니다.
    console.log("=========linsert=========");
    // 현재 mylist: [a, b]
    const afterInsert: number = await client.linsert(
      "mylist",
      "BEFORE",
      "b",
      "b_before",
    );
    console.log("b 앞에 b_before 삽입 후 길이 :", afterInsert);
    console.log("삽입 후 리스트 :", await client.lrange("mylist", 0, -1));
    // 결과: [a, b_before, b]

    // 10. lrem
    // lrem 명령어는 리스트에서 특정 값을 count 만큼 제거합니다.
    // count > 0: head 에서 tail 방향으로 count 개 제거
    // count < 0: tail 에서 head 방향으로 |count| 개 제거
    // count = 0: 해당 값을 모두 제거
    // 반환값은 실제로 제거된 개수
    //
    // [차이점 없음] Java / ioredis 모두 number(Long) 반환
    console.log("=========lrem=========");
    await client.del("remlist");
    await client.rpush("remlist", "x", "y", "x", "z", "x");
    // 현재: [x, y, x, z, x]
    const removed: number = await client.lrem("remlist", 2, "x");
    console.log("'x' 2개 제거 완료, 제거된 개수 :", removed);
    console.log("제거 후 리스트 :", await client.lrange("remlist", 0, -1));
    // 결과: [y, z, x] (앞에서 2개 제거)

    // =========================================================
    // Blocking POP
    // 리스트가 비어있을 때 지정한 시간(초) 만큼 대기하다가
    // 값이 들어오면 즉시 꺼내는 명령어
    // timeout = 0 이면 무한 대기
    // 실시간 작업 큐, 이벤트 드리븐 처리에 활용
    // 주의: Blocking 명령어는 해당 Connection 을 점유하므로
    //       별도 Connection 을 사용해야 합니다.
    //
    // [차이점] Java Lettuce:  blpop(timeout, key) → KeyValue<String, String> 반환
    //                           → .getKey(), .getValue() 로 접근
    //          ioredis:       blpop(key, timeout) → [string, string] | null 반환
    //                           → [0] 이 키, [1] 이 값 (배열로 반환)
    //            → 인자 순서 주의: Java 는 timeout 먼저, ioredis 는 key 먼저입니다.
    // =========================================================

    // 11. blpop - 데이터가 있을 때 (즉시 반환)
    // blpop 명령어는 리스트 왼쪽에서 Blocking 방식으로 값을 꺼냅니다.
    // 데이터가 이미 있으면 즉시 반환
    console.log("=========blpop (데이터 있을 때)=========");
    await client.del("blqueue");
    await client.rpush("blqueue", "job1", "job2");
    const blpopResult: [string, string] | null = await client.blpop(
      "blqueue",
      3,
    );
    if (blpopResult) {
      console.log(
        "blpop 결과 - 키 :",
        blpopResult[0],
        ", 값 :",
        blpopResult[1],
      );
    }

    // 12. blpop - 빈 리스트에서 대기 후 반환
    // 별도 클라이언트에서 1초 후 데이터를 push → blpop 이 감지하여 꺼냄
    //
    // 주의: ioredis 에서도 blocking 명령어는 해당 커넥션을 점유합니다.
    //       별도의 Redis 클라이언트를 생성해서 push 해야 합니다.
    console.log("=========blpop (빈 리스트 대기)=========");
    await client.del("blqueue");

    // 생산자: 1초 후 별도 클라이언트로 데이터 삽입
    const producerClient = createRedisClient();
    const producerPromise = (async () => {
      await new Promise((resolve) => setTimeout(resolve, 1000));
      await producerClient.rpush("blqueue", "delayed_job");
      console.log("[생산자] delayed_job 삽입 완료");
      await disconnectRedisClient(producerClient);
    })();

    // 소비자: 최대 5초 대기
    console.log("[소비자] blqueue 에서 데이터 대기 중... (최대 5초)");
    const delayedResult: [string, string] | null = await client.blpop(
      "blqueue",
      5,
    );
    if (delayedResult) {
      console.log(
        "[소비자] blpop 수신 - 키 :",
        delayedResult[0],
        ", 값 :",
        delayedResult[1],
      );
    } else {
      console.log("[소비자] timeout - 데이터 없음");
    }

    await producerPromise;

    // 13. blpop - timeout 초과 시 null 반환
    console.log("=========blpop (timeout 초과)=========");
    await client.del("emptyQueue");
    console.log("[소비자] emptyQueue 에서 대기 중... (최대 2초)");
    const timeoutResult: [string, string] | null = await client.blpop(
      "emptyQueue",
      2,
    );
    if (timeoutResult) {
      console.log("수신 :", timeoutResult[1]);
    } else {
      console.log("timeout 발생 → null 반환");
    }

    // 14. brpop - 오른쪽에서 Blocking POP
    // blpop 과 동일하게 동작하지만, 오른쪽(tail) 에서 꺼냅니다.
    //
    // [차이점] Java Lettuce:  KeyValue<String, String> 반환
    //          ioredis:       [string, string] | null 반환
    console.log("=========brpop=========");
    await client.del("brqueue");
    await client.rpush("brqueue", "item1", "item2", "item3");
    const brpopResult: [string, string] | null = await client.brpop(
      "brqueue",
      3,
    );
    if (brpopResult) {
      console.log(
        "brpop 결과 - 키 :",
        brpopResult[0],
        ", 값 :",
        brpopResult[1],
      );
    }
    // item3 (오른쪽에서 꺼냄)
  } finally {
    await disconnectRedisClient(client);
  }
}

main().catch(console.error);
