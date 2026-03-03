package com.baekgwa.example.pubsub;

import com.baekgwa.connect.RedisConnect;

import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;

/**
 * PackageName : com.baekgwa.example.pubsub
 * FileName    : PubSubSubscriber
 * Author      : Baekgwa
 * Date        : 26. 3. 3.
 * Description :
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2026-03-03     Baekgwa               Initial creation
 **/
// Redis Pub/Sub - 구독자(Subscriber)
// subscribe 명령으로 채널을 구독하면 해당 채널에 발행된 메시지를 실시간으로 수신
// 구독 상태에서는 subscribe/unsubscribe/ping 외 일반 명령 불가 → 별도 커넥션 사용
public class PubSubSubscriber {

	public static StatefulRedisPubSubConnection<String, String> start(RedisConnect redisConnect) {
		StatefulRedisPubSubConnection<String, String> connection = redisConnect.getPubSubConnection();

		// 리스너 등록
		// RedisPubSubAdapter: 필요한 메서드만 오버라이드 가능한 어댑터 클래스
		connection.addListener(new RedisPubSubAdapter<>() {

			// 채널 구독 완료 시 호출
			@Override
			public void subscribed(String channel, long count) {
				System.out.println("[Subscriber] 구독 완료 - 채널: " + channel + ", 현재 구독 채널 수: " + count);
			}

			// 채널 구독 해제 시 호출
			@Override
			public void unsubscribed(String channel, long count) {
				System.out.println("[Subscriber] 구독 해제 - 채널: " + channel + ", 남은 구독 채널 수: " + count);
			}

			// 구독 중인 채널에서 메시지 수신 시 호출
			@Override
			public void message(String channel, String message) {
				System.out.println("[Subscriber] 메시지 수신 - 채널: " + channel + " | 내용: " + message);
			}
		});

		RedisPubSubCommands<String, String> commands = connection.sync();

		// 1. subscribe
		// subscribe 명령어는 하나 이상의 채널을 구독합니다.
		// 구독 즉시 해당 채널에 발행된 메시지를 수신하기 시작합니다.
		// 반환값 없음 - 메시지는 리스너(addListener)를 통해 비동기로 수신됩니다.
		System.out.println("=========subscribe=========");
		commands.subscribe("news", "sports", "tech");
		System.out.println("[Subscriber] news, sports, tech 채널 구독 요청 완료");

		return connection;
	}
}
