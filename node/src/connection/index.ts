import Redis from "ioredis";

export function createRedisClient(): Redis {
  const client = new Redis({
    host: "localhost",
    port: 6379,
    db: 0,
  });

  client.on("error", (err: Error) => {
    console.error("Redis Client Error", err);
  });

  return client;
}

export async function disconnectRedisClient(client: Redis): Promise<void> {
  try {
    await client.quit();
  } catch (err) {
    client.disconnect();
  }
}
