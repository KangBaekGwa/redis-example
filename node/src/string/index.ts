import { createRedisClient, disconnectRedisClient } from "../connection";

// ioredis는 기본이 비동기(Promise) 모델입니다.
// 모든 Redis 명령어가 Promise를 반환하며, async/await으로 동기적 흐름처럼 작성합니다.
// Java Lettuce의 connection.async()와 유사하지만, ioredis에서는 이게 기본.
async function main(): Promise<void> {
  const client = createRedisClient();

  try {
    // 0. 안의 내용들 모두 초기화.
    await client.flushdb();

    // 1. set
    // set 명령어는 항상 성공합니다.
    // 없는 키에 set 을 한다면? 새로운 키를 만들과 값을 등록
    // 있는 키에 set 을 한다면? 있는 키에 값을 덮어쓰기
    console.log("=========set=========");
    const isSuccess: string = await client.set("name", "백과");
    console.log("저장 성공? :", isSuccess);
    const isSuccess2: string = await client.set("name", "백과");
    console.log("2번째 저장 성공? :", isSuccess2);

    // 2. get
    // get 명령어 또한 항상 성공합니다. (exception 없음)
    // 만약 있는 key 로 조회한다면 값 반환
    // 없는 key 로 조회한다면 null 반환
    console.log("=========get=========");
    const savedName: string | null = await client.get("name");
    console.log("내 이름은 :", savedName);
    const notExistKey: string | null = await client.get("notExistKey");
    console.log("없는 키는 :", notExistKey);

    // 3. incr
    // incr 명령어는 값을 1 증가시킵니다.
    // 반환값은 명령어를 수행한 후 저장된 값을 반환합니다.
    // 키가 없으면 0 으로 초기화 후 1 반환
    //
    // [차이점 없음] Java / Spring / ioredis 모두 number(Long) 반환
    console.log("=========incr=========");
    const viewCount: number = await client.incr("viewCount");
    console.log("조회수 :", viewCount);

    // 문자열은 incr() 할 수 없어, Exception 발생
    // ReplyError: ERR value is not an integer or out of range
    // await client.set("stringCount", "stringCount");
    // const stringCount = await client.incr("stringCount");

    // 4. decr
    // decr 명령어는 값을 1 감소시킵니다.
    // 반환값은 명령어를 수행한 후 저장된 값을 반환합니다.
    // 키가 없으면 0 으로 초기화 후 -1 반환
    //
    // [차이점 없음] Java / Spring / ioredis 모두 number(Long) 반환
    console.log("=========decr=========");
    const decreasedCount: number = await client.decr("viewCount");
    console.log("감소된 조회수 :", decreasedCount);

    // 5. incrby
    // incrby 명령어는 값을 특정 수만큼 증가시킵니다.
    // incr 은 무조건 1 증가, incrby 는 원하는 수만큼 증가 가능
    //
    // [차이점 없음] Java / Spring / ioredis 모두 number(Long) 반환
    console.log("=========incrby=========");
    const incrByCount: number = await client.incrby("viewCount", 10);
    console.log("10 증가된 조회수 :", incrByCount);

    // 6. decrby
    // decrby 명령어는 값을 특정 수만큼 감소시킵니다.
    // decr 은 무조건 1 감소, decrby 는 원하는 수만큼 감소 가능
    //
    // [차이점 없음] Java / Spring / ioredis 모두 number(Long) 반환
    console.log("=========decrby=========");
    const decrByCount: number = await client.decrby("viewCount", 5);
    console.log("5 감소된 조회수 :", decrByCount);

    // 7. mset
    // mset 명령어는 여러 키-값 쌍을 한번에 저장합니다.
    // 원자적으로 처리되며, 일부만 실패하는 경우는 없습니다.
    //
    // [차이점] Java Lettuce:       Map<String, String> 전달 → String "OK" 반환
    //          Spring Data Redis:  Map<String, String> 전달 → void 반환
    //          ioredis:            key-value 를 펼쳐서(spread) 전달 → String "OK" 반환
    //            → ioredis는 Map 객체를 직접 받지 않고 [key, val, key, val ...] 형태로 전달합니다.
    //            → Record<string, string>을 Object.entries()로 flatten 후 spread 합니다.
    console.log("=========mset=========");
    const multiMap: Record<string, string> = {
      city: "서울",
      country: "대한민국",
      language: "한국어",
    };
    const msetResult: string = await client.mset(
      ...Object.entries(multiMap).flat(),
    );
    console.log("다중 저장 성공? :", msetResult);

    // 8. mget
    // mget 명령어는 여러 키의 값을 한번에 조회합니다.
    // 없는 키는 null 로 반환됩니다.
    // 입력한 키 순서대로 결과가 반환됩니다.
    //
    // [차이점] Java Lettuce:       List<KeyValue<String, String>> 반환 (키 정보 포함)
    //          Spring Data Redis:  List<String | null> 반환 (키 정보 없음)
    //          ioredis:            (string | null)[] 반환 (키 정보 없음)
    //            → Spring과 동일하게 값만 배열로 반환됩니다.
    console.log("=========mget=========");
    const keys: string[] = ["city", "country", "language", "notExistKey"];
    const mgetResult: (string | null)[] = await client.mget(...keys);
    mgetResult.forEach((value, index) => {
      console.log(`${keys[index]} :`, value);
    });

    // 9. setex (set with expire)
    // setex 명령어는 값을 저장하면서 만료 시간(초)을 함께 설정합니다.
    // 만료 시간이 지나면 해당 키는 자동으로 삭제됩니다.
    // 캐시처럼 일정 시간만 유지해야 할 데이터에 유용합니다.
    //
    // [차이점] Java Lettuce:       command.setex("key", 60, "value") → String "OK" 반환
    //          Spring Data Redis:  ops.set("key", "value", 60, TimeUnit.SECONDS) → void
    //          ioredis:            client.setex("key", 60, "value")  → String "OK" 반환
    //            → Java Lettuce와 메서드명, 인자 순서 모두 동일합니다.
    //            → Spring과 달리 별도의 setex() 메서드가 존재합니다.
    console.log("=========setex=========");
    const setexResult: string = await client.setex(
      "tempKey",
      60,
      "임시 데이터",
    );
    console.log("만료 시간 포함 저장 성공? :", setexResult);

    // 10. ttl
    // ttl 명령어는 키의 남은 만료 시간(초)을 반환합니다.
    // 만료 시간이 설정되지 않은 키 → -1 반환
    // 존재하지 않는 키              → -2 반환
    //
    // [차이점 없음] Java / Spring / ioredis 모두 number(Long) 반환, 규칙 동일
    console.log("=========ttl=========");
    const ttlTemp: number = await client.ttl("tempKey");
    console.log("tempKey 의 남은 만료 시간 :", ttlTemp, "초");
    const ttlNoExpire: number = await client.ttl("name");
    console.log("만료 시간 없는 키의 ttl :", ttlNoExpire);
    const ttlNotExist: number = await client.ttl("notExistKey");
    console.log("없는 키의 ttl :", ttlNotExist);

    // 11. setnx (set if not exists)
    // setnx 명령어는 키가 존재하지 않을 때만 저장합니다.
    // 이미 키가 존재한다면 → 저장하지 않고 0 반환
    // 키가 없다면         → 저장하고 1 반환
    // 분산 락(Distributed Lock) 구현에 많이 활용됩니다.
    //
    // [차이점] Java Lettuce:       Boolean 반환 (true / false)
    //          Spring Data Redis:  Boolean 반환 (true / false)
    //          ioredis:            number 반환 (1 / 0)
    //            → Redis 프로토콜의 integer reply를 그대로 반환합니다.
    //            → 사용 시 === 1 또는 === 0 으로 비교해야 합니다.
    console.log("=========setnx=========");
    const setnxFirst: number = await client.setnx("setnxKey", "최초 저장");
    console.log("최초 저장 성공? :", setnxFirst === 1);
    const setnxSecond: number = await client.setnx(
      "setnxKey",
      "두번째 저장 시도",
    );
    console.log("두번째 저장 성공? :", setnxSecond === 1);
    const setnxValue: string | null = await client.get("setnxKey");
    console.log("setnxKey 의 값 :", setnxValue);
  } finally {
    await disconnectRedisClient(client);
  }
}

main().catch(console.error);
