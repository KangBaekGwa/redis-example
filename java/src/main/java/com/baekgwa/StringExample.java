package com.baekgwa;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baekgwa.connect.RedisConnect;

import io.lettuce.core.KeyValue;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * PackageName : com.baekgwa
 * FileName    : StringExample
 * Author      : Baekgwa
 * Date        : 26. 2. 28.
 * Description : 
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2025-08-03     Baekgwa               Initial creation
 **/
// Java 는 기본이 동기 Blocking 모델
// 따라서 명시적으로 Lettuce 는 3가지 방식을 제공
// connection.sync();      // blocking
// connection.async();     // Future 기반
// connection.reactive();  // Reactor 기반
// 사실 .sync() 도 .async() 의 레퍼
public class StringExample {

	public static void start() {
		RedisConnect redisConnect = new RedisConnect();

		try (StatefulRedisConnection<String, String> connection = redisConnect.getConnection()) {
			RedisCommands<String, String> command = connection.sync();

			// 0. 안의 내용들 모두 초기화.
			command.flushdb();

			// 1. set
			// set 명령어는 항상 성공합니다.
			// 없는 키에 set 을 한다면? 새로운 키를 만들과 값을 등록
			// 있는 키에 set 을 한다면? 있는 키에 값을 덮어쓰기
			System.out.println("=========set=========");
			String isSuccess = command.set("name", "백과");
			System.out.println("저장 성공? : " + isSuccess);
			isSuccess = command.set("name", "백과");
			System.out.println("2번째 저장 성공? : " + isSuccess);

			// 2. get
			// get 명령어 또한 항상 성공합니다. (exception 없음)
			// 만약 있는 key 로 조회한다면 값 반환
			// 없는 key 로 조회한다면 null 반환
			System.out.println("=========get=========");
			String savedName = command.get("name");
			System.out.println("내 이름은 : " + savedName);
			String notExistKey = command.get("notExistKey");
			System.out.println("없는 키는 : " + notExistKey);

			// 3. incr
			// incr 명령어는 값을 증가시킵니다.
			// 반환값은 명령어를 수행한 후 저장된 값을 반환합니다.
			// 키가 없으면 0 으로 초기화 후 1 반환
			System.out.println("=========incr=========");
			Long viewCount = command.incr("viewCount");
			System.out.println("조회수 : " + viewCount);

			// 문자열은 incr() 할 수 없어, Exception 발생
			// Exception in thread "main" io.lettuce.core.RedisCommandExecutionException: ERR value is not an integer or out of range
			//command.set("stringCount", "stringCount");
			//Long stringCount = command.incr("stringCount");
			//System.out.println("stringCount : " + stringCount);

			// 4. decr
			// decr 명령어는 값을 1 감소시킵니다.
			// 반환값은 명령어를 수행한 후 저장된 값을 반환합니다.
			// incr 과 반대로 동작합니다.
			// 키가 없으면 0 으로 초기화 후 -1 반환
			System.out.println("=========decr=========");
			Long decreasedCount = command.decr("viewCount");
			System.out.println("감소된 조회수 : " + decreasedCount);

			// 5. incrby
			// incrby 명령어는 값을 특정 수만큼 증가시킵니다.
			// incr 은 무조건 1 증가, incrby 는 원하는 수만큼 증가 가능
			System.out.println("=========incrby=========");
			Long incrByCount = command.incrby("viewCount", 10);
			System.out.println("10 증가된 조회수 : " + incrByCount);

			// 6. decrby
			// decrby 명령어는 값을 특정 수만큼 감소시킵니다.
			// decr 은 무조건 1 감소, decrby 는 원하는 수만큼 감소 가능
			System.out.println("=========decrby=========");
			Long decrByCount = command.decrby("viewCount", 5);
			System.out.println("5 감소된 조회수 : " + decrByCount);

			// 7. mset
			// mset 명령어는 여러 키-값 쌍을 한번에 저장합니다.
			// 원자적으로 처리되며, 일부만 실패하는 경우는 없습니다.
			System.out.println("=========mset=========");
			Map<String, String> multiMap = new HashMap<>();
			multiMap.put("city", "서울");
			multiMap.put("country", "대한민국");
			multiMap.put("language", "한국어");
			String msetResult = command.mset(multiMap);
			System.out.println("다중 저장 성공? : " + msetResult);

			// 8. mget
			// mget 명령어는 여러 키의 값을 한번에 조회합니다.
			// 없는 키는 KeyValue.empty() 로 반환되며, hasValue() 로 확인 가능
			// 입력한 키 순서대로 결과가 반환됩니다.
			System.out.println("=========mget=========");
			List<KeyValue<String, String>> mgetResult = command.mget("city", "country", "language", "notExistKey");
			mgetResult.forEach(kv -> System.out.println(kv.getKey() + " : " + (kv.hasValue() ? kv.getValue() : "null")));

			// 9. setex
			// setex 명령어는 값을 저장하면서 만료 시간(초)을 함께 설정합니다.
			// 만료 시간이 지나면 해당 키는 자동으로 삭제됩니다.
			// 캐시처럼 일정 시간만 유지해야 할 데이터에 유용합니다.
			System.out.println("=========setex=========");
			String setexResult = command.setex("tempKey", 60, "임시 데이터");
			System.out.println("만료 시간 포함 저장 성공? : " + setexResult);

			// 10. ttl
			// ttl 명령어는 키의 남은 만료 시간(초)을 반환합니다.
			// 만료 시간이 설정되지 않은 키 → -1 반환
			// 존재하지 않는 키              → -2 반환
			System.out.println("=========ttl=========");
			Long ttlTemp = command.ttl("tempKey");
			System.out.println("tempKey 의 남은 만료 시간 : " + ttlTemp + "초");
			Long ttlNoExpire = command.ttl("name");
			System.out.println("만료 시간 없는 키의 ttl : " + ttlNoExpire);
			Long ttlNotExist = command.ttl("notExistKey");
			System.out.println("없는 키의 ttl : " + ttlNotExist);

			// 11. setnx
			// setnx 명령어는 키가 존재하지 않을 때만 저장합니다. (SET if Not exists)
			// 이미 키가 존재한다면 → 저장하지 않고 false 반환
			// 키가 없다면         → 저장하고 true 반환
			// 분산 락(Distributed Lock) 구현에 많이 활용됩니다.
			System.out.println("=========setnx=========");
			Boolean setnxFirst = command.setnx("setnxKey", "최초 저장");
			System.out.println("최초 저장 성공? : " + setnxFirst);
			Boolean setnxSecond = command.setnx("setnxKey", "두번째 저장 시도");
			System.out.println("두번째 저장 성공? : " + setnxSecond);
			String setnxValue = command.get("setnxKey");
			System.out.println("setnxKey 의 값 : " + setnxValue);
		}
	}
}
