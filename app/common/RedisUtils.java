package common;


import akka.actor.ActorSystem;
import com.google.inject.Inject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import scala.concurrent.duration.Duration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for Redis.
 */
public class RedisUtils {

    private static final String KEY_DISCS = "discs";
    private static final String KEY_LAST_DISC = "lastDisc";
    @Inject
    private static JedisPool jedisPool;
    @Inject
    private static ActorSystem actorSystem;


    /**
     * Subscribe a JedisPubSub listener to a channel
     */
    public static void subscribe(JedisPubSub jedisPubSub, String channel) {
        actorSystem.scheduler().scheduleOnce(
                Duration.create(0, TimeUnit.MILLISECONDS),
                () -> {
                    jedisPool.getResource().subscribe(jedisPubSub, channel);
                },
                actorSystem.dispatcher());
    }

    /**
     * Publish a message to a channel
     */
    public static void publish(String channel, String message) {
        Jedis jedis = jedisPool.getResource();
        try {
            jedis.publish(channel, message);
        } finally {
            jedisPool.returnResource(jedis);
        }
    }

    /**
     * Attach a disc with a game. This implies that the disc is taken by a player.
     *
     * @return -1 if disc could not be attached, else count of discs after attachment.
     */
    public static short attachDisc(String gameId, short disc) {
        Jedis jedis = jedisPool.getResource();
        try {
            String listName = KEY_DISCS + gameId, discString = String.valueOf(disc);
            List<String> discs = jedis.lrange(listName, 0, -1);
            if (!discs.contains(discString)) {
                jedis.lpush(listName, discString);
                return (short) (discs.size() + 1);
            }
            return -1;
        } finally {
            jedisPool.returnResource(jedis);
        }
    }

    /**
     * Get one of the currently attached discs
     */
    public static short getAttachedDisc(String gameId) {
        Jedis jedis = jedisPool.getResource();
        try {
            String listName = KEY_DISCS + gameId;
            String disc = jedis.lindex(listName, 0);
            if (disc != null)
                return Short.valueOf(disc);
            return -1;
        } finally {
            jedisPool.returnResource(jedis);
        }
    }

    /**
     * Get the number of currently attached discs
     */
    public static short getAttachedCount(String gameId) {
        Jedis jedis = jedisPool.getResource();
        try {
            return jedis.llen(KEY_DISCS + gameId).shortValue();
        } finally {
            jedisPool.returnResource(jedis);
        }
    }

    /**
     * Detach a disc from a game. This implies that the disc is freed up from a player.
     */
    public static void detachDisc(String gameId, short disc) {
        Jedis jedis = jedisPool.getResource();
        try {
            String listName = KEY_DISCS + gameId;
            jedis.lrem(listName, 0, String.valueOf(disc));
            if (jedis.llen(listName) == 0)
                jedis.del(listName);
        } finally {
            jedisPool.returnResource(jedis);
        }
    }

    /**
     * Get the disc used in the last move of the game
     */
    public static short getLastDisc(String gameId) {
        Jedis jedis = jedisPool.getResource();
        try {
            return Short.valueOf(jedis.get(KEY_LAST_DISC + gameId));
        } finally {
            jedisPool.returnResource(jedis);
        }
    }

    /**
     * Set the disc used in the last move of the game
     */
    public static void setLastDisc(String gameId, short disc) {
        Jedis jedis = jedisPool.getResource();
        try {
            jedis.set(KEY_LAST_DISC + gameId, String.valueOf(disc));
        } finally {
            jedisPool.returnResource(jedis);
        }
    }
}