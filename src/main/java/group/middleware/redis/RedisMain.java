package group.middleware.redis;

import redis.clients.jedis.Jedis;

/**
 * Redis使用
 * @Author yourgentlesmile
 * @Date 2020/1/19 14:10.
 */
public class RedisMain {
    public static void main(String[] args) {
        Jedis redis = new Jedis("192.168.31.158",6379);
        System.out.println(redis.get("plp"));
        // to be continue
    }
}
