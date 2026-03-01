package com.baekgwa.spring.example;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * PackageName : com.baekgwa.spring.example
 * FileName    : HashExample
 * Author      : Baekgwa
 * Date        : 26. 3. 1.
 * Description :
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2026-03-01     Baekgwa               Initial creation
 **/

// =====================================================================================================================
// Redis Hash 란?
// =====================================================================================================================
// Hash 는 하나의 키 아래 "field → value" 쌍을 여러 개 저장하는 구조입니다.
// 예: user:1001 → { name: "Alice", age: "30", city: "Seoul" }
//
// Hash 가 유리한 상황: 일부 필드만 업데이트/조회, 필드별 숫자 연산, 메모리 최적화
// String(JSON) 이 유리한 상황: 전체를 통째로 읽고 쓸 때, 필드별 TTL 필요 시, 중첩 객체
@Component
public class HashExample implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(HashExample.class);

	private final RedisTemplate<String, String> redisTemplate;

	public HashExample(RedisTemplate<String, String> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void run(String... args) {
		// HashOperations: Hash 타입의 key-value 명령을 담당하는 객체
		// Java 순수 Lettuce의 RedisCommands에서 Hash 관련 명령만 추출한 것과 유사합니다.
		HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

		// 0. 안의 내용들 모두 초기화.
		// Spring Data Redis에는 flushDb()를 직접 호출하는 메서드가 없습니다.
		// RedisCallback을 통해 low-level connection에 직접 접근하여 실행합니다.
		// Java 순수 Lettuce: command.flushdb()
		redisTemplate.execute((RedisCallback<Object>) connection -> {
			connection.serverCommands().flushAll();
			return null;
		});

		// 1. hset (단일 필드)
		// hset 명령어는 Hash 에 field-value 쌍을 저장합니다.
		// 신규 필드 추가 시 true, 기존 필드 업데이트 시 false 반환
		//
		// [차이점] Java 순수 Lettuce: command.hset(key, field, value) → Boolean 반환 (true=신규, false=업데이트)
		//          Spring Data Redis: hashOps.put(key, field, value)   → void (반환값 없음)
		//            → 메서드명이 hset 에서 put 으로 변경됩니다.
		//            → Spring 은 신규/업데이트 여부를 반환하지 않습니다.
		log.info("=========hset (단일 필드)=========");
		hashOps.put("user:1001", "name", "Alice");
		log.info("신규 필드 추가 완료 (반환값 없음)");
		hashOps.put("user:1001", "name", "Alice_Updated");
		log.info("기존 필드 수정 완료 (반환값 없음)");

		// 2. hset (다중 필드)
		// Map 을 넘겨서 여러 필드를 한번에 저장합니다.
		// name 은 예제 1 에서 이미 추가했으므로 업데이트 처리
		//
		// [차이점] Java 순수 Lettuce: command.hset(key, Map) → Long (새로 추가된 필드 수 반환)
		//          Spring Data Redis: hashOps.putAll(key, Map) → void (반환값 없음)
		//            → 메서드명이 hset(Map) 에서 putAll 로 변경됩니다.
		//            → 추가된 필드 수를 반환하지 않습니다.
		log.info("=========hset (다중 필드)=========");
		Map<String, String> userFields = Map.of(
				"name", "Alice",
				"age", "30",
				"city", "Seoul",
				"email", "alice@example.com",
				"point", "500"
		);
		hashOps.putAll("user:1001", userFields);
		log.info("다중 필드 저장 완료 (반환값 없음)");

		// 3. hget
		// hget 명령어는 Hash 에서 특정 필드의 값을 조회합니다.
		// 필드가 없거나 키가 없으면 null 반환
		//
		// [차이점 없음] 두 방식 모두 String 또는 null 반환
		// Java 순수 Lettuce: command.hget(key, field)
		// Spring Data Redis: hashOps.get(key, field)
		//   → 메서드명이 hget 에서 get 으로 변경됩니다.
		log.info("=========hget=========");
		String name = hashOps.get("user:1001", "name");
		log.info("name : {}", name);                          // Alice
		String notExistField = hashOps.get("user:1001", "phone");
		log.info("없는 필드 : {}", notExistField);            // null

		// 4. hmget
		// hmget 명령어는 여러 필드를 한번에 조회합니다.
		// 없는 필드는 null 로 반환되며, 순서는 요청한 필드 순서와 동일합니다.
		//
		// [차이점] Java 순수 Lettuce: command.hmget(key, fields...)     → List<KeyValue<String, String>> 반환
		//                               → .hasValue() 로 존재 여부 확인
		//                               → .getValue() 로 값 접근 (없으면 NoSuchElementException)
		//                               → .getValueOrElse(null) 로 안전하게 접근
		//          Spring Data Redis: hashOps.multiGet(key, List<field>) → List<String> 반환 (없으면 null 포함)
		//            → 메서드명이 hmget 에서 multiGet 으로 변경됩니다.
		//            → KeyValue 래퍼 없이 값 또는 null 의 단순 List 로 반환됩니다.
		//            → field 목록을 Collection 으로 전달해야 합니다.
		log.info("=========hmget=========");
		List<String> fields = hashOps.multiGet("user:1001", Arrays.asList("name", "email", "phone"));
		log.info("name : {}", fields.get(0));                 // Alice
		log.info("email : {}", fields.get(1));                // alice@example.com
		log.info("phone hasValue : {}", fields.get(2) != null); // false
		log.info("phone (없음) : {}", fields.get(2));         // null

		// 5. hgetall
		// hgetall 명령어는 Hash 의 모든 field-value 쌍을 Map 으로 반환합니다.
		// 필드 수가 많을 경우 부하가 클 수 있으므로 대용량 Hash 에서는 주의
		//
		// [차이점] Java 순수 Lettuce: command.hgetall(key) → Map<String, String> 반환
		//          Spring Data Redis: hashOps.entries(key)  → Map<String, String> 반환
		//            → 메서드명이 hgetall 에서 entries 로 변경됩니다.
		log.info("=========hgetall=========");
		Map<String, String> userMap = hashOps.entries("user:1001");
		log.info("전체 Hash : {}", userMap);

		// 6. hkeys / hvals / hlen
		// hkeys: 모든 필드명 반환
		// hvals: 모든 값 반환
		// hlen: 필드 수 반환
		//
		// [차이점] Java 순수 Lettuce: command.hkeys(key) → List<String> 반환
		//          Spring Data Redis: hashOps.keys(key)   → Set<String> 반환
		//            → List 가 아닌 Set 으로 반환됩니다. (순서 미보장)
		//
		//          Java 순수 Lettuce: command.hvals(key)  → List<String> 반환
		//          Spring Data Redis: hashOps.values(key) → List<String> 반환 (동일)
		//
		//          Java 순수 Lettuce: command.hlen(key)   → Long 반환
		//          Spring Data Redis: hashOps.size(key)   → Long 반환
		//            → 메서드명이 hlen 에서 size 로 변경됩니다. (llen, scard 와 동일한 패턴)
		log.info("=========hkeys / hvals / hlen=========");
		Set<String> keys = hashOps.keys("user:1001");
		log.info("필드명 목록 : {}", keys);
		List<String> vals = hashOps.values("user:1001");
		log.info("값 목록 : {}", vals);
		Long fieldCount = hashOps.size("user:1001");
		log.info("필드 수 : {}", fieldCount);

		// 7. hexists
		// hexists 명령어는 Hash 에 특정 필드가 존재하는지 확인합니다.
		// 존재하면 true, 없으면 false 반환
		//
		// [차이점 없음] 두 방식 모두 Boolean 반환 (ioredis 와 달리 Boolean 유지)
		// Java 순수 Lettuce: command.hexists(key, field)
		// Spring Data Redis: hashOps.hasKey(key, field)
		//   → 메서드명이 hexists 에서 hasKey 로 변경됩니다.
		log.info("=========hexists=========");
		Boolean hasEmail = hashOps.hasKey("user:1001", "email");
		log.info("email 필드 존재? : {}", hasEmail);         // true
		Boolean hasPhone = hashOps.hasKey("user:1001", "phone");
		log.info("phone 필드 존재? : {}", hasPhone);         // false

		// 8. hdel
		// hdel 명령어는 Hash 에서 특정 필드를 삭제합니다.
		// 반환값은 실제로 삭제된 필드 수
		// 없는 필드를 삭제하려 해도 에러 없이 0 반환
		//
		// [차이점] Java 순수 Lettuce: command.hdel(key, fields...)
		//          Spring Data Redis: hashOps.delete(key, fields...)
		//            → 메서드명이 hdel 에서 delete 로 변경됩니다.
		log.info("=========hdel=========");
		Long deletedCount = hashOps.delete("user:1001", "city");
		log.info("삭제된 필드 수 : {}", deletedCount);       // 1
		Long notExistDel = hashOps.delete("user:1001", "phone");
		log.info("없는 필드 삭제 시도 : {}", notExistDel);   // 0
		log.info("삭제 후 필드 수 : {}", hashOps.size("user:1001"));

		// 9. hsetnx
		// hsetnx 명령어는 필드가 존재하지 않을 때만 값을 설정합니다.
		// 설정 성공(신규) 시 true, 이미 존재하면 false 반환 (덮어쓰지 않음)
		//
		// [차이점 없음] 두 방식 모두 Boolean 반환 (ioredis 와 달리 Boolean 유지)
		// Java 순수 Lettuce: command.hsetnx(key, field, value)
		// Spring Data Redis: hashOps.putIfAbsent(key, field, value)
		//   → 메서드명이 hsetnx 에서 putIfAbsent 로 변경됩니다.
		log.info("=========hsetnx=========");
		Boolean setNew = hashOps.putIfAbsent("user:1001", "nickname", "alice99");
		log.info("nickname 신규 설정 : {}", setNew);         // true
		Boolean setExist = hashOps.putIfAbsent("user:1001", "name", "Bob");
		log.info("name 이미 존재 (덮어쓰기 X) : {}", setExist); // false
		log.info("name 은 그대로 : {}", hashOps.get("user:1001", "name")); // Alice

		// 10. hincrby
		// hincrby 명령어는 Hash 의 필드 값을 정수만큼 증가시킵니다.
		// 감소는 음수 값을 넘기면 됩니다.
		// 필드가 없으면 0 으로 초기화 후 증가
		//
		// [차이점] Java 순수 Lettuce: command.hincrby(key, field, amount)    → Long 반환
		//          Spring Data Redis: hashOps.increment(key, field, delta)    → Long 반환
		//            → 메서드명이 hincrby 에서 increment 로 변경됩니다.
		//            → long 타입 delta 를 넘기면 정수 증가 (hincrbyfloat 과 동일 메서드, 타입으로 구분)
		log.info("=========hincrby=========");
		Long newPoint = hashOps.increment("user:1001", "point", 100L);
		log.info("포인트 100 적립 후 : {}", newPoint);       // 600
		Long afterDecrease = hashOps.increment("user:1001", "point", -200L);
		log.info("포인트 200 차감 후 : {}", afterDecrease);  // 400

		// 11. hincrbyfloat
		// hincrbyfloat 명령어는 Hash 의 필드 값을 실수만큼 증가시킵니다.
		// 평점 누적, 소수점 카운터 등에 활용
		//
		// [차이점] Java 순수 Lettuce: command.hincrbyfloat(key, field, amount) → Double 반환
		//          Spring Data Redis: hashOps.increment(key, field, delta)      → Double 반환
		//            → hincrby / hincrbyfloat 구분 없이 increment 메서드 하나로 통합됩니다.
		//            → delta 를 double 타입으로 넘기면 자동으로 hincrbyfloat 처럼 동작합니다.
		log.info("=========hincrbyfloat=========");
		hashOps.put("product:55", "rating", "4.0");
		Double newRating = hashOps.increment("product:55", "rating", 0.5);
		log.info("평점 0.5 추가 후 : {}", newRating);        // 4.5
		Double afterMore = hashOps.increment("product:55", "rating", -1.0);
		log.info("평점 1.0 감소 후 : {}", afterMore);        // 3.5

		// =========================================================
		// 실전 활용 예시: Hash vs String(JSON) 비교
		// =========================================================

		// [Case 1] 포인트만 업데이트 — Hash 가 압도적으로 유리
		//
		// String(JSON) 방식 (애플리케이션 코드 필요):
		//   String json = redisTemplate.opsForValue().get("user:1001");
		//   User user = objectMapper.readValue(json, User.class); // 역직렬화
		//   user.setPoint(user.getPoint() + 100);                 // 수정
		//   redisTemplate.opsForValue().set("user:1001", objectMapper.writeValueAsString(user)); // 재저장
		//
		// Hash 방식 (Redis 한 줄):
		log.info("=========실전 예시: 포인트 업데이트=========");
		hashOps.increment("user:1001", "point", 100L);
		log.info("포인트 업데이트 완료 : {}", hashOps.get("user:1001", "point"));

		// [Case 2] 이름과 이메일만 조회 — Hash 가 유리
		//
		// String(JSON) 방식: 전체 JSON 을 GET 해서 필요한 필드만 추출
		// Hash 방식: 필요한 필드만 정확히 조회
		log.info("=========실전 예시: 필요한 필드만 조회=========");
		List<String> profile = hashOps.multiGet("user:1001", Arrays.asList("name", "email"));
		log.info("이름: {}, 이메일: {}", profile.get(0), profile.get(1));

		// [Case 3] TTL 설정 — 키 단위로만 가능 (Hash 의 한계)
		//
		// Hash 는 개별 필드에 TTL 을 설정할 수 없습니다.
		// user:1001 키 전체에만 만료 시간을 부여할 수 있습니다.
		// 필드별로 다른 만료가 필요하다면 String 으로 분리해야 합니다.
		//
		// [차이점] Java 순수 Lettuce: command.expire(key, seconds)       → Boolean 반환
		//                             command.ttl(key)                    → Long 반환
		//          Spring Data Redis: redisTemplate.expire(key, Duration) → Boolean 반환
		//                             redisTemplate.getExpire(key)        → Long 반환
		//            → expire/ttl 은 HashOperations 가 아닌 redisTemplate 에서 직접 호출합니다.
		//            → 메서드명이 ttl 에서 getExpire 로 변경됩니다.
		log.info("=========Hash TTL (키 단위만 가능)=========");
		redisTemplate.expire("user:1001", Duration.ofSeconds(3600)); // 1시간 후 만료
		Long ttl = redisTemplate.getExpire("user:1001");
		log.info("user:1001 만료까지 남은 시간(초) : {}", ttl);
	}
}
