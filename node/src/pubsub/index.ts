import { createRedisClient, disconnectRedisClient } from "../connection";

async function main(): Promise<void> {
  const subscriber = createRedisClient();
  const publisher = createRedisClient();

  try {
    const channel = "test:notification";
    let messageRecived = false;

    subscriber.on("message", (channel, message) => {
      console.log(`Message received on channel ${channel}: ${message}`);
      messageRecived = true;
    });

    await subscriber.subscribe(channel);
    console.log(`Subscribed to channel: ${channel}`);

    const message1 = "Hello, Redis Pub/Sub!";
    const receivers1 = await publisher.publish(channel, message1);
    console.log(`Published message to ${receivers1} receivers`);

    const message2 = "Another message for subscribers!";
    const receivers2 = await publisher.publish(channel, message2);
    console.log(`Published message to ${receivers2} receivers`);

    await new Promise((resolve) => setTimeout(resolve, 1000));
  } finally {
    await disconnectRedisClient(subscriber);
    await disconnectRedisClient(publisher);
  }
}

main().catch(console.error);
