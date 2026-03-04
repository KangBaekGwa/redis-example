-- 선착순 쿠폰 발급 스크립트 (원자적 재고 확인 + 차감)
-- KEYS[1] : 쿠폰 재고 키
-- 반환값  : 1 = 발급 성공, 0 = 재고 소진
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil or stock <= 0 then
    return 0
end
redis.call('DECR', KEYS[1])
return 1
