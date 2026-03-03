package com.baekgwa.spring.example.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * PackageName : com.baekgwa.spring.example.pubsub
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
// MessageListener 인터페이스를 구현하여 메시지 수신 로직을 정의합니다.
// Bean 으로 등록하고 PubSubConfig 에서 RedisMessageListenerContainer 에 주입합니다.
//
// [차이점] Java 순수 Lettuce: RedisPubSubAdapter 를 상속하여 message() 메서드 오버라이드
//          Spring Data Redis: MessageListener 인터페이스의 onMessage() 메서드 구현
//            → 채널 구독/해제 이벤트(subscribed/unsubscribed) 콜백은 제공되지 않습니다.
//               구독은 RedisMessageListenerContainer 가 내부적으로 관리합니다.
@Component
public class PubSubSubscriber implements MessageListener {

	private static final Logger log = LoggerFactory.getLogger(PubSubSubscriber.class);

	// 메시지 수신 시 호출되는 콜백 메서드
	// message.getChannel() : 수신한 채널명 (byte[])
	// message.getBody()    : 수신한 메시지 본문 (byte[])
	// pattern              : 패턴 구독(PSUBSCRIBE) 시 매칭된 패턴, 일반 구독(SUBSCRIBE) 시 null
	@Override
	public void onMessage(Message message, byte[] pattern) {
		String channel = new String(message.getChannel());
		String body = new String(message.getBody());
		log.info("[Subscriber] 메시지 수신 - 채널: {} | 내용: {}", channel, body);
	}
}
