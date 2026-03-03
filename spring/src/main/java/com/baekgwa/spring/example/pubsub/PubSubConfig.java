package com.baekgwa.spring.example.pubsub;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * PackageName : com.baekgwa.spring.example.pubsub
 * FileName    : PubSubConfig
 * Author      : Baekgwa
 * Date        : 26. 3. 3.
 * Description :
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2026-03-03     Baekgwa               Initial creation
 **/
// Redis Pub/Sub 설정 클래스
// RedisMessageListenerContainer 를 Bean 으로 등록하여 Spring 이 lifecycle(시작/종료)을 관리합니다.
// 애플리케이션 시작 시 자동으로 구독을 시작하고, 종료 시 자동으로 구독을 해제합니다.
//
// [차이점] Java 순수 Lettuce: StatefulRedisPubSubConnection + commands.subscribe() 를 직접 관리
//          Spring Data Redis: Container Bean 등록으로 구독 lifecycle 을 Spring 에 위임
@Configuration
public class PubSubConfig {

	// RedisMessageListenerContainer
	// - Pub/Sub 구독 전용 커넥션을 내부적으로 생성하고 유지합니다.
	// - 등록된 MessageListener 에 수신 메시지를 비동기로 전달합니다.
	// - SmartLifecycle 을 구현하므로 Spring 컨텍스트 시작/종료에 맞춰 자동으로 start/stop 됩니다.
	@Bean
	public RedisMessageListenerContainer redisMessageListenerContainer(
			RedisConnectionFactory connectionFactory,
			PubSubSubscriber subscriber) {

		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);

		// ChannelTopic: 정확한 채널명으로 구독 (SUBSCRIBE)
		// 동일한 subscriber 를 여러 채널에 등록하거나, 채널마다 다른 subscriber 를 등록할 수 있습니다.
		container.addMessageListener(subscriber, new ChannelTopic("news"));
		container.addMessageListener(subscriber, new ChannelTopic("sports"));
		container.addMessageListener(subscriber, new ChannelTopic("tech"));

		return container;
	}
}
