import { createRedisClient, disconnectRedisClient } from "../connection";

async function runBasicPipelineExample(): Promise<void> {
  const client = createRedisClient();

  try {
    // 0. 초기화
    await client.flushdb();

    console.log("=========기본 파이프라이닝=========");

    // client.pipeline() 으로 Pipeline 객체 생성
    // 이후 명령어들은 즉시 전송되지 않고 내부 버퍼에 쌓입니다.
    const pipeline = client.pipeline();
    pipeline.set("pipe:name", "백과");
    pipeline.set("pipe:city", "서울");
    pipeline.get("pipe:name");
    pipeline.get("pipe:city");
    pipeline.incr("pipe:count");
    pipeline.incr("pipe:count");
    pipeline.incr("pipe:count");

    // exec() 호출 시 버퍼에 쌓인 명령어를 한 번에 서버로 전송합니다.
    // 반환값: [error, value][] 배열 (각 명령어 결과)
    // [[null, "OK"], [null, "OK"], [null, "백과"], [null, "서울"], [null, 1], [null, 2], [null, 3]]
    const results = await pipeline.exec();

    console.log("파이프라인 결과 목록 :");
    results?.forEach(([err, value], index) => {
      console.log(`  [${index}]`, err ? `에러: ${err.message}` : value);
    });
  } finally {
    await disconnectRedisClient(client);
  }
}

async function main(): Promise<void> {
  await runBasicPipelineExample();
}

main().catch(console.error);
