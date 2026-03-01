import { createRedisClient, disconnectRedisClient } from "../connection";

// Redis Set 은 순서 없는 유일한 문자열의 집합
// 중복을 허용하지 않으며, 합집합/교집합/차집합 등 집합 연산을 O(N) 으로 지원
// 활용 사례: 좋아요 목록, 팔로워/팔로잉, 태그, 방문자 추적 등
async function main(): Promise<void> {
  const client = createRedisClient();

  try {
    // 0. 안의 내용들 모두 초기화.
    await client.flushdb();

    // 1. sadd
    // sadd 명령어는 Set 에 멤버를 추가합니다.
    // 반환값은 실제로 추가된 멤버의 수입니다. (이미 존재하면 추가 안 됨)
    // 중복 값은 무시됩니다.
    //
    // [차이점 없음] Java / ioredis 모두 number(Long) 반환
    console.log("=========sadd=========");
    const addedCount: number = await client.sadd(
      "fruits",
      "apple",
      "banana",
      "orange",
    );
    console.log("추가된 멤버 수 :", addedCount); // 3
    const duplicateCount: number = await client.sadd(
      "fruits",
      "apple",
      "grape",
    );
    console.log("중복 포함 추가 시 실제 추가 수 :", duplicateCount); // 1 (apple 은 중복)

    // 2. smembers
    // smembers 명령어는 Set 의 모든 멤버를 반환합니다.
    // 반환 순서는 보장되지 않습니다. (Set 특성)
    // 키가 없으면 빈 배열 반환
    //
    // [차이점] Java Lettuce: Set<String> 반환
    //          ioredis:       string[] 반환 (배열)
    //            → ioredis 는 Set 이 아닌 배열로 반환합니다. 순서 보장은 되지 않습니다.
    console.log("=========smembers=========");
    const allFruits: string[] = await client.smembers("fruits");
    console.log("모든 과일 :", allFruits);
    const notExist: string[] = await client.smembers("notExistKey");
    console.log("없는 키 smembers :", notExist); // []

    // 3. scard
    // scard 명령어는 Set 의 크기(멤버 수)를 반환합니다.
    // 키가 없으면 0 반환
    //
    // [차이점 없음] Java / ioredis 모두 number(Long) 반환
    console.log("=========scard=========");
    const fruitCount: number = await client.scard("fruits");
    console.log("과일 Set 크기 :", fruitCount); // 4
    const notExistCard: number = await client.scard("notExistKey");
    console.log("없는 키 scard :", notExistCard); // 0

    // 4. sismember
    // sismember 명령어는 특정 멤버가 Set 에 존재하는지 확인합니다.
    // 존재하면 true, 없으면 false 반환
    //
    // [차이점] Java Lettuce: Boolean 반환 (true / false)
    //          ioredis:       0 | 1 반환 (정수)
    //            → ioredis 는 boolean 이 아닌 정수 0(없음) / 1(있음) 으로 반환합니다.
    console.log("=========sismember=========");
    const hasApple: number = await client.sismember("fruits", "apple");
    console.log("apple 존재? :", hasApple === 1); // true
    const hasMango: number = await client.sismember("fruits", "mango");
    console.log("mango 존재? :", hasMango === 1); // false

    // 5. smismember
    // smismember 명령어는 여러 멤버의 존재 여부를 한번에 확인합니다.
    // 입력한 순서대로 결과를 반환합니다.
    //
    // [차이점] Java Lettuce: List<Boolean> 반환
    //          ioredis:       (0 | 1)[] 반환 (정수 배열)
    //            → ioredis 는 boolean[] 이 아닌 정수 배열 0/1 로 반환합니다.
    console.log("=========smismember=========");
    const memberChecks: number[] = await client.smismember(
      "fruits",
      "apple",
      "mango",
      "banana",
    );
    console.log("apple 존재? :", memberChecks[0] === 1); // true
    console.log("mango 존재? :", memberChecks[1] === 1); // false
    console.log("banana 존재? :", memberChecks[2] === 1); // true

    // 6. srem
    // srem 명령어는 Set 에서 특정 멤버를 제거합니다.
    // 반환값은 실제로 제거된 멤버의 수입니다.
    // 존재하지 않는 멤버를 제거하려 해도 에러 없이 0 반환
    //
    // [차이점 없음] Java / ioredis 모두 number(Long) 반환
    console.log("=========srem=========");
    const removedCount: number = await client.srem("fruits", "banana");
    console.log("제거된 멤버 수 :", removedCount); // 1
    const notExistRemove: number = await client.srem("fruits", "mango");
    console.log("없는 멤버 제거 시도 :", notExistRemove); // 0
    console.log("제거 후 Set :", await client.smembers("fruits"));

    // 7. spop
    // spop 명령어는 Set 에서 랜덤으로 멤버를 꺼내 제거합니다.
    // Set 이 비어있으면 null 반환
    // count 를 지정하면 여러 개를 한번에 꺼낼 수 있습니다.
    //
    // [차이점] Java Lettuce: spop(key)        → String 반환
    //                        spop(key, count)  → Set<String> 반환
    //          ioredis:       spop(key)        → string | null 반환
    //                         spop(key, count) → string[] 반환 (배열)
    //            → 복수 spop 시 ioredis 는 Set 이 아닌 배열로 반환합니다.
    console.log("=========spop=========");
    await client.sadd("colors", "red", "green", "blue", "yellow", "purple");
    const poppedColor: string | null = await client.spop("colors");
    console.log("랜덤으로 꺼낸 색상 :", poppedColor);
    console.log("spop 후 남은 Set :", await client.smembers("colors"));

    const multiPopped: string[] = await client.spop("colors", 2);
    console.log("한번에 2개 꺼내기 :", multiPopped);
    console.log("2개 spop 후 남은 Set :", await client.smembers("colors"));

    // 8. srandmember
    // srandmember 명령어는 Set 에서 랜덤으로 멤버를 조회합니다.
    // spop 과 달리 Set 에서 제거하지 않습니다.
    // count 양수: 중복 없이 count 개 반환
    // count 음수: 중복 허용하여 |count| 개 반환
    //
    // [차이점] Java Lettuce: srandmember(key)        → String 반환
    //                        srandmember(key, count)  → List<String> 반환
    //          ioredis:       srandmember(key)        → string | null 반환
    //                         srandmember(key, count) → string[] 반환
    //            → 차이점 없음. 배열 vs List 의 언어적 차이만 존재합니다.
    console.log("=========srandmember=========");
    await client.sadd("numbers", "1", "2", "3", "4", "5");
    const randomOne: string | null = await client.srandmember("numbers");
    console.log("랜덤 1개 조회 (제거 X) :", randomOne);
    const randomThree: string[] = await client.srandmember("numbers", 3);
    console.log("랜덤 3개 조회 (중복 없음) :", randomThree);
    const randomWithDup: string[] = await client.srandmember("numbers", -5);
    console.log("랜덤 5개 조회 (중복 허용) :", randomWithDup);
    console.log(
      "srandmember 후 Set 크기 변화 없음 :",
      await client.scard("numbers"),
    ); // 5

    // =========================================================
    // 집합 연산
    // =========================================================

    // 데이터 준비
    await client.sadd("team:backend", "Alice", "Bob", "Charlie", "Dave");
    await client.sadd("team:frontend", "Charlie", "Dave", "Eve", "Frank");

    // 9. sunion (합집합)
    // sunion 명령어는 여러 Set 의 합집합을 반환합니다.
    // 중복 멤버는 한번만 포함됩니다.
    //
    // [차이점] Java Lettuce: Set<String> 반환
    //          ioredis:       string[] 반환 (배열)
    //            → ioredis 는 Set 이 아닌 배열로 반환합니다.
    console.log("=========sunion (합집합)=========");
    const union: string[] = await client.sunion(
      "team:backend",
      "team:frontend",
    );
    console.log("백엔드 + 프론트엔드 전체 인원 :", union);

    // 10. sinter (교집합)
    // sinter 명령어는 여러 Set 의 교집합을 반환합니다.
    // 모든 Set 에 공통으로 존재하는 멤버만 반환합니다.
    //
    // [차이점] Java Lettuce: Set<String> 반환
    //          ioredis:       string[] 반환 (배열)
    console.log("=========sinter (교집합)=========");
    const inter: string[] = await client.sinter(
      "team:backend",
      "team:frontend",
    );
    console.log("백엔드 & 프론트엔드 동시 소속 :", inter); // Charlie, Dave

    // 11. sdiff (차집합)
    // sdiff 명령어는 첫 번째 Set 기준으로 나머지 Set 에 없는 멤버를 반환합니다.
    // sdiff A B → A 에 있고 B 에 없는 것
    //
    // [차이점] Java Lettuce: Set<String> 반환
    //          ioredis:       string[] 반환 (배열)
    console.log("=========sdiff (차집합)=========");
    const diffBackend: string[] = await client.sdiff(
      "team:backend",
      "team:frontend",
    );
    console.log("백엔드 전용 (프론트엔드에 없는) :", diffBackend); // Alice, Bob
    const diffFrontend: string[] = await client.sdiff(
      "team:frontend",
      "team:backend",
    );
    console.log("프론트엔드 전용 (백엔드에 없는) :", diffFrontend); // Eve, Frank

    // 12. sunionstore (합집합 결과 저장)
    // sunionstore 명령어는 합집합 결과를 새로운 키에 저장합니다.
    // 반환값은 저장된 Set 의 크기
    //
    // [차이점 없음] Java / ioredis 모두 number(Long) 반환
    console.log("=========sunionstore=========");
    const unionSize: number = await client.sunionstore(
      "team:all",
      "team:backend",
      "team:frontend",
    );
    console.log("합집합 저장 완료, 크기 :", unionSize);
    console.log("team:all :", await client.smembers("team:all"));

    // 13. sinterstore (교집합 결과 저장)
    // sinterstore 명령어는 교집합 결과를 새로운 키에 저장합니다.
    //
    // [차이점 없음] Java / ioredis 모두 number(Long) 반환
    console.log("=========sinterstore=========");
    const interSize: number = await client.sinterstore(
      "team:both",
      "team:backend",
      "team:frontend",
    );
    console.log("교집합 저장 완료, 크기 :", interSize);
    console.log("team:both :", await client.smembers("team:both"));

    // 14. sdiffstore (차집합 결과 저장)
    // sdiffstore 명령어는 차집합 결과를 새로운 키에 저장합니다.
    //
    // [차이점 없음] Java / ioredis 모두 number(Long) 반환
    console.log("=========sdiffstore=========");
    const diffSize: number = await client.sdiffstore(
      "team:backend:only",
      "team:backend",
      "team:frontend",
    );
    console.log("차집합 저장 완료, 크기 :", diffSize);
    console.log(
      "team:backend:only :",
      await client.smembers("team:backend:only"),
    );

    // 15. smove
    // smove 명령어는 한 Set 에서 다른 Set 으로 멤버를 이동합니다.
    // 원자적으로 처리됩니다. (source 에서 제거 + destination 에 추가)
    // 이동 성공 시 true, 멤버가 source 에 없으면 false 반환
    //
    // [차이점] Java Lettuce: Boolean 반환 (true / false)
    //          ioredis:       0 | 1 반환 (정수)
    //            → ioredis 는 boolean 이 아닌 정수 0(실패) / 1(성공) 으로 반환합니다.
    console.log("=========smove=========");
    // Alice 를 백엔드에서 프론트엔드로 이동
    const moved: number = await client.smove(
      "team:backend",
      "team:frontend",
      "Alice",
    );
    console.log("Alice 이동 성공? :", moved === 1); // true
    console.log("이동 후 백엔드 :", await client.smembers("team:backend"));
    console.log("이동 후 프론트엔드 :", await client.smembers("team:frontend"));

    const notMoved: number = await client.smove(
      "team:backend",
      "team:frontend",
      "notExistMember",
    );
    console.log("없는 멤버 이동 시도 :", notMoved === 1); // false
  } finally {
    await disconnectRedisClient(client);
  }
}

main().catch(console.error);
