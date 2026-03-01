package com.baekgwa.spring.example;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.stereotype.Component;

/**
 * PackageName : com.baekgwa.spring.example
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
@Component
public class SetExample implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(SetExample.class);

	private final RedisTemplate<String, String> redisTemplate;

	public SetExample(RedisTemplate<String, String> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void run(String... args) {
		// SetOperations: Set 타입의 key-value 명령을 담당하는 객체
		// Java 순수 Lettuce의 RedisCommands에서 Set 관련 명령만 추출한 것과 유사합니다.
		SetOperations<String, String> setOps = redisTemplate.opsForSet();

		// 0. 안의 내용들 모두 초기화.
		// Spring Data Redis에는 flushDb()를 직접 호출하는 메서드가 없습니다.
		// RedisCallback을 통해 low-level connection에 직접 접근하여 실행합니다.
		// Java 순수 Lettuce: command.flushdb()
		redisTemplate.execute((RedisCallback<Object>) connection -> {
			connection.serverCommands().flushAll();
			return null;
		});

		// 1. sadd
		// sadd 명령어는 Set 에 멤버를 추가합니다.
		// 반환값은 실제로 추가된 멤버의 수입니다. (이미 존재하면 추가 안 됨)
		// 중복 값은 무시됩니다.
		//
		// [차이점] Java 순수 Lettuce: command.sadd(key, members...)
		//          Spring Data Redis: setOps.add(key, members...)
		//            → 메서드명이 sadd 에서 add 로 변경됩니다.
		log.info("=========sadd=========");
		Long addedCount = setOps.add("fruits", "apple", "banana", "orange");
		log.info("추가된 멤버 수 : {}", addedCount);          // 3
		Long duplicateCount = setOps.add("fruits", "apple", "grape");
		log.info("중복 포함 추가 시 실제 추가 수 : {}", duplicateCount); // 1 (apple 은 중복)

		// 2. smembers
		// smembers 명령어는 Set 의 모든 멤버를 반환합니다.
		// 반환 순서는 보장되지 않습니다. (Set 특성)
		// 키가 없으면 빈 Set 반환
		//
		// [차이점 없음] 두 방식 모두 Set<String> 반환 (ioredis 와 달리 Set 자료구조 유지)
		// Java 순수 Lettuce: command.smembers(key)
		// Spring Data Redis: setOps.members(key)
		//   → 메서드명이 smembers 에서 members 로 변경됩니다.
		log.info("=========smembers=========");
		Set<String> allFruits = setOps.members("fruits");
		log.info("모든 과일 : {}", allFruits);
		Set<String> notExist = setOps.members("notExistKey");
		log.info("없는 키 smembers : {}", notExist);          // []

		// 3. scard
		// scard 명령어는 Set 의 크기(멤버 수)를 반환합니다.
		// 키가 없으면 0 반환
		//
		// [차이점] Java 순수 Lettuce: command.scard(key)
		//          Spring Data Redis: setOps.size(key)
		//            → 메서드명이 scard 에서 size 로 변경됩니다. (llen → size 와 동일한 패턴)
		log.info("=========scard=========");
		Long fruitCount = setOps.size("fruits");
		log.info("과일 Set 크기 : {}", fruitCount);           // 4
		Long notExistCard = setOps.size("notExistKey");
		log.info("없는 키 scard : {}", notExistCard);         // 0

		// 4. sismember
		// sismember 명령어는 특정 멤버가 Set 에 존재하는지 확인합니다.
		// 존재하면 true, 없으면 false 반환
		//
		// [차이점 없음] 두 방식 모두 Boolean 반환 (ioredis 는 0/1 정수 반환)
		// Java 순수 Lettuce: command.sismember(key, member)
		// Spring Data Redis: setOps.isMember(key, member)
		//   → 메서드명이 sismember 에서 isMember 로 변경됩니다.
		log.info("=========sismember=========");
		Boolean hasApple = setOps.isMember("fruits", "apple");
		log.info("apple 존재? : {}", hasApple);               // true
		Boolean hasMango = setOps.isMember("fruits", "mango");
		log.info("mango 존재? : {}", hasMango);               // false

		// 5. smismember
		// smismember 명령어는 여러 멤버의 존재 여부를 한번에 확인합니다.
		//
		// [차이점] Java 순수 Lettuce: command.smismember(key, members...) → List<Boolean> 반환
		//                               → 인덱스 기반으로 순서대로 접근
		//          Spring Data Redis: setOps.isMember(key, members...)    → Map<Object, Boolean> 반환
		//                               → 멤버값을 키로 하는 Map 으로 반환됩니다.
		//            → 복수 인자를 isMember 에 넘기면 smismember 로 동작합니다.
		//            → List 인덱스 기반이 아닌 Map.get(member) 로 접근합니다.
		log.info("=========smismember=========");
		Map<Object, Boolean> memberChecks = setOps.isMember("fruits", "apple", "mango", "banana");
		log.info("apple 존재? : {}", memberChecks.get("apple"));   // true
		log.info("mango 존재? : {}", memberChecks.get("mango"));   // false
		log.info("banana 존재? : {}", memberChecks.get("banana")); // true

		// 6. srem
		// srem 명령어는 Set 에서 특정 멤버를 제거합니다.
		// 반환값은 실제로 제거된 멤버의 수입니다.
		// 존재하지 않는 멤버를 제거하려 해도 에러 없이 0 반환
		//
		// [차이점] Java 순수 Lettuce: command.srem(key, members...)
		//          Spring Data Redis: setOps.remove(key, members...)
		//            → 메서드명이 srem 에서 remove 로 변경됩니다.
		log.info("=========srem=========");
		Long removedCount = setOps.remove("fruits", "banana");
		log.info("제거된 멤버 수 : {}", removedCount);         // 1
		Long notExistRemove = setOps.remove("fruits", "mango");
		log.info("없는 멤버 제거 시도 : {}", notExistRemove);  // 0
		log.info("제거 후 Set : {}", setOps.members("fruits"));

		// 7. spop
		// spop 명령어는 Set 에서 랜덤으로 멤버를 꺼내 제거합니다.
		// Set 이 비어있으면 null 반환
		// count 를 지정하면 여러 개를 한번에 꺼낼 수 있습니다.
		//
		// [차이점] Java 순수 Lettuce: command.spop(key)        → String 반환
		//                             command.spop(key, count)  → Set<String> 반환
		//          Spring Data Redis: setOps.pop(key)           → String 반환
		//                             setOps.pop(key, count)    → List<String> 반환
		//            → 메서드명이 spop 에서 pop 으로 변경됩니다.
		//            → 복수 spop 시 Set<String> 대신 List<String> 으로 반환됩니다.
		log.info("=========spop=========");
		setOps.add("colors", "red", "green", "blue", "yellow", "purple");
		String poppedColor = setOps.pop("colors");
		log.info("랜덤으로 꺼낸 색상 : {}", poppedColor);
		log.info("spop 후 남은 Set : {}", setOps.members("colors"));

		List<String> multiPopped = setOps.pop("colors", 2);
		log.info("한번에 2개 꺼내기 : {}", multiPopped);
		log.info("2개 spop 후 남은 Set : {}", setOps.members("colors"));

		// 8. srandmember
		// srandmember 명령어는 Set 에서 랜덤으로 멤버를 조회합니다.
		// spop 과 달리 Set 에서 제거하지 않습니다.
		// count 양수: 중복 없이 count 개 반환
		// count 음수: 중복 허용하여 |count| 개 반환
		//
		// [차이점] Java 순수 Lettuce: command.srandmember(key)         → String 반환
		//                             command.srandmember(key, count)   → List<String> 반환 (양수: 중복 없음)
		//                             command.srandmember(key, -count)  → List<String> 반환 (음수: 중복 허용)
		//          Spring Data Redis: setOps.randomMember(key)                  → String 반환 (단일)
		//                             setOps.distinctRandomMembers(key, count)  → Set<String> 반환 (중복 없음)
		//                             setOps.randomMembers(key, count)          → List<String> 반환 (중복 허용)
		//            → 양수/음수 count 로 구분하던 방식 대신 메서드가 명시적으로 분리됩니다.
		log.info("=========srandmember=========");
		setOps.add("numbers", "1", "2", "3", "4", "5");
		String randomOne = setOps.randomMember("numbers");
		log.info("랜덤 1개 조회 (제거 X) : {}", randomOne);
		Set<String> randomThree = setOps.distinctRandomMembers("numbers", 3);
		log.info("랜덤 3개 조회 (중복 없음) : {}", randomThree);
		List<String> randomWithDup = setOps.randomMembers("numbers", 5);
		log.info("랜덤 5개 조회 (중복 허용) : {}", randomWithDup);
		log.info("srandmember 후 Set 크기 변화 없음 : {}", setOps.size("numbers")); // 5

		// =========================================================
		// 집합 연산
		// =========================================================

		// 데이터 준비
		setOps.add("team:backend", "Alice", "Bob", "Charlie", "Dave");
		setOps.add("team:frontend", "Charlie", "Dave", "Eve", "Frank");

		// 9. sunion (합집합)
		// sunion 명령어는 여러 Set 의 합집합을 반환합니다.
		// 중복 멤버는 한번만 포함됩니다.
		//
		// [차이점 없음] 두 방식 모두 Set<String> 반환
		// Java 순수 Lettuce: command.sunion(key1, key2)
		// Spring Data Redis: setOps.union(key1, key2)
		//   → 메서드명이 sunion 에서 union 으로 변경됩니다.
		log.info("=========sunion (합집합)=========");
		Set<String> union = setOps.union("team:backend", "team:frontend");
		log.info("백엔드 + 프론트엔드 전체 인원 : {}", union);

		// 10. sinter (교집합)
		// sinter 명령어는 여러 Set 의 교집합을 반환합니다.
		// 모든 Set 에 공통으로 존재하는 멤버만 반환합니다.
		//
		// [차이점 없음] 두 방식 모두 Set<String> 반환
		// Java 순수 Lettuce: command.sinter(key1, key2)
		// Spring Data Redis: setOps.intersect(key1, key2)
		//   → 메서드명이 sinter 에서 intersect 로 변경됩니다.
		log.info("=========sinter (교집합)=========");
		Set<String> inter = setOps.intersect("team:backend", "team:frontend");
		log.info("백엔드 & 프론트엔드 동시 소속 : {}", inter); // Charlie, Dave

		// 11. sdiff (차집합)
		// sdiff 명령어는 첫 번째 Set 기준으로 나머지 Set 에 없는 멤버를 반환합니다.
		// sdiff A B → A 에 있고 B 에 없는 것
		//
		// [차이점 없음] 두 방식 모두 Set<String> 반환
		// Java 순수 Lettuce: command.sdiff(key1, key2)
		// Spring Data Redis: setOps.difference(key1, key2)
		//   → 메서드명이 sdiff 에서 difference 로 변경됩니다.
		log.info("=========sdiff (차집합)=========");
		Set<String> diffBackend = setOps.difference("team:backend", "team:frontend");
		log.info("백엔드 전용 (프론트엔드에 없는) : {}", diffBackend); // Alice, Bob
		Set<String> diffFrontend = setOps.difference("team:frontend", "team:backend");
		log.info("프론트엔드 전용 (백엔드에 없는) : {}", diffFrontend); // Eve, Frank

		// 12. sunionstore (합집합 결과 저장)
		// sunionstore 명령어는 합집합 결과를 새로운 키에 저장합니다.
		// 반환값은 저장된 Set 의 크기
		//
		// [차이점] Java 순수 Lettuce: command.sunionstore(destKey, key1, key2) → dest 가 첫 번째 인자
		//          Spring Data Redis: setOps.unionAndStore(key1, key2, destKey) → dest 가 마지막 인자
		//            → 메서드명 변경(sunionstore → unionAndStore) 및 인자 순서가 반대입니다.
		log.info("=========sunionstore=========");
		Long unionSize = setOps.unionAndStore("team:backend", "team:frontend", "team:all");
		log.info("합집합 저장 완료, 크기 : {}", unionSize);
		log.info("team:all : {}", setOps.members("team:all"));

		// 13. sinterstore (교집합 결과 저장)
		// sinterstore 명령어는 교집합 결과를 새로운 키에 저장합니다.
		//
		// [차이점] Java 순수 Lettuce: command.sinterstore(destKey, key1, key2) → dest 가 첫 번째 인자
		//          Spring Data Redis: setOps.intersectAndStore(key1, key2, destKey) → dest 가 마지막 인자
		//            → 메서드명 변경(sinterstore → intersectAndStore) 및 인자 순서가 반대입니다.
		log.info("=========sinterstore=========");
		Long interSize = setOps.intersectAndStore("team:backend", "team:frontend", "team:both");
		log.info("교집합 저장 완료, 크기 : {}", interSize);
		log.info("team:both : {}", setOps.members("team:both"));

		// 14. sdiffstore (차집합 결과 저장)
		// sdiffstore 명령어는 차집합 결과를 새로운 키에 저장합니다.
		//
		// [차이점] Java 순수 Lettuce: command.sdiffstore(destKey, key1, key2) → dest 가 첫 번째 인자
		//          Spring Data Redis: setOps.differenceAndStore(key1, key2, destKey) → dest 가 마지막 인자
		//            → 메서드명 변경(sdiffstore → differenceAndStore) 및 인자 순서가 반대입니다.
		log.info("=========sdiffstore=========");
		Long diffSize = setOps.differenceAndStore("team:backend", "team:frontend", "team:backend:only");
		log.info("차집합 저장 완료, 크기 : {}", diffSize);
		log.info("team:backend:only : {}", setOps.members("team:backend:only"));

		// 15. smove
		// smove 명령어는 한 Set 에서 다른 Set 으로 멤버를 이동합니다.
		// 원자적으로 처리됩니다. (source 에서 제거 + destination 에 추가)
		// 이동 성공 시 true, 멤버가 source 에 없으면 false 반환
		//
		// [차이점] Java 순수 Lettuce: command.smove(source, dest, member)   → Boolean 반환
		//          Spring Data Redis: setOps.move(source, member, dest)      → Boolean 반환
		//            → 메서드명이 smove 에서 move 로 변경됩니다.
		//            → 인자 순서 주의: Lettuce 는 (source, dest, member),
		//                              Spring 은 (source, member, dest) 입니다.
		log.info("=========smove=========");
		// Alice 를 백엔드에서 프론트엔드로 이동
		Boolean moved = setOps.move("team:backend", "Alice", "team:frontend");
		log.info("Alice 이동 성공? : {}", moved);             // true
		log.info("이동 후 백엔드 : {}", setOps.members("team:backend"));
		log.info("이동 후 프론트엔드 : {}", setOps.members("team:frontend"));

		Boolean notMoved = setOps.move("team:backend", "notExistMember", "team:frontend");
		log.info("없는 멤버 이동 시도 : {}", notMoved);       // false
	}
}
