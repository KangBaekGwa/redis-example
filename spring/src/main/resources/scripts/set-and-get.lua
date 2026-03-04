-- SET 후 GET 결과를 반환하는 스크립트
-- KEYS[1] : 저장할 키
-- ARGV[1] : 저장할 값
redis.call('SET', KEYS[1], ARGV[1])
return redis.call('GET', KEYS[1])
