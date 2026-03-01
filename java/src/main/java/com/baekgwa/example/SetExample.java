package com.baekgwa.example;

import java.util.List;
import java.util.Set;

import com.baekgwa.connect.RedisConnect;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * PackageName : com.baekgwa.example
 * FileName    : SetExample
 * Author      : Baekgwa
 * Date        : 26. 3. 1.
 * Description :
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2026-03-01     Baekgwa               Initial creation
 **/
// Redis Set 은 순서 없는 유일한 문자열의 집합
// 중복을 허용하지 않으며, 합집합/교집합/차집합 등 집합 연산을 O(N) 으로 지원
// 활용 사례: 좋아요 목록, 팔로워/팔로잉, 태그, 방문자 추적 등
public class SetExample {

	public static void start() {
		RedisConnect redisConnect = new RedisConnect();

		try (StatefulRedisConnection<String, String> connection = redisConnect.getConnection()) {
			RedisCommands<String, String> command = connection.sync();

			// 0. 안의 내용들 모두 초기화.
			command.flushdb();

			// 1. sadd
			// sadd 명령어는 Set 에 멤버를 추가합니다.
			// 반환값은 실제로 추가된 멤버의 수입니다. (이미 존재하면 추가 안 됨)
			// 중복 값은 무시됩니다.
			System.out.println("=========sadd=========");
			Long addedCount = command.sadd("fruits", "apple", "banana", "orange");
			System.out.println("추가된 멤버 수 : " + addedCount);          // 3
			Long duplicateCount = command.sadd("fruits", "apple", "grape");
			System.out.println("중복 포함 추가 시 실제 추가 수 : " + duplicateCount); // 1 (apple 은 중복)

			// 2. smembers
			// smembers 명령어는 Set 의 모든 멤버를 반환합니다.
			// 반환 순서는 보장되지 않습니다. (Set 특성)
			// 키가 없으면 빈 Set 반환
			System.out.println("=========smembers=========");
			Set<String> allFruits = command.smembers("fruits");
			System.out.println("모든 과일 : " + allFruits);
			Set<String> notExist = command.smembers("notExistKey");
			System.out.println("없는 키 smembers : " + notExist);          // []

			// 3. scard
			// scard 명령어는 Set 의 크기(멤버 수)를 반환합니다.
			// 키가 없으면 0 반환
			System.out.println("=========scard=========");
			Long fruitCount = command.scard("fruits");
			System.out.println("과일 Set 크기 : " + fruitCount);           // 4
			Long notExistCard = command.scard("notExistKey");
			System.out.println("없는 키 scard : " + notExistCard);         // 0

			// 4. sismember
			// sismember 명령어는 특정 멤버가 Set 에 존재하는지 확인합니다.
			// 존재하면 true, 없으면 false 반환
			System.out.println("=========sismember=========");
			Boolean hasApple = command.sismember("fruits", "apple");
			System.out.println("apple 존재? : " + hasApple);               // true
			Boolean hasMango = command.sismember("fruits", "mango");
			System.out.println("mango 존재? : " + hasMango);               // false

			// 5. smismember
			// smismember 명령어는 여러 멤버의 존재 여부를 한번에 확인합니다.
			// 입력한 순서대로 Boolean 리스트를 반환합니다.
			System.out.println("=========smismember=========");
			List<Boolean> memberChecks = command.smismember("fruits", "apple", "mango", "banana");
			System.out.println("apple 존재? : " + memberChecks.get(0));    // true
			System.out.println("mango 존재? : " + memberChecks.get(1));    // false
			System.out.println("banana 존재? : " + memberChecks.get(2));   // true

			// 6. srem
			// srem 명령어는 Set 에서 특정 멤버를 제거합니다.
			// 반환값은 실제로 제거된 멤버의 수입니다.
			// 존재하지 않는 멤버를 제거하려 해도 에러 없이 0 반환
			System.out.println("=========srem=========");
			Long removedCount = command.srem("fruits", "banana");
			System.out.println("제거된 멤버 수 : " + removedCount);         // 1
			Long notExistRemove = command.srem("fruits", "mango");
			System.out.println("없는 멤버 제거 시도 : " + notExistRemove);  // 0
			System.out.println("제거 후 Set : " + command.smembers("fruits"));

			// 7. spop
			// spop 명령어는 Set 에서 랜덤으로 멤버를 꺼내 제거합니다.
			// Set 이 비어있으면 null 반환
			// count 를 지정하면 여러 개를 한번에 꺼낼 수 있습니다.
			System.out.println("=========spop=========");
			command.sadd("colors", "red", "green", "blue", "yellow", "purple");
			String poppedColor = command.spop("colors");
			System.out.println("랜덤으로 꺼낸 색상 : " + poppedColor);
			System.out.println("spop 후 남은 Set : " + command.smembers("colors"));

			Set<String> multiPopped = command.spop("colors", 2);
			System.out.println("한번에 2개 꺼내기 : " + multiPopped);
			System.out.println("2개 spop 후 남은 Set : " + command.smembers("colors"));

			// 8. srandmember
			// srandmember 명령어는 Set 에서 랜덤으로 멤버를 조회합니다.
			// spop 과 달리 Set 에서 제거하지 않습니다.
			// count 양수: 중복 없이 count 개 반환
			// count 음수: 중복 허용하여 |count| 개 반환
			System.out.println("=========srandmember=========");
			command.sadd("numbers", "1", "2", "3", "4", "5");
			String randomOne = command.srandmember("numbers");
			System.out.println("랜덤 1개 조회 (제거 X) : " + randomOne);
			List<String> randomThree = command.srandmember("numbers", 3);
			System.out.println("랜덤 3개 조회 (중복 없음) : " + randomThree);
			List<String> randomWithDup = command.srandmember("numbers", -5);
			System.out.println("랜덤 5개 조회 (중복 허용) : " + randomWithDup);
			System.out.println("srandmember 후 Set 크기 변화 없음 : " + command.scard("numbers")); // 5

			// =========================================================
			// 집합 연산
			// =========================================================

			// 데이터 준비
			command.sadd("team:backend", "Alice", "Bob", "Charlie", "Dave");
			command.sadd("team:frontend", "Charlie", "Dave", "Eve", "Frank");

			// 9. sunion (합집합)
			// sunion 명령어는 여러 Set 의 합집합을 반환합니다.
			// 중복 멤버는 한번만 포함됩니다.
			System.out.println("=========sunion (합집합)=========");
			Set<String> union = command.sunion("team:backend", "team:frontend");
			System.out.println("백엔드 + 프론트엔드 전체 인원 : " + union);

			// 10. sinter (교집합)
			// sinter 명령어는 여러 Set 의 교집합을 반환합니다.
			// 모든 Set 에 공통으로 존재하는 멤버만 반환합니다.
			System.out.println("=========sinter (교집합)=========");
			Set<String> inter = command.sinter("team:backend", "team:frontend");
			System.out.println("백엔드 & 프론트엔드 동시 소속 : " + inter); // Charlie, Dave

			// 11. sdiff (차집합)
			// sdiff 명령어는 첫 번째 Set 기준으로 나머지 Set 에 없는 멤버를 반환합니다.
			// sdiff A B → A 에 있고 B 에 없는 것
			System.out.println("=========sdiff (차집합)=========");
			Set<String> diffBackend = command.sdiff("team:backend", "team:frontend");
			System.out.println("백엔드 전용 (프론트엔드에 없는) : " + diffBackend); // Alice, Bob
			Set<String> diffFrontend = command.sdiff("team:frontend", "team:backend");
			System.out.println("프론트엔드 전용 (백엔드에 없는) : " + diffFrontend); // Eve, Frank

			// 12. sunionstore (합집합 결과 저장)
			// sunionstore 명령어는 합집합 결과를 새로운 키에 저장합니다.
			// 반환값은 저장된 Set 의 크기
			System.out.println("=========sunionstore=========");
			Long unionSize = command.sunionstore("team:all", "team:backend", "team:frontend");
			System.out.println("합집합 저장 완료, 크기 : " + unionSize);
			System.out.println("team:all : " + command.smembers("team:all"));

			// 13. sinterstore (교집합 결과 저장)
			// sinterstore 명령어는 교집합 결과를 새로운 키에 저장합니다.
			System.out.println("=========sinterstore=========");
			Long interSize = command.sinterstore("team:both", "team:backend", "team:frontend");
			System.out.println("교집합 저장 완료, 크기 : " + interSize);
			System.out.println("team:both : " + command.smembers("team:both"));

			// 14. sdiffstore (차집합 결과 저장)
			// sdiffstore 명령어는 차집합 결과를 새로운 키에 저장합니다.
			System.out.println("=========sdiffstore=========");
			Long diffSize = command.sdiffstore("team:backend:only", "team:backend", "team:frontend");
			System.out.println("차집합 저장 완료, 크기 : " + diffSize);
			System.out.println("team:backend:only : " + command.smembers("team:backend:only"));

			// 15. smove
			// smove 명령어는 한 Set 에서 다른 Set 으로 멤버를 이동합니다.
			// 원자적으로 처리됩니다. (source 에서 제거 + destination 에 추가)
			// 이동 성공 시 true, 멤버가 source 에 없으면 false 반환
			System.out.println("=========smove=========");
			// Alice 를 백엔드에서 프론트엔드로 이동
			Boolean moved = command.smove("team:backend", "team:frontend", "Alice");
			System.out.println("Alice 이동 성공? : " + moved);             // true
			System.out.println("이동 후 백엔드 : " + command.smembers("team:backend"));
			System.out.println("이동 후 프론트엔드 : " + command.smembers("team:frontend"));

			Boolean notMoved = command.smove("team:backend", "team:frontend", "notExistMember");
			System.out.println("없는 멤버 이동 시도 : " + notMoved);       // false
		}
	}
}
