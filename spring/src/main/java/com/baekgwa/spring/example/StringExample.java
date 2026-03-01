package com.baekgwa.spring.example;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

/**
 * PackageName : com.baekgwa.spring.example
 * FileName    : StringExample
 * Author      : Baekgwa
 * Date        : 26. 2. 28.
 * Description : 
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2025-08-03     Baekgwa               Initial creation
 **/
@Component
public class StringExample {

	private static final Logger log = LoggerFactory.getLogger(StringExample.class);

	private final RedisTemplate<String, String> redisTemplate;

	public StringExample(RedisTemplate<String, String> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public void run(String... args) {
		// ValueOperations: String 타입의 key-value 명령을 담당하는 객체
		// Java 순수 Lettuce의 RedisCommands에서 String 관련 명령만 추출한 것과 유사합니다.
		ValueOperations<String, String> ops = redisTemplate.opsForValue();

		// 0. 안의 내용들 모두 초기화.
		// Spring Data Redis에는 flushDb()를 직접 호출하는 메서드가 없습니다.
		// RedisCallback을 통해 low-level connection에 직접 접근하여 실행합니다.
		// Java 순수 Lettuce: command.flushdb()
		redisTemplate.execute((RedisCallback<Object>)connection -> {
			connection.serverCommands().flushAll();
			return null;
		});

		// 1. set
		// set 명령어는 항상 성공합니다.
		// 없는 키에 set 을 한다면? 새로운 키를 만들과 값을 등록
		// 있는 키에 set 을 한다면? 있는 키에 값을 덮어쓰기
		//
		// [차이점] Java 순수 Lettuce: command.set() → String "OK" 반환
		//          Spring Data Redis: ops.set()     → void (반환값 없음)
		log.info("=========set=========");
		ops.set("name", "백과");
		log.info("저장 성공? : OK (Spring Data Redis의 set()은 항상 void 반환)");
		ops.set("name", "백과");
		log.info("2번째 저장 성공? : OK");

		// 2. get
		// get 명령어 또한 항상 성공합니다. (exception 없음)
		// 만약 있는 key 로 조회한다면 값 반환
		// 없는 key 로 조회한다면 null 반환
		//
		// [차이점 없음] 두 방식 모두 String 또는 null 반환
		log.info("=========get=========");
		String savedName = ops.get("name");
		log.info("내 이름은 : {}", savedName);
		String notExistKey = ops.get("notExistKey");
		log.info("없는 키는 : {}", notExistKey);

		// 3. incr
		// incr 명령어는 값을 1 증가시킵니다.
		// 반환값은 명령어를 수행한 후 저장된 값을 반환합니다.
		// 키가 없으면 0 으로 초기화 후 1 반환
		//
		// [차이점 없음] 두 방식 모두 Long 반환
		// Java 순수 Lettuce: command.incr("viewCount")
		// Spring Data Redis: ops.increment("viewCount")
		log.info("=========incr=========");
		Long viewCount = ops.increment("viewCount");
		log.info("조회수 : {}", viewCount);

		// 문자열은 increment() 할 수 없어, Exception 발생
		// Exception: io.lettuce.core.RedisCommandExecutionException: ERR value is not an integer or out of range
		// ops.set("stringCount", "stringCount");
		// Long stringCount = ops.increment("stringCount");

		// 4. decr
		// decr 명령어는 값을 1 감소시킵니다.
		// 반환값은 명령어를 수행한 후 저장된 값을 반환합니다.
		// 키가 없으면 0 으로 초기화 후 -1 반환
		//
		// [차이점 없음] 두 방식 모두 Long 반환
		// Java 순수 Lettuce: command.decr("viewCount")
		// Spring Data Redis: ops.decrement("viewCount")
		log.info("=========decr=========");
		Long decreasedCount = ops.decrement("viewCount");
		log.info("감소된 조회수 : {}", decreasedCount);

		// 5. incrby
		// incrby 명령어는 값을 특정 수만큼 증가시킵니다.
		// incr 은 무조건 1 증가, incrby 는 원하는 수만큼 증가 가능
		//
		// [차이점 없음] 두 방식 모두 Long 반환
		// Java 순수 Lettuce: command.incrby("viewCount", 10)
		// Spring Data Redis: ops.increment("viewCount", 10)  ← increment에 delta 인자 추가
		log.info("=========incrby=========");
		Long incrByCount = ops.increment("viewCount", 10);
		log.info("10 증가된 조회수 : {}", incrByCount);

		// 6. decrby
		// decrby 명령어는 값을 특정 수만큼 감소시킵니다.
		// decr 은 무조건 1 감소, decrby 는 원하는 수만큼 감소 가능
		//
		// [차이점 없음] 두 방식 모두 Long 반환
		// Java 순수 Lettuce: command.decrby("viewCount", 5)
		// Spring Data Redis: ops.decrement("viewCount", 5)  ← decrement에 delta 인자 추가
		log.info("=========decrby=========");
		Long decrByCount = ops.decrement("viewCount", 5);
		log.info("5 감소된 조회수 : {}", decrByCount);

		// 7. mset
		// mset 명령어는 여러 키-값 쌍을 한번에 저장합니다.
		// 원자적으로 처리되며, 일부만 실패하는 경우는 없습니다.
		//
		// [차이점] Java 순수 Lettuce: command.mset(map) → String "OK" 반환
		//          Spring Data Redis: ops.multiSet(map) → void (반환값 없음)
		log.info("=========mset=========");
		Map<String, String> multiMap = new HashMap<>();
		multiMap.put("city", "서울");
		multiMap.put("country", "대한민국");
		multiMap.put("language", "한국어");
		ops.multiSet(multiMap);
		log.info("다중 저장 성공? : OK (Spring Data Redis의 multiSet()은 항상 void 반환)");

		// 8. mget
		// mget 명령어는 여러 키의 값을 한번에 조회합니다.
		// 없는 키는 null 로 반환됩니다.
		// 입력한 키 순서대로 결과가 반환됩니다.
		//
		// [차이점] Java 순수 Lettuce: List<KeyValue<String, String>> 반환
		//            → KeyValue.hasValue()로 존재 여부 확인, KeyValue.getKey()로 키 접근 가능
		//          Spring Data Redis: List<String> 반환 (없는 키는 null)
		//            → 키 정보는 포함되지 않고 값만 반환됩니다. 키가 필요하면 직접 매핑해야 합니다.
		log.info("=========mget=========");
		List<String> keys = Arrays.asList("city", "country", "language", "notExistKey");
		List<String> mgetResult = ops.multiGet(keys);
		for (int i = 0; i < keys.size(); i++) {
			log.info("{} : {}", keys.get(i), mgetResult.get(i));
		}

		// 9. setex (set with expire)
		// setex 명령어는 값을 저장하면서 만료 시간(초)을 함께 설정합니다.
		// 만료 시간이 지나면 해당 키는 자동으로 삭제됩니다.
		// 캐시처럼 일정 시간만 유지해야 할 데이터에 유용합니다.
		//
		// [차이점] Java 순수 Lettuce: command.setex("key", 60, "value")    → String "OK" 반환
		//          Spring Data Redis: ops.set("key", "value", 60, TimeUnit.SECONDS) → void
		//            → 별도의 setex() 메서드 없이 set()에 Duration / TimeUnit 인자를 추가하는 방식입니다.
		log.info("=========setex=========");
		ops.set("tempKey", "임시 데이터", 60, TimeUnit.SECONDS);
		log.info("만료 시간 포함 저장 성공? : OK (Spring Data Redis의 set(with TTL)은 void 반환)");

		// 10. ttl
		// ttl 명령어는 키의 남은 만료 시간(초)을 반환합니다.
		// 만료 시간이 설정되지 않은 키 → -1 반환
		// 존재하지 않는 키              → -2 반환
		//
		// [차이점] Java 순수 Lettuce: command.ttl("key") → Long (초 단위)
		//          Spring Data Redis: template.getExpire("key") → Long (초 단위)
		//            → opsForValue()가 아닌 template에서 직접 호출합니다.
		//            → template.getExpire("key", TimeUnit.SECONDS) 로 단위 지정도 가능합니다.
		log.info("=========ttl=========");
		Long ttlTemp = redisTemplate.getExpire("tempKey");
		log.info("tempKey 의 남은 만료 시간 : {}초", ttlTemp);
		Long ttlNoExpire = redisTemplate.getExpire("name");
		log.info("만료 시간 없는 키의 ttl : {}", ttlNoExpire);
		Long ttlNotExist = redisTemplate.getExpire("notExistKey");
		log.info("없는 키의 ttl : {}", ttlNotExist);

		// 11. setnx (set if not exists)
		// setnx 명령어는 키가 존재하지 않을 때만 저장합니다.
		// 이미 키가 존재한다면 → 저장하지 않고 false 반환
		// 키가 없다면         → 저장하고 true 반환
		// 분산 락(Distributed Lock) 구현에 많이 활용됩니다.
		//
		// [차이점 없음] 두 방식 모두 Boolean 반환
		// Java 순수 Lettuce: command.setnx("key", "value")
		// Spring Data Redis: ops.setIfAbsent("key", "value")
		//   → 메서드명이 다릅니다. setnx → setIfAbsent (의미를 더 명확하게 표현)
		//   → setIfAbsent("key", "value", 60, TimeUnit.SECONDS) 로 TTL도 함께 설정 가능합니다.
		log.info("=========setnx=========");
		Boolean setnxFirst = ops.setIfAbsent("setnxKey", "최초 저장");
		log.info("최초 저장 성공? : {}", setnxFirst);
		Boolean setnxSecond = ops.setIfAbsent("setnxKey", "두번째 저장 시도");
		log.info("두번째 저장 성공? : {}", setnxSecond);
		String setnxValue = ops.get("setnxKey");
		log.info("setnxKey 의 값 : {}", setnxValue);
	}
}
