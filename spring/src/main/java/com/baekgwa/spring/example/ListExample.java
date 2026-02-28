package com.baekgwa.spring.example;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * PackageName : com.baekgwa.spring.example
 * FileName    : ListExample
 * Author      : Baekgwa
 * Date        : 26. 3. 1.
 * Description :
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2026-03-01     Baekgwa               Initial creation
 **/
// Redis List 는 Doubly Linked List 구조
// 양 끝(Left/Right) 에서 O(1) 으로 삽입/삭제 가능
// 인덱스 기반 접근은 O(N)
// Queue(큐): rpush → lpop  (오른쪽에 넣고, 왼쪽에서 꺼내기)
// Stack(스택): lpush → lpop  (왼쪽에 넣고, 왼쪽에서 꺼내기)
@Component
public class ListExample implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(ListExample.class);

	private final RedisTemplate<String, String> redisTemplate;

	public ListExample(RedisTemplate<String, String> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void run(String... args) {
		// ListOperations: List 타입의 key-value 명령을 담당하는 객체
		// Java 순수 Lettuce의 RedisCommands에서 List 관련 명령만 추출한 것과 유사합니다.
		ListOperations<String, String> listOps = redisTemplate.opsForList();

		// 0. 안의 내용들 모두 초기화.
		// Spring Data Redis에는 flushDb()를 직접 호출하는 메서드가 없습니다.
		// RedisCallback을 통해 low-level connection에 직접 접근하여 실행합니다.
		// Java 순수 Lettuce: command.flushdb()
		redisTemplate.execute((RedisCallback<Object>) connection -> {
			connection.serverCommands().flushAll();
			return null;
		});

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
		// [차이점] Java 순수 Lettuce: command.rpush(key, value), command.rpush(key, v1, v2...)
		//                              → 단일/복수 모두 동일한 rpush 메서드 사용
		//          Spring Data Redis: listOps.rightPush(key, value)       → 단일 값 추가
		//                             listOps.rightPushAll(key, v1, v2...) → 복수 값 추가
		//            → 단일과 복수 삽입 메서드가 분리되어 있습니다.
		log.info("=========rpush=========");
		Long rpushCount = listOps.rightPush("queue", "task1");
		log.info("rpush 후 리스트 길이 : {}", rpushCount);
		Long rpushCount2 = listOps.rightPushAll("queue", "task2", "task3");
		log.info("rpush 2개 후 리스트 길이 : {}", rpushCount2);
		// 현재 상태: [task1, task2, task3]

		// 2. lrange
		// lrange 명령어는 리스트의 특정 범위를 조회합니다.
		// lrange key start stop
		// 인덱스는 0부터 시작하며, -1 은 마지막 요소를 의미합니다.
		// -1 을 사용하면 끝까지 조회합니다.
		//
		// [차이점] Java 순수 Lettuce: command.lrange(key, start, stop)
		//          Spring Data Redis: listOps.range(key, start, stop)
		//            → 메서드명이 lrange 에서 range 로 단축됩니다.
		log.info("=========lrange=========");
		List<String> queueList = listOps.range("queue", 0, -1);
		log.info("전체 조회 : {}", queueList);
		List<String> partialList = listOps.range("queue", 0, 1);
		log.info("0~1 범위 조회 : {}", partialList);

		// 3. lpop (Queue 의 dequeue)
		// lpop 명령어는 리스트의 왼쪽(head) 에서 값을 꺼냅니다.
		// rpush 로 오른쪽에 넣고, lpop 으로 왼쪽에서 꺼내면 → FIFO 구조
		// 반환값은 꺼낸 값이며, 리스트가 비어있으면 null 반환
		//
		// [차이점 없음] 두 방식 모두 String 또는 null 반환
		// Java 순수 Lettuce: command.lpop(key)
		// Spring Data Redis: listOps.leftPop(key)
		log.info("=========lpop (Queue dequeue)=========");
		String firstTask = listOps.leftPop("queue");
		log.info("꺼낸 작업 : {}", firstTask);          // task1
		log.info("남은 큐 : {}", listOps.range("queue", 0, -1));
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
		// [차이점] Java 순수 Lettuce: command.lpush(key, value), command.lpush(key, v1, v2...)
		//                              → 단일/복수 모두 동일한 lpush 메서드 사용
		//          Spring Data Redis: listOps.leftPush(key, value)       → 단일 값 추가
		//                             listOps.leftPushAll(key, v1, v2...) → 복수 값 추가
		//            → 단일과 복수 삽입 메서드가 분리되어 있습니다.
		log.info("=========lpush=========");
		redisTemplate.delete("stack");
		Long lpushCount = listOps.leftPush("stack", "page1");
		log.info("lpush 후 리스트 길이 : {}", lpushCount);
		Long lpushCount2 = listOps.leftPushAll("stack", "page2", "page3");
		log.info("lpush 2개 후 리스트 길이 : {}", lpushCount2);
		log.info("스택 상태 : {}", listOps.range("stack", 0, -1));
		// 현재 상태: [page3, page2, page1]

		// 5. lpop (Stack 의 pop)
		// lpush 로 왼쪽에 넣고, lpop 으로 왼쪽에서 꺼내면 → LIFO 구조
		log.info("=========lpop (Stack pop)=========");
		String topPage = listOps.leftPop("stack");
		log.info("꺼낸 페이지 (가장 최근) : {}", topPage);   // page3
		log.info("남은 스택 : {}", listOps.range("stack", 0, -1));

		// 6. rpop
		// rpop 명령어는 리스트의 오른쪽(tail) 에서 값을 꺼냅니다.
		// 리스트가 비어있으면 null 반환
		//
		// [차이점 없음] 두 방식 모두 String 또는 null 반환
		// Java 순수 Lettuce: command.rpop(key)
		// Spring Data Redis: listOps.rightPop(key)
		log.info("=========rpop=========");
		listOps.rightPushAll("mylist", "a", "b", "c");
		String lastItem = listOps.rightPop("mylist");
		log.info("오른쪽에서 꺼낸 값 : {}", lastItem);       // c
		log.info("남은 리스트 : {}", listOps.range("mylist", 0, -1));

		// 7. llen
		// llen 명령어는 리스트의 길이를 반환합니다.
		// 키가 없으면 0 반환
		//
		// [차이점] Java 순수 Lettuce: command.llen(key)
		//          Spring Data Redis: listOps.size(key)
		//            → 메서드명이 llen 에서 size 로 변경됩니다.
		log.info("=========llen=========");
		Long listLen = listOps.size("mylist");
		log.info("mylist 길이 : {}", listLen);
		Long notExistLen = listOps.size("notExistKey");
		log.info("없는 키의 길이 : {}", notExistLen);

		// 8. lindex
		// lindex 명령어는 특정 인덱스의 값을 조회합니다.
		// 인덱스는 0부터 시작하며, -1 은 마지막 요소
		// 범위를 벗어나면 null 반환
		//
		// [차이점 없음] 두 방식 모두 String 또는 null 반환
		// Java 순수 Lettuce: command.lindex(key, index)
		// Spring Data Redis: listOps.index(key, index)
		log.info("=========lindex=========");
		// 현재 mylist: [a, b]
		String firstItem = listOps.index("mylist", 0);
		log.info("0번 인덱스 : {}", firstItem);            // a
		String lastIndex = listOps.index("mylist", -1);
		log.info("-1번 인덱스(마지막) : {}", lastIndex);   // b
		String outOfRange = listOps.index("mylist", 99);
		log.info("범위 초과 인덱스 : {}", outOfRange);     // null

		// 9. linsert
		// linsert 명령어는 특정 값의 앞(BEFORE) 또는 뒤(AFTER) 에 새 값을 삽입합니다.
		// 반환값은 삽입 후 리스트 길이
		// 기준 값이 없으면 -1 반환
		//
		// [차이점] Java 순수 Lettuce: command.linsert(key, true, pivot, value)  → true = BEFORE
		//                             command.linsert(key, false, pivot, value) → false = AFTER
		//          Spring Data Redis: listOps.leftPush(key, pivot, value)   → pivot 앞에 삽입 (BEFORE)
		//                             listOps.rightPush(key, pivot, value)  → pivot 뒤에 삽입 (AFTER)
		//            → linsert 메서드가 없으며, leftPush / rightPush 의 3인자 버전으로 대체합니다.
		//            → 메서드명의 left/right 는 리스트 방향이 아닌 pivot 기준 삽입 방향입니다.
		log.info("=========linsert=========");
		// 현재 mylist: [a, b]
		Long afterInsert = listOps.leftPush("mylist", "b", "b_before");
		log.info("b 앞에 b_before 삽입 후 길이 : {}", afterInsert);
		log.info("삽입 후 리스트 : {}", listOps.range("mylist", 0, -1));
		// 결과: [a, b_before, b]

		// 10. lrem
		// lrem 명령어는 리스트에서 특정 값을 count 만큼 제거합니다.
		// count > 0: head 에서 tail 방향으로 count 개 제거
		// count < 0: tail 에서 head 방향으로 |count| 개 제거
		// count = 0: 해당 값을 모두 제거
		// 반환값은 실제로 제거된 개수
		//
		// [차이점] Java 순수 Lettuce: command.lrem(key, count, value)
		//          Spring Data Redis: listOps.remove(key, count, value)
		//            → 메서드명이 lrem 에서 remove 로 변경됩니다.
		log.info("=========lrem=========");
		redisTemplate.delete("remlist");
		listOps.rightPushAll("remlist", "x", "y", "x", "z", "x");
		// 현재: [x, y, x, z, x]
		Long removed = listOps.remove("remlist", 2, "x");
		log.info("'x' 2개 제거 완료, 제거된 개수 : {}", removed);
		log.info("제거 후 리스트 : {}", listOps.range("remlist", 0, -1));
		// 결과: [y, z, x] (앞에서 2개 제거)

		// =========================================================
		// Blocking POP
		// 리스트가 비어있을 때 지정한 시간(초) 만큼 대기하다가
		// 값이 들어오면 즉시 꺼내는 명령어
		// timeout = 0 이면 무한 대기
		// 실시간 작업 큐, 이벤트 드리븐 처리에 활용
		//
		// [차이점] Java 순수 Lettuce: command.blpop(timeout, key)  → KeyValue<String, String> 반환
		//                               → .getKey(), .getValue() 로 접근
		//          Spring Data Redis: listOps.leftPop(key, Duration) → String | null 반환
		//                               → 키 정보 없이 값만 반환됩니다.
		//                               → 인자 순서 반대: Lettuce 는 timeout 먼저, Spring 은 key 먼저
		//                               → 별도 Connection 불필요: Lettuce Connection Pool 에서
		//                                 자동으로 다른 커넥션이 할당됩니다.
		// =========================================================

		// 11. blpop - 데이터가 있을 때 (즉시 반환)
		// blpop 명령어는 리스트 왼쪽에서 Blocking 방식으로 값을 꺼냅니다.
		// 데이터가 이미 있으면 즉시 반환
		log.info("=========blpop (데이터 있을 때)=========");
		redisTemplate.delete("blqueue");
		listOps.rightPushAll("blqueue", "job1", "job2");
		String blpopResult = listOps.leftPop("blqueue", Duration.ofSeconds(3));
		log.info("blpop 결과 : {}", blpopResult);

		// 12. blpop - 빈 리스트에서 대기 후 반환
		// 별도 스레드에서 1초 후 데이터를 push → blpop 이 감지하여 꺼냄
		// Spring Data Redis 는 Lettuce Connection Pool 을 사용하므로
		// 동일한 redisTemplate 을 별도 스레드에서 사용해도 다른 커넥션이 할당됩니다.
		log.info("=========blpop (빈 리스트 대기)=========");
		redisTemplate.delete("blqueue");

		// 생산자 스레드: 1초 후 데이터 삽입 (Connection Pool 에서 별도 커넥션 사용)
		Thread producer = new Thread(() -> {
			try {
				Thread.sleep(1000);
				redisTemplate.opsForList().rightPush("blqueue", "delayed_job");
				log.info("[생산자] delayed_job 삽입 완료");
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		producer.start();

		// 소비자: 최대 5초 대기
		log.info("[소비자] blqueue 에서 데이터 대기 중... (최대 5초)");
		String delayedResult = listOps.leftPop("blqueue", Duration.ofSeconds(5));
		if (delayedResult != null) {
			log.info("[소비자] blpop 수신 : {}", delayedResult);
		} else {
			log.info("[소비자] timeout - 데이터 없음");
		}

		try {
			producer.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		// 13. blpop - timeout 초과 시 null 반환
		log.info("=========blpop (timeout 초과)=========");
		redisTemplate.delete("emptyQueue");
		log.info("[소비자] emptyQueue 에서 대기 중... (최대 2초)");
		String timeoutResult = listOps.leftPop("emptyQueue", Duration.ofSeconds(2));
		if (timeoutResult != null) {
			log.info("수신 : {}", timeoutResult);
		} else {
			log.info("timeout 발생 → null 반환");
		}

		// 14. brpop - 오른쪽에서 Blocking POP
		// blpop 과 동일하게 동작하지만, 오른쪽(tail) 에서 꺼냅니다.
		//
		// [차이점] Java 순수 Lettuce: command.brpop(timeout, key)  → KeyValue<String, String> 반환
		//          Spring Data Redis: listOps.rightPop(key, Duration) → String | null 반환
		log.info("=========brpop=========");
		redisTemplate.delete("brqueue");
		listOps.rightPushAll("brqueue", "item1", "item2", "item3");
		String brpopResult = listOps.rightPop("brqueue", Duration.ofSeconds(3));
		log.info("brpop 결과 : {}", brpopResult);
		// item3 (오른쪽에서 꺼냄)
	}
}
