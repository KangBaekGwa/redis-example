package com.baekgwa;

import java.util.List;

import com.baekgwa.connect.RedisConnect;

import io.lettuce.core.KeyValue;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * PackageName : com.baekgwa
 * FileName    : ListExample
 * Author      : Baekgwa
 * Date        : 26. 2. 28.
 * Description :
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2026-02-28     Baekgwa               Initial creation
 **/
// Redis List 는 Doubly Linked List 구조
// 양 끝(Left/Right) 에서 O(1) 으로 삽입/삭제 가능
// 인덱스 기반 접근은 O(N)
// Queue(큐): rpush → lpop  (오른쪽에 넣고, 왼쪽에서 꺼내기)
// Stack(스택): lpush → lpop  (왼쪽에 넣고, 왼쪽에서 꺼내기)
public class ListExample {

	public static void start() {
		RedisConnect redisConnect = new RedisConnect();

		try (StatefulRedisConnection<String, String> connection = redisConnect.getConnection()) {
			RedisCommands<String, String> command = connection.sync();

			// 0. 안의 내용들 모두 초기화.
			command.flushdb();

			// =========================================================
			// Queue 처럼 사용하기 (FIFO: First In, First Out)
			// rpush → lpop
			// =========================================================

			// 1. rpush
			// rpush 명령어는 리스트의 오른쪽(tail) 에 값을 추가합니다.
			// 반환값은 명령어를 수행한 후 리스트의 길이입니다.
			// 키가 없으면 새로운 리스트를 만들어 추가합니다.
			// 여러 값을 한번에 추가할 수 있으며, 왼쪽에서 오른쪽 순서로 쌓입니다.
			System.out.println("=========rpush=========");
			Long rpushCount = command.rpush("queue", "task1");
			System.out.println("rpush 후 리스트 길이 : " + rpushCount);
			rpushCount = command.rpush("queue", "task2", "task3");
			System.out.println("rpush 2개 후 리스트 길이 : " + rpushCount);
			// 현재 상태: [task1, task2, task3]

			// 2. lrange
			// lrange 명령어는 리스트의 특정 범위를 조회합니다.
			// lrange key start stop
			// 인덱스는 0부터 시작하며, -1 은 마지막 요소를 의미합니다.
			// -1 을 사용하면 끝까지 조회합니다.
			System.out.println("=========lrange=========");
			List<String> queueList = command.lrange("queue", 0, -1);
			System.out.println("전체 조회 : " + queueList);
			List<String> partialList = command.lrange("queue", 0, 1);
			System.out.println("0~1 범위 조회 : " + partialList);

			// 3. lpop (Queue 의 dequeue)
			// lpop 명령어는 리스트의 왼쪽(head) 에서 값을 꺼냅니다.
			// rpush 로 오른쪽에 넣고, lpop 으로 왼쪽에서 꺼내면 → FIFO 구조
			// 반환값은 꺼낸 값이며, 리스트가 비어있으면 null 반환
			System.out.println("=========lpop (Queue dequeue)=========");
			String firstTask = command.lpop("queue");
			System.out.println("꺼낸 작업 : " + firstTask);          // task1
			System.out.println("남은 큐 : " + command.lrange("queue", 0, -1));
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
			System.out.println("=========lpush=========");
			command.del("stack");
			Long lpushCount = command.lpush("stack", "page1");
			System.out.println("lpush 후 리스트 길이 : " + lpushCount);
			lpushCount = command.lpush("stack", "page2", "page3");
			System.out.println("lpush 2개 후 리스트 길이 : " + lpushCount);
			System.out.println("스택 상태 : " + command.lrange("stack", 0, -1));
			// 현재 상태: [page3, page2, page1]

			// 5. lpop (Stack 의 pop)
			// lpush 로 왼쪽에 넣고, lpop 으로 왼쪽에서 꺼내면 → LIFO 구조
			System.out.println("=========lpop (Stack pop)=========");
			String topPage = command.lpop("stack");
			System.out.println("꺼낸 페이지 (가장 최근) : " + topPage);   // page3
			System.out.println("남은 스택 : " + command.lrange("stack", 0, -1));

			// 6. rpop
			// rpop 명령어는 리스트의 오른쪽(tail) 에서 값을 꺼냅니다.
			// 리스트가 비어있으면 null 반환
			System.out.println("=========rpop=========");
			command.rpush("mylist", "a", "b", "c");
			String lastItem = command.rpop("mylist");
			System.out.println("오른쪽에서 꺼낸 값 : " + lastItem);       // c
			System.out.println("남은 리스트 : " + command.lrange("mylist", 0, -1));

			// 7. llen
			// llen 명령어는 리스트의 길이를 반환합니다.
			// 키가 없으면 0 반환
			System.out.println("=========llen=========");
			Long listLen = command.llen("mylist");
			System.out.println("mylist 길이 : " + listLen);
			Long notExistLen = command.llen("notExistKey");
			System.out.println("없는 키의 길이 : " + notExistLen);

			// 8. lindex
			// lindex 명령어는 특정 인덱스의 값을 조회합니다.
			// 인덱스는 0부터 시작하며, -1 은 마지막 요소
			// 범위를 벗어나면 null 반환
			System.out.println("=========lindex=========");
			// 현재 mylist: [a, b]
			String firstItem = command.lindex("mylist", 0);
			System.out.println("0번 인덱스 : " + firstItem);            // a
			String lastIndex = command.lindex("mylist", -1);
			System.out.println("-1번 인덱스(마지막) : " + lastIndex);   // b
			String outOfRange = command.lindex("mylist", 99);
			System.out.println("범위 초과 인덱스 : " + outOfRange);     // null

			// 9. linsert
			// linsert 명령어는 특정 값의 앞(BEFORE) 또는 뒤(AFTER) 에 새 값을 삽입합니다.
			// 반환값은 삽입 후 리스트 길이
			// 기준 값이 없으면 -1 반환
			System.out.println("=========linsert=========");
			// 현재 mylist: [a, b]
			Long afterInsert = command.linsert("mylist", true, "b", "b_before");
			System.out.println("b 앞에 b_before 삽입 후 길이 : " + afterInsert);
			System.out.println("삽입 후 리스트 : " + command.lrange("mylist", 0, -1));
			// 결과: [a, b_before, b]

			// 10. lrem
			// lrem 명령어는 리스트에서 특정 값을 count 만큼 제거합니다.
			// count > 0: head 에서 tail 방향으로 count 개 제거
			// count < 0: tail 에서 head 방향으로 |count| 개 제거
			// count = 0: 해당 값을 모두 제거
			// 반환값은 실제로 제거된 개수
			System.out.println("=========lrem=========");
			command.del("remlist");
			command.rpush("remlist", "x", "y", "x", "z", "x");
			// 현재: [x, y, x, z, x]
			Long removed = command.lrem("remlist", 2, "x");
			System.out.println("'x' 2개 제거 완료, 제거된 개수 : " + removed);
			System.out.println("제거 후 리스트 : " + command.lrange("remlist", 0, -1));
			// 결과: [y, z, x] (앞에서 2개 제거)

			// =========================================================
			// Blocking POP
			// 리스트가 비어있을 때 지정한 시간(초) 만큼 대기하다가
			// 값이 들어오면 즉시 꺼내는 명령어
			// timeout = 0 이면 무한 대기
			// 실시간 작업 큐, 이벤트 드리븐 처리에 활용
			// 주의: Blocking 명령어는 해당 Connection 을 점유하므로
			//       별도 Connection 을 사용해야 합니다.
			// =========================================================

			// 11. blpop - 데이터가 있을 때 (즉시 반환)
			// blpop 명령어는 리스트 왼쪽에서 Blocking 방식으로 값을 꺼냅니다.
			// 데이터가 이미 있으면 즉시 반환
			// 반환값은 KeyValue<키, 값> 형태 (어느 키에서 꺼냈는지 포함)
			System.out.println("=========blpop (데이터 있을 때)=========");
			command.del("blqueue");
			command.rpush("blqueue", "job1", "job2");
			KeyValue<String, String> blpopResult = command.blpop(3, "blqueue");
			System.out.println("blpop 결과 - 키 : " + blpopResult.getKey() + ", 값 : " + blpopResult.getValue());

			// 12. blpop - 빈 리스트에서 대기 후 반환
			// 별도 스레드에서 1초 후 데이터를 push → blpop 이 감지하여 꺼냄
			System.out.println("=========blpop (빈 리스트 대기)=========");
			command.del("blqueue");

			// 생산자 스레드: 1초 후 데이터 삽입 (별도 Connection 사용)
			Thread producer = new Thread(() -> {
				try (StatefulRedisConnection<String, String> producerConn = redisConnect.getConnection()) {
					RedisCommands<String, String> producerCmd = producerConn.sync();
					Thread.sleep(1000);
					producerCmd.rpush("blqueue", "delayed_job");
					System.out.println("[생산자] delayed_job 삽입 완료");
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
			producer.start();

			// 소비자: 최대 5초 대기
			System.out.println("[소비자] blqueue 에서 데이터 대기 중... (최대 5초)");
			KeyValue<String, String> delayedResult = command.blpop(5, "blqueue");
			if (delayedResult != null) {
				System.out.println("[소비자] blpop 수신 - 키 : " + delayedResult.getKey() + ", 값 : " + delayedResult.getValue());
			} else {
				System.out.println("[소비자] timeout - 데이터 없음");
			}

			// producer 스레드가 끝날 때까지 대기
			try {
				producer.join();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}

			// 13. blpop - timeout 초과 시 null 반환
			System.out.println("=========blpop (timeout 초과)=========");
			command.del("emptyQueue");
			System.out.println("[소비자] emptyQueue 에서 대기 중... (최대 2초)");
			KeyValue<String, String> timeoutResult = command.blpop(2, "emptyQueue");
			if (timeoutResult != null) {
				System.out.println("수신 : " + timeoutResult.getValue());
			} else {
				System.out.println("timeout 발생 → null 반환");
			}

			// 14. brpop - 오른쪽에서 Blocking POP
			// blpop 과 동일하게 동작하지만, 오른쪽(tail) 에서 꺼냅니다.
			System.out.println("=========brpop=========");
			command.del("brqueue");
			command.rpush("brqueue", "item1", "item2", "item3");
			KeyValue<String, String> brpopResult = command.brpop(3, "brqueue");
			System.out.println("brpop 결과 - 키 : " + brpopResult.getKey() + ", 값 : " + brpopResult.getValue());
			// item3 (오른쪽에서 꺼냄)
		}
	}
}
