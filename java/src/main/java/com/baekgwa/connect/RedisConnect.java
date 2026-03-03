package com.baekgwa.connect;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

/**
 * PackageName : com.baekgwa.connect
 * FileName    : RedisConnect
 * Author      : Baekgwa
 * Date        : 26. 2. 28.
 * Description : 
 * =====================================================================================================================
 * DATE          AUTHOR               NOTE
 * ---------------------------------------------------------------------------------------------------------------------
 * 2025-08-03     Baekgwa               Initial creation
 **/
public class RedisConnect {

	private final RedisClient client;

	public RedisConnect() {
		RedisURI uri = RedisURI.Builder
				.redis("localhost", 6379)
				.build();

		this.client = RedisClient.create(uri);
	}

	/**
	 * Redis Connection 생성
	 */
	public StatefulRedisConnection<String, String> getConnection() {
		return client.connect();
	}

	/**
	 * Redis Pub/Sub Connection 생성
	 */
	public StatefulRedisPubSubConnection<String, String> getPubSubConnection() {
		return client.connectPubSub();
	}

	/**
	 * 애플리케이션 종료 시 호출
	 */
	public void shutdown() {
		client.shutdown();
	}
}
