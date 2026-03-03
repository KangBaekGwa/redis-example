package com.baekgwa.spring.example.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * PackageName : com.baekgwa.spring.example.pubsub
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
// convertAndSend 메서드를 통해 채널에 메시지를 발행합니다.
//
// [차이점] Java 순수 Lettuce: command.publish(channel, message) → Long (수신자 수) 반환
//          Spring Data Redis: redisTemplate.convertAndSend(channel, message) → void 반환
//            → 수신자 수가 필요한 경우 execute(RedisCallback) 으로 low-level 접근 필요
@Component
public class PubSubPublisher {

	private static final Logger log = LoggerFactory.getLogger(PubSubPublisher.class);

	private final RedisTemplate<String, String> redisTemplate;

	public PubSubPublisher(RedisTemplate<String, String> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public void publish() throws InterruptedException {
		// 1. convertAndSend - 채널에 메시지 발행
		// convertAndSend: 설정된 Serializer 로 메시지를 직렬화한 뒤 채널에 발행합니다.
		// 반환값 없음 (void) - Java Lettuce 의 command.publish() 와 동일한 역할이나 수신자 수 반환 없음
		log.info("=========publish=========");
		redisTemplate.convertAndSend("news", "Breaking: Redis 8.0 Released!");
		log.info("[Publisher] news 채널 발행 완료");

		Thread.sleep(100); // 구독자 수신 대기

		redisTemplate.convertAndSend("sports", "FIFA World Cup 2026 결승전 한국 우승!");
		log.info("[Publisher] sports 채널 발행 완료");

		Thread.sleep(100);

		redisTemplate.convertAndSend("tech", "Java 25 LTS 출시 예정");
		log.info("[Publisher] tech 채널 발행 완료");

		Thread.sleep(100);

		// 2. 구독자 없는 채널에 발행
		// 구독자가 없어도 에러 없이 처리됩니다.
		log.info("=========구독자 없는 채널 발행=========");
		redisTemplate.convertAndSend("unsubscribed-channel", "아무도 듣지 않는 메시지");
		log.info("[Publisher] unsubscribed-channel 발행 완료 (수신자 없음)");

		Thread.sleep(100);

		// 3. 동일 채널에 여러 번 발행
		log.info("=========연속 발행=========");
		for (int i = 1; i <= 3; i++) {
			redisTemplate.convertAndSend("news", "뉴스 속보 #" + i);
			log.info("[Publisher] news 채널 {}번째 발행 완료", i);
			Thread.sleep(100);
		}
	}
}
