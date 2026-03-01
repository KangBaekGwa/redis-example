import { createRedisClient, disconnectRedisClient } from "../connection";

// =====================================================================================================================
// Redis Hash 란?
// =====================================================================================================================
// Hash 는 하나의 키 아래 "field → value" 쌍을 여러 개 저장하는 구조입니다.
// 예: user:1001 → { name: "Alice", age: "30", city: "Seoul" }
//
// Hash 가 유리한 상황: 일부 필드만 업데이트/조회, 필드별 숫자 연산, 메모리 최적화
// String(JSON) 이 유리한 상황: 전체를 통째로 읽고 쓸 때, 필드별 TTL 필요 시, 중첩 객체
async function main(): Promise<void> {
  const client = createRedisClient();

  try {
    // 0. 안의 내용들 모두 초기화.
    await client.flushdb();

    // 1. hset (단일 필드)
    // hset 명령어는 Hash 에 field-value 쌍을 저장합니다.
    // 신규 필드 추가 시 1, 기존 필드 업데이트 시 0 반환
    //
    // [차이점] Java Lettuce: Boolean 반환 (true=신규, false=업데이트)
    //          ioredis:       number 반환 (1=신규, 0=업데이트)
    //            → ioredis 는 boolean 이 아닌 정수 0/1 로 반환합니다.
    console.log("=========hset (단일 필드)=========");
    const isNew: number = await client.hset("user:1001", "name", "Alice");
    console.log("신규 필드 추가 :", isNew === 1);            // true
    const isUpdate: number = await client.hset(
      "user:1001",
      "name",
      "Alice_Updated",
    );
    console.log("기존 필드 수정 :", isUpdate === 1);         // false (0)

    // 2. hset (다중 필드)
    // Map 을 넘겨서 여러 필드를 한번에 저장합니다.
    // 반환값은 새로 추가된 필드 수 (업데이트된 필드는 미포함)
    //
    // [차이점] Java Lettuce: command.hset(key, Map<String, String>) → Long
    //          ioredis:       client.hset(key, object | Map)         → number
    //            → ioredis 는 plain object 또는 Map 을 인자로 받습니다.
    console.log("=========hset (다중 필드)=========");
    const addedFields: number = await client.hset("user:1001", {
      name: "Alice",
      age: "30",
      city: "Seoul",
      email: "alice@example.com",
      point: "500",
    });
    console.log("새로 추가된 필드 수 :", addedFields);

    // 3. hget
    // hget 명령어는 Hash 에서 특정 필드의 값을 조회합니다.
    // 필드가 없거나 키가 없으면 null 반환
    //
    // [차이점 없음] Java / ioredis 모두 string | null 반환
    console.log("=========hget=========");
    const name: string | null = await client.hget("user:1001", "name");
    console.log("name :", name);                              // Alice
    const notExistField: string | null = await client.hget("user:1001", "phone");
    console.log("없는 필드 :", notExistField);               // null

    // 4. hmget
    // hmget 명령어는 여러 필드를 한번에 조회합니다.
    // 없는 필드는 null 로 반환되며, 순서는 요청한 필드 순서와 동일합니다.
    //
    // [차이점] Java Lettuce: List<KeyValue<String, String>> 반환
    //                         → .hasValue() 로 존재 여부 확인
    //                         → .getValue() 로 값 접근 (없으면 NoSuchElementException)
    //                         → .getValueOrElse(null) 로 안전하게 접근
    //          ioredis:       (string | null)[] 반환 (단순 배열)
    //            → KeyValue 래퍼 없이 값 또는 null 을 직접 배열로 반환합니다.
    //            → null 체크로 존재 여부를 확인합니다.
    console.log("=========hmget=========");
    const fields: (string | null)[] = await client.hmget(
      "user:1001",
      "name",
      "email",
      "phone",
    );
    console.log("name :", fields[0]);                         // Alice
    console.log("email :", fields[1]);                        // alice@example.com
    console.log("phone hasValue :", fields[2] !== null);      // false
    console.log("phone (없음) :", fields[2]);                 // null

    // 5. hgetall
    // hgetall 명령어는 Hash 의 모든 field-value 쌍을 반환합니다.
    // 필드 수가 많을 경우 부하가 클 수 있으므로 대용량 Hash 에서는 주의
    //
    // [차이점] Java Lettuce: Map<String, String> 반환
    //          ioredis:       Record<string, string> 반환 (plain object)
    //            → 사용 방법은 동일합니다.
    console.log("=========hgetall=========");
    const userMap: Record<string, string> = await client.hgetall("user:1001");
    console.log("전체 Hash :", userMap);

    // 6. hkeys / hvals / hlen
    // hkeys: 모든 필드명 반환
    // hvals: 모든 값 반환
    // hlen: 필드 수 반환
    //
    // [차이점 없음] Java / ioredis 모두 동일한 반환 타입
    console.log("=========hkeys / hvals / hlen=========");
    const keys: string[] = await client.hkeys("user:1001");
    console.log("필드명 목록 :", keys);
    const vals: string[] = await client.hvals("user:1001");
    console.log("값 목록 :", vals);
    const fieldCount: number = await client.hlen("user:1001");
    console.log("필드 수 :", fieldCount);

    // 7. hexists
    // hexists 명령어는 Hash 에 특정 필드가 존재하는지 확인합니다.
    //
    // [차이점] Java Lettuce: Boolean 반환 (true / false)
    //          ioredis:       0 | 1 반환 (정수)
    //            → ioredis 는 boolean 이 아닌 정수 0(없음) / 1(있음) 으로 반환합니다.
    console.log("=========hexists=========");
    const hasEmail: number = await client.hexists("user:1001", "email");
    console.log("email 필드 존재? :", hasEmail === 1);        // true
    const hasPhone: number = await client.hexists("user:1001", "phone");
    console.log("phone 필드 존재? :", hasPhone === 1);        // false

    // 8. hdel
    // hdel 명령어는 Hash 에서 특정 필드를 삭제합니다.
    // 반환값은 실제로 삭제된 필드 수
    // 없는 필드를 삭제하려 해도 에러 없이 0 반환
    //
    // [차이점 없음] Java / ioredis 모두 number(Long) 반환
    console.log("=========hdel=========");
    const deletedCount: number = await client.hdel("user:1001", "city");
    console.log("삭제된 필드 수 :", deletedCount);            // 1
    const notExistDel: number = await client.hdel("user:1001", "phone");
    console.log("없는 필드 삭제 시도 :", notExistDel);        // 0
    console.log("삭제 후 필드 수 :", await client.hlen("user:1001"));

    // 9. hsetnx
    // hsetnx 명령어는 필드가 존재하지 않을 때만 값을 설정합니다.
    // 설정 성공(신규) 시 1, 이미 존재하면 0 반환 (덮어쓰지 않음)
    //
    // [차이점] Java Lettuce: Boolean 반환 (true=성공, false=이미 존재)
    //          ioredis:       0 | 1 반환 (정수)
    //            → ioredis 는 boolean 이 아닌 정수 0(실패) / 1(성공) 으로 반환합니다.
    console.log("=========hsetnx=========");
    const setNew: number = await client.hsetnx("user:1001", "nickname", "alice99");
    console.log("nickname 신규 설정 :", setNew === 1);        // true
    const setExist: number = await client.hsetnx("user:1001", "name", "Bob");
    console.log("name 이미 존재 (덮어쓰기 X) :", setExist === 1); // false
    console.log("name 은 그대로 :", await client.hget("user:1001", "name")); // Alice

    // 10. hincrby
    // hincrby 명령어는 Hash 의 필드 값을 정수만큼 증가시킵니다.
    // 감소는 음수 값을 넘기면 됩니다.
    // 필드가 없으면 0 으로 초기화 후 증가
    //
    // [차이점 없음] Java / ioredis 모두 number(Long) 반환
    console.log("=========hincrby=========");
    const newPoint: number = await client.hincrby("user:1001", "point", 100);
    console.log("포인트 100 적립 후 :", newPoint);            // 600
    const afterDecrease: number = await client.hincrby(
      "user:1001",
      "point",
      -200,
    );
    console.log("포인트 200 차감 후 :", afterDecrease);       // 400

    // 11. hincrbyfloat
    // hincrbyfloat 명령어는 Hash 의 필드 값을 실수만큼 증가시킵니다.
    // 평점 누적, 소수점 카운터 등에 활용
    //
    // [차이점] Java Lettuce: Double 반환
    //          ioredis:       string 반환 (부동소수점 정밀도 보장을 위해 문자열로 반환)
    //            → 숫자로 사용하려면 parseFloat() 변환이 필요합니다.
    console.log("=========hincrbyfloat=========");
    await client.hset("product:55", { rating: "4.0" });
    const newRating: string = await client.hincrbyfloat(
      "product:55",
      "rating",
      0.5,
    );
    console.log("평점 0.5 추가 후 :", parseFloat(newRating)); // 4.5
    const afterMore: string = await client.hincrbyfloat(
      "product:55",
      "rating",
      -1.0,
    );
    console.log("평점 1.0 감소 후 :", parseFloat(afterMore)); // 3.5

    // =========================================================
    // 실전 활용 예시: Hash vs String(JSON) 비교
    // =========================================================

    // [Case 1] 포인트만 업데이트 — Hash 가 압도적으로 유리
    // String(JSON) 방식: GET → 역직렬화 → 수정 → 직렬화 → SET (5단계)
    // Hash 방식: hincrby 한 줄로 원자적 처리
    console.log("=========실전 예시: 포인트 업데이트=========");
    await client.hincrby("user:1001", "point", 100);
    console.log(
      "포인트 업데이트 완료 :",
      await client.hget("user:1001", "point"),
    );

    // [Case 2] 이름과 이메일만 조회 — Hash 가 유리
    // String(JSON) 방식: 전체 JSON 을 GET 해서 필요한 필드만 추출
    // Hash 방식: 필요한 필드만 정확히 조회
    console.log("=========실전 예시: 필요한 필드만 조회=========");
    const profile: (string | null)[] = await client.hmget(
      "user:1001",
      "name",
      "email",
    );
    console.log(`이름: ${profile[0]}, 이메일: ${profile[1]}`);

    // [Case 3] TTL 설정 — 키 단위로만 가능 (Hash 의 한계)
    // Hash 는 개별 필드에 TTL 을 설정할 수 없습니다.
    // user:1001 키 전체에만 만료 시간을 부여할 수 있습니다.
    //
    // [차이점 없음] Java / ioredis 모두 expire / ttl 명령어 동일하게 사용
    console.log("=========Hash TTL (키 단위만 가능)=========");
    await client.expire("user:1001", 3600);                   // 1시간 후 만료
    const ttl: number = await client.ttl("user:1001");
    console.log("user:1001 만료까지 남은 시간(초) :", ttl);
  } finally {
    await disconnectRedisClient(client);
  }
}

main().catch(console.error);
