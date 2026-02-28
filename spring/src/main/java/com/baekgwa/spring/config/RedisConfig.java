package com.baekgwa.spring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 설정 클래스
 *
 * [Auto Configuration 흐름]
 *   application.yml (spring.data.redis.host/port/timeout 등)
 *     → Spring Boot RedisAutoConfiguration
 *       → LettuceConnectionFactory Bean 자동 생성
 *         → 이 클래스에서 주입받아 RedisTemplate Bean 등록
 *
 * [왜 RedisTemplate을 직접 등록하는가?]
 *   Spring Boot가 자동 등록하는 RedisTemplate<Object, Object>은
 *   기본으로 JdkSerializationRedisSerializer를 사용합니다.
 *   이 경우 Redis CLI에서 키/값이 바이너리로 보여 직접 확인이 어렵습니다.
 *
 *   StringRedisSerializer를 명시적으로 설정한 RedisTemplate<String, String>을
 *   직접 등록하면 Redis CLI에서 사람이 읽을 수 있는 형태로 저장됩니다.
 *
 * [StringRedisTemplate vs RedisTemplate<String, String>]
 *   StringRedisTemplate: RedisTemplate<String, String>을 상속하며 StringRedisSerializer를 기본 적용
 *                        → 직접 new 해도 별도 Serializer 설정 불필요
 *   RedisTemplate<String, String>: 직접 Serializer를 설정해야 String으로 저장됩니다.
 *                                   → 이 예제처럼 직접 설정하면 StringRedisTemplate과 동일하게 동작합니다.
 */
@Configuration
public class RedisConfig {

	/**
	 * RedisTemplate Bean 등록
	 *
	 * LettuceConnectionFactory는 application.yml 설정 기반으로
	 * Spring Boot RedisAutoConfiguration이 자동 생성한 Bean을 주입받습니다.
	 * (별도로 new LettuceConnectionFactory()를 할 필요 없음)
	 *
	 * @ConditionalOnMissingBean(name = "redisTemplate") 조건으로 인해
	 * 이 Bean이 등록되면 Spring Boot가 자동 생성하는 RedisTemplate<Object, Object>은 등록되지 않습니다.
	 */
	@Bean
	public RedisTemplate<String, String> redisTemplate(LettuceConnectionFactory connectionFactory) {
		RedisTemplate<String, String> template = new RedisTemplate<>();

		template.setConnectionFactory(connectionFactory);

		// StringRedisSerializer: String을 UTF-8 바이트 배열로 직렬화/역직렬화합니다.
		// 설정하지 않으면 JdkSerializationRedisSerializer가 기본 적용됩니다.
		StringRedisSerializer stringSerializer = new StringRedisSerializer();
		template.setKeySerializer(stringSerializer);
		template.setValueSerializer(stringSerializer);
		template.setHashKeySerializer(stringSerializer);
		template.setHashValueSerializer(stringSerializer);

		return template;
	}
}
