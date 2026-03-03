package com.baekgwa.spring.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.baekgwa.spring.example.pubsub.PubSubPublisher;

/**
 * PackageName : com.baekgwa.spring.example
 * FileName    : PubSubExample
 * Author      : Baekgwa
 * Date        : 26. 3. 3.
 * Description :
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2026-03-03     Baekgwa               Initial creation
 **/
// Redis Pub/Sub 예제 진입점
// PubSubConfig 에서 RedisMessageListenerContainer Bean 이 등록되어
// 애플리케이션 시작 시 news/sports/tech 채널 구독이 자동으로 완료됩니다.
// 이 클래스에서는 Publisher 를 통해 메시지 발행만 수행합니다.
@Component
public class PubSubExample {

	private static final Logger log = LoggerFactory.getLogger(PubSubExample.class);

	private final PubSubPublisher publisher;

	public PubSubExample(PubSubPublisher publisher) {
		this.publisher = publisher;
	}

	public void run() throws Exception {
		// Container Bean 이 이미 시작되어 구독 중이므로 바로 발행합니다.
		log.info("[Pub/Sub 예제 시작] - news, sports, tech 채널 구독 중");

		publisher.publish();

		Thread.sleep(300); // 마지막 메시지 수신 대기

		log.info("[Pub/Sub 예제 완료]");
	}
}
