package actors;


import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import common.Constants;
import common.RedisUtils;
import models.MessageType;
import models.entities.GameMessage;
import redis.clients.jedis.JedisPubSub;

/**
 * Akka actor class for all inbound and outbound messages.
 * Checks for locks and decides when to use the GameListener interface.
 */
public class GameActor extends UntypedActor {

    private ActorRef out;
    private String gameId;
    private short disc;
    private final GameListener gameListener;
    private final JedisPubSub jedisPubSub;
    private boolean started = false;

    public interface GameListener {
        void onReady();

        GameMessage onMove(short column);
    }


    public GameActor(ActorRef out, String gameId, short disc, GameListener gameListener) {
        this.out = out;
        this.gameId = gameId;
        this.disc = disc;
        this.gameListener = gameListener;

        jedisPubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                GameMessage gameMessage = GameMessage.fromJsonString(message);

                if (gameMessage.getType().equals(MessageType.START))
                    started = true; // Starting the game

                out.tell(message, self());
            }

            @Override
            public void onPMessage(String pattern, String channel, String message) {

            }

            @Override
            public void onSubscribe(String channel, int subscribedChannels) {

            }

            @Override
            public void onUnsubscribe(String channel, int subscribedChannels) {

            }

            @Override
            public void onPUnsubscribe(String pattern, int subscribedChannels) {

            }

            @Override
            public void onPSubscribe(String pattern, int subscribedChannels) {

            }
        };

        RedisUtils.subscribe(jedisPubSub, gameId);

        // This lets the client know that the player has connected
        out.tell(new GameMessage(MessageType.CONNECT, disc, gameId).toString(), self());

        gameListener.onReady();
    }

    public static Props props(ActorRef out, String gameId, short disc, GameListener gameListener) {
        return Props.create(GameActor.class, out, gameId, disc, gameListener);
    }


    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof String) {
            // A player cannot make a move until the game has started
            if (started) {
                short column = Short.valueOf((String) message);
                GameMessage gameMessage = gameListener.onMove(column);

                if (!gameMessage.getType().equals(MessageType.LOCKED))
                    RedisUtils.publish(gameId, gameMessage.toString());
                else // If the game is locked then tell the client directly instead of publishing the message to the channel
                    out.tell(gameMessage.toString(), self());
            } else
                out.tell(new GameMessage(MessageType.LOCKED, disc, Constants.MESSAGE_GAME_WAITING).toString(), self());
        }
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();

        jedisPubSub.unsubscribe();

        // Detach the disc so it can be used by another player if the game hasn't already started
        RedisUtils.detachDisc(gameId, disc);
    }
}