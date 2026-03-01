package com.baekgwa.example;

import java.util.List;
import java.util.Map;

import com.baekgwa.connect.RedisConnect;

import io.lettuce.core.KeyValue;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * PackageName : com.baekgwa.example
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
// =====================================================================================================================
// Hash vs String(JSON) — 언제 Hash 를 써야 하는가?
// =====================================================================================================================
//
// [String(JSON) 으로 저장할 때의 흐름]
//   1. Redis 에서 전체 JSON 문자열 GET
//   2. 애플리케이션에서 역직렬화(JSON → 객체)
//   3. 특정 필드(age) 수정
//   4. 다시 직렬화(객체 → JSON)
//   5. Redis 에 전체 문자열 SET
//   → 필드 하나 바꾸려고 항상 전체를 읽고 씀
//
// [Hash 로 저장할 때의 흐름]
//   1. HSET user:1001 age 31
//   → 필요한 필드만 정확히 수정, 나머지 필드는 건드리지 않음
//
// =====================================================================================================================
// Hash 가 유리한 상황
// =====================================================================================================================
//  1. 객체의 일부 필드만 자주 업데이트할 때
//     예: 사용자 프로필에서 마지막 로그인 시간, 포인트, 상태 등을 개별적으로 갱신
//
//  2. 객체의 일부 필드만 조회할 때
//     예: 이름과 이메일만 필요한 API → HMGET user:1001 name email
//         String/JSON 이면 전체를 다 읽어와야 함
//
//  3. 필드별 숫자 연산이 필요할 때
//     예: HINCRBY user:1001 point 100 (포인트 적립)
//         HINCRBYFLOAT product:55 rating 0.5 (평점 누적)
//         String 으로는 이 연산을 원자적으로 처리하기 어려움
//
//  4. 메모리 최적화가 중요할 때
//     Hash 의 필드 수 ≤ 128 개, 값 크기 ≤ 64 bytes 이면
//     Redis 가 내부적으로 ziplist 인코딩을 사용 → JSON 보다 메모리 훨씬 절약
//
// =====================================================================================================================
// String(JSON) 이 유리한 상황
// =====================================================================================================================
//  1. 항상 객체 전체를 통째로 읽고 쓸 때
//     예: 캐시 목적으로 DB 조회 결과를 통으로 저장할 때
//
//  2. 필드별로 TTL 이 달라야 할 때
//     Hash 는 키 단위로만 TTL 설정 가능 (개별 필드에 TTL 불가)
//
//  3. 중첩 객체(nested object)를 다룰 때
//     Hash 는 depth 1 (field → value) 구조만 지원
//     { address: { city: "Seoul", zip: "12345" } } 같은 구조는 Hash 로 표현 불가
//
//  4. 객체를 원자적으로 전체 교체해야 할 때
//     SET 명령어 하나로 전체를 덮어쓸 수 있음

public class HashExample {

	public static void start() {
		RedisConnect redisConnect = new RedisConnect();

		try (StatefulRedisConnection<String, String> connection = redisConnect.getConnection()) {
			RedisCommands<String, String> command = connection.sync();

			// 0. 안의 내용들 모두 초기화.
			command.flushdb();

			// 1. hset (단일 필드)
			// hset 명령어는 Hash 에 field-value 쌍을 저장합니다.
			// 신규 필드 추가 시 1, 기존 필드 업데이트 시 0 반환
			// (Redis 4.0+ 부터 hmset 대신 hset 으로 다중 필드도 처리)
			System.out.println("=========hset (단일 필드)=========");
			Boolean isNew = command.hset("user:1001", "name", "Alice");
			System.out.println("신규 필드 추가 : " + isNew);               // true
			Boolean isUpdate = command.hset("user:1001", "name", "Alice_Updated");
			System.out.println("기존 필드 수정 : " + isUpdate);            // false (업데이트)

			// 2. hset (다중 필드)
			// Map 을 넘겨서 여러 필드를 한번에 저장합니다.
			// 반환값은 새로 추가된 필드 수 (업데이트된 필드는 미포함)
			// name 은 예제 1 에서 이미 추가했으므로 업데이트 처리 → 반환값에 미포함
			System.out.println("=========hset (다중 필드)=========");
			Map<String, String> userFields = Map.of(
					"name", "Alice",
					"age", "30",
					"city", "Seoul",
					"email", "alice@example.com",
					"point", "500"
			);
			Long addedFields = command.hset("user:1001", userFields);
			System.out.println("새로 추가된 필드 수 : " + addedFields);

			// 3. hget
			// hget 명령어는 Hash 에서 특정 필드의 값을 조회합니다.
			// 필드가 없거나 키가 없으면 null 반환
			System.out.println("=========hget=========");
			String name = command.hget("user:1001", "name");
			System.out.println("name : " + name);                          // Alice
			String notExistField = command.hget("user:1001", "phone");
			System.out.println("없는 필드 : " + notExistField);            // null

			// 4. hmget
			// hmget 명령어는 여러 필드를 한번에 조회합니다.
			// 반환 타입은 List<KeyValue<String, String>> — KeyValue 는 (field, value) 쌍
			// 없는 필드는 hasValue() == false 이며, 순서는 요청한 필드 순서와 동일합니다.
			// → String(JSON) 이라면 전체를 읽어와야 하지만, Hash 는 필요한 것만 조회 가능
			System.out.println("=========hmget=========");
			List<KeyValue<String, String>> fields = command.hmget("user:1001", "name", "email", "phone"); // phone 은 없는 필드
			System.out.println("name : " + fields.get(0).getValue());                // Alice
			System.out.println("email : " + fields.get(1).getValue());               // alice@example.com
			System.out.println("phone hasValue : " + fields.get(2).hasValue());      // false
			System.out.println("phone (없음) : " + fields.get(2).getValueOrElse(null)); // null
			// System.out.println("phone (없음) : " + fields.get(2).getValue()); // NoSuchElementException 발생

			// 5. hgetall
			// hgetall 명령어는 Hash 의 모든 field-value 쌍을 Map 으로 반환합니다.
			// 필드 수가 많을 경우 부하가 클 수 있으므로 대용량 Hash 에서는 주의
			System.out.println("=========hgetall=========");
			Map<String, String> userMap = command.hgetall("user:1001");
			System.out.println("전체 Hash : " + userMap);

			// 6. hkeys / hvals / hlen
			// hkeys: 모든 필드명 반환
			// hvals: 모든 값 반환
			// hlen: 필드 수 반환
			System.out.println("=========hkeys / hvals / hlen=========");
			List<String> keys = command.hkeys("user:1001");
			System.out.println("필드명 목록 : " + keys);
			List<String> vals = command.hvals("user:1001");
			System.out.println("값 목록 : " + vals);
			Long fieldCount = command.hlen("user:1001");
			System.out.println("필드 수 : " + fieldCount);

			// 7. hexists
			// hexists 명령어는 Hash 에 특정 필드가 존재하는지 확인합니다.
			// 존재하면 true, 없으면 false 반환
			System.out.println("=========hexists=========");
			Boolean hasEmail = command.hexists("user:1001", "email");
			System.out.println("email 필드 존재? : " + hasEmail);          // true
			Boolean hasPhone = command.hexists("user:1001", "phone");
			System.out.println("phone 필드 존재? : " + hasPhone);          // false

			// 8. hdel
			// hdel 명령어는 Hash 에서 특정 필드를 삭제합니다.
			// 반환값은 실제로 삭제된 필드 수
			// 없는 필드를 삭제하려 해도 에러 없이 0 반환
			System.out.println("=========hdel=========");
			Long deletedCount = command.hdel("user:1001", "city");
			System.out.println("삭제된 필드 수 : " + deletedCount);        // 1
			Long notExistDel = command.hdel("user:1001", "phone");
			System.out.println("없는 필드 삭제 시도 : " + notExistDel);    // 0
			System.out.println("삭제 후 필드 수 : " + command.hlen("user:1001"));

			// 9. hsetnx
			// hsetnx 명령어는 필드가 존재하지 않을 때만 값을 설정합니다.
			// 설정 성공(신규) 시 true, 이미 존재하면 false 반환 (덮어쓰지 않음)
			// 동시성 환경에서 "이미 존재하면 건드리지 않는다" 를 보장할 때 유용
			System.out.println("=========hsetnx=========");
			Boolean setNew = command.hsetnx("user:1001", "nickname", "alice99");
			System.out.println("nickname 신규 설정 : " + setNew);          // true
			Boolean setExist = command.hsetnx("user:1001", "name", "Bob");
			System.out.println("name 이미 존재 (덮어쓰기 X) : " + setExist); // false
			System.out.println("name 은 그대로 : " + command.hget("user:1001", "name")); // Alice

			// 10. hincrby
			// hincrby 명령어는 Hash 의 필드 값을 정수만큼 증가시킵니다.
			// 감소는 음수 값을 넘기면 됩니다.
			// 필드가 없으면 0 으로 초기화 후 증가
			// → String 이라면 GET → 파싱 → 계산 → SET 이 필요하지만,
			//   Hash 는 단일 명령어로 원자적 처리 가능
			System.out.println("=========hincrby=========");
			Long newPoint = command.hincrby("user:1001", "point", 100);
			System.out.println("포인트 100 적립 후 : " + newPoint);        // 600
			Long afterDecrease = command.hincrby("user:1001", "point", -200);
			System.out.println("포인트 200 차감 후 : " + afterDecrease);   // 400

			// 11. hincrbyfloat
			// hincrbyfloat 명령어는 Hash 의 필드 값을 실수만큼 증가시킵니다.
			// 평점 누적, 소수점 카운터 등에 활용
			System.out.println("=========hincrbyfloat=========");
			command.hset("product:55", Map.of("rating", "4.0"));
			Double newRating = command.hincrbyfloat("product:55", "rating", 0.5);
			System.out.println("평점 0.5 추가 후 : " + newRating);         // 4.5
			Double afterMore = command.hincrbyfloat("product:55", "rating", -1.0);
			System.out.println("평점 1.0 감소 후 : " + afterMore);         // 3.5

			// =========================================================
			// 실전 활용 예시: Hash vs String(JSON) 비교
			// =========================================================

			// [Case 1] 포인트만 업데이트 — Hash 가 압도적으로 유리
			//
			// String(JSON) 방식 (애플리케이션 코드 필요):
			//   String json = command.get("user:1001");
			//   User user = objectMapper.readValue(json, User.class); // 역직렬화
			//   user.setPoint(user.getPoint() + 100);                 // 수정
			//   command.set("user:1001", objectMapper.writeValueAsString(user)); // 재저장
			//
			// Hash 방식 (Redis 한 줄):
			System.out.println("=========실전 예시: 포인트 업데이트=========");
			command.hincrby("user:1001", "point", 100);
			System.out.println("포인트 업데이트 완료 : " + command.hget("user:1001", "point"));

			// [Case 2] 이름과 이메일만 조회 — Hash 가 유리
			//
			// String(JSON) 방식: 전체 JSON 을 GET 해서 필요한 필드만 추출
			// Hash 방식: 필요한 필드만 정확히 조회
			System.out.println("=========실전 예시: 필요한 필드만 조회=========");
			List<KeyValue<String, String>> profile = command.hmget("user:1001", "name", "email");
			System.out.println("이름: " + profile.get(0).getValue() + ", 이메일: " + profile.get(1).getValue());

			// [Case 3] TTL 설정 — 키 단위로만 가능 (Hash 의 한계)
			//
			// Hash 는 개별 필드에 TTL 을 설정할 수 없습니다.
			// user:1001 키 전체에만 만료 시간을 부여할 수 있습니다.
			// 필드별로 다른 만료가 필요하다면 String 으로 분리해야 합니다.
			System.out.println("=========Hash TTL (키 단위만 가능)=========");
			command.expire("user:1001", 3600);                             // 1시간 후 만료
			System.out.println("user:1001 만료까지 남은 시간(초) : " + command.ttl("user:1001"));
		}
	}
}
