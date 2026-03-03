package com.baekgwa.example.pubsub;

import com.baekgwa.connect.RedisConnect;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * PackageName : com.baekgwa.example.pubsub
 * FileName    : PubSubPublisher
 * Author      : Baekgwa
 * Date        : 26. 3. 3.
 * Description :
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2026-03-03     Baekgwa               Initial creation
 **/
// Redis Pub/Sub - 발행자(Publisher)
// publish 명령으로 채널에 메시지를 발행
// 발행자는 일반 커넥션을 사용하며, 구독자가 없어도 에러 없이 동작 (수신자 수 = 0)
public class PubSubPublisher {

	public static void start(RedisConnect redisConnect) throws InterruptedException {
		try (StatefulRedisConnection<String, String> connection = redisConnect.getConnection()) {
			RedisCommands<String, String> command = connection.sync();

			// 1. publish - 단일 채널에 메시지 발행
			// publish 명령어는 지정한 채널에 메시지를 발행합니다.
			// 반환값은 해당 메시지를 수신한 구독자 수입니다.
			// 구독자가 없으면 0 반환 (에러 아님)
			System.out.println("=========publish=========");
			Long receivers1 = command.publish("news", "Breaking: Redis 8.0 Released!");
			System.out.println("[Publisher] news 채널 발행 - 수신자 수: " + receivers1);

			Thread.sleep(100); // 구독자 수신 대기

			Long receivers2 = command.publish("sports", "FIFA World Cup 2026 결승전 한국 우승!");
			System.out.println("[Publisher] sports 채널 발행 - 수신자 수: " + receivers2);

			Thread.sleep(100);

			Long receivers3 = command.publish("tech", "Java 25 LTS 출시 예정");
			System.out.println("[Publisher] tech 채널 발행 - 수신자 수: " + receivers3);

			Thread.sleep(100);

			// 2. publish - 구독자 없는 채널에 발행
			// 구독자가 없는 채널에 발행해도 에러 없이 수신자 수 0을 반환합니다.
			System.out.println("=========구독자 없는 채널 발행=========");
			Long receivers4 = command.publish("unsubscribed-channel", "아무도 듣지 않는 메시지");
			System.out.println("[Publisher] unsubscribed-channel 발행 - 수신자 수: " + receivers4); // 0

			Thread.sleep(100);

			// 3. publish - 동일 채널에 여러 번 발행
			System.out.println("=========연속 발행=========");
			for (int i = 1; i <= 3; i++) {
				Long receivers = command.publish("news", "뉴스 속보 #" + i);
				System.out.println("[Publisher] news 채널 " + i + "번째 발행 - 수신자 수: " + receivers);
				Thread.sleep(100);
			}
		}
	}
}
