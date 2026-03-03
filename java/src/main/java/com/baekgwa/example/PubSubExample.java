package com.baekgwa.example;

import com.baekgwa.connect.RedisConnect;
import com.baekgwa.example.pubsub.PubSubPublisher;
import com.baekgwa.example.pubsub.PubSubSubscriber;

import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

/**
 * PackageName : com.baekgwa.example
 * FileName    : PubSubExample
 * Author      : Baekgwa
 * Date        : 26. 3. 3.
 * Description :
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2026-03-03     Baekgwa               Initial creation
 **/
// Redis Pub/Sub 예제
// Subscriber 와 Publisher 는 각각 별도 커넥션을 사용해야 합니다.
// 구독 상태의 커넥션에서는 일반 명령(GET, SET 등)을 실행할 수 없습니다.
public class PubSubExample {

	public static void start() throws InterruptedException {
		RedisConnect redisConnect = new RedisConnect();

		try {
			// 구독자 먼저 시작 (채널 구독 완료 후 발행자 실행)
			StatefulRedisPubSubConnection<String, String> subConn = PubSubSubscriber.start(redisConnect);
			Thread.sleep(200); // 구독 설정 완료 대기

			// 발행자 실행
			PubSubPublisher.start(redisConnect);
			Thread.sleep(300); // 마지막 메시지 수신 대기

			// 2. unsubscribe - 채널 구독 해제
			// unsubscribe 명령어는 지정한 채널의 구독을 해제합니다.
			// 인자 없이 호출하면 모든 채널 구독 해제
			System.out.println("=========unsubscribe=========");
			subConn.sync().unsubscribe("news", "sports", "tech");
			Thread.sleep(100);

			subConn.close();
		} finally {
			redisConnect.shutdown();
		}
	}
}
