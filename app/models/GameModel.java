package models;


import actors.GameActor;
import common.Constants;
import common.RedisUtils;
import models.entities.Game;
import models.entities.GameMessage;
import play.mvc.LegacyWebSocket;
import play.mvc.WebSocket;

/**
 * Model class for all logic and operations on a Game object.
 * Use this class to get a WebSocket for a new game or an existing game.
 * A move listener will check for a win whenever a move is made and will prevent moves out of turn.
 */
public class GameModel {

    private static final short ROWS = 6;
    private static final short COLUMNS = 7;
    private static final short CONNECT_COUNT = 4;
    private static final short DISC_COUNT = 2;
    private static final short DISC_EMPTY = 0;
    private static final short DISC_1 = 1;
    private static final short DISC_2 = 2;
    private static final short DISC_1_WIN = 3;
    private static final short DISC_2_WIN = 4;
    private static final short ALIGNMENTS = 4;
    private static final short ALIGNMENT_E_W = 0;
    private static final short ALIGNMENT_N_S = 1;
    private static final short ALIGNMENT_NW_SE = 2;
    private static final short ALIGNMENT_NE_SW = 3;
    private static final short DIRECTION_UP = 0;
    private static final short DIRECTION_DOWN = 1;

    private class Pair {
        short i;
        short j;

        public Pair(short i, short j) {
            this.i = i;
            this.j = j;
        }
    }


    /**
     * Get WebSocket for a new game.
     */
    public LegacyWebSocket<String> getNewGameSocket() {
        String gameId = new Game(new short[ROWS][COLUMNS]).save();

        // Setting the last disc to 2 forces player 1 to make the first move
        RedisUtils.setLastDisc(gameId, DISC_2);

        return getSocket(gameId, DISC_1);
    }

    /**
     * Get WebSocket for an open game where a player is waiting.
     */
    public LegacyWebSocket<String> getOpenGameSocket() {
        Game game = Game.findOpenGame();
        if (game == null)
            return getNewGameSocket();

        // Once a game is started it cannot be joined by a new player again
        game.setStartedOn(System.currentTimeMillis());
        String gameId = game.save();

        // Since the current player's disc has not yet been attached there will only be maximum one disc attached to Redis
        // Get socket with DISC_2 only if DISC_1 is currently taken
        return getSocket(gameId, RedisUtils.getAttachedDisc(gameId) == DISC_1 ? DISC_2 : DISC_1);
    }

    /**
     * Get WebSocket for an existing game with game with a gameId and a disc that isn't being used by another player.
     * This is useful for reconnecting with a game that was interrupted.
     */
    public LegacyWebSocket<String> getGameSocketById(String gameId, short disc) {
        if (Game.exists(gameId))
            return getSocket(gameId, disc);
        return null;
    }

    private LegacyWebSocket<String> getSocket(String gameId, short disc) {
        short attachResult = RedisUtils.attachDisc(gameId, disc);

        // If attachResult is negative then the player's disc could not be attached
        if (attachResult > 0) {
            return WebSocket.withActor(actorRef -> GameActor.props(actorRef, gameId, disc,
                    getMoveListener(gameId, disc, attachResult)));
        }
        return null;
    }

    private GameActor.GameListener getMoveListener(String gameId, short disc, short attachResult) {
        return new GameActor.GameListener() {
            @Override
            public void onReady() {
                // If attachResult is equal to DISC_COUNT then the game is full
                if (attachResult == DISC_COUNT)
                    // This lets all clients know that the game has started
                    RedisUtils.publish(gameId, new GameMessage(MessageType.START, disc, null).toString());
            }

            @Override
            public GameMessage onMove(short column) {
                // Use Redis to check if the last move was made by the same disc
                if (RedisUtils.getLastDisc(gameId) == disc)
                    return new GameMessage(MessageType.LOCKED, disc, Constants.MESSAGE_WRONG_TURN);

                else {
                    // Immediately update the last disc used. This will lock the other player.
                    RedisUtils.setLastDisc(gameId, disc);

                    // Get the game and update the grid
                    Game game = Game.findById(gameId);
                    short[][] grid = game.getGrid();
                    short row = ROWS - 1;
                    for (; row >= 0; row--) {
                        if (grid[row][column] == DISC_EMPTY) {
                            grid[row][column] = disc;
                            break;
                        }
                    }

                    // Check for a win
                    // Worst case: 4 * O(k) for 4 different alignments
                    boolean win = false;
                    for (short align = 0; align < ALIGNMENTS; align++) {
                        if (computeWin(row, column, grid, disc, align)) {
                            win = true;
                            break;
                        }
                    }

                    // Update the game and return
                    game.update(column, grid, disc);
                    return new GameMessage(win ? MessageType.END : MessageType.MOVE, disc, game.toString());
                }
            }
        };
    }

    // The method traverses the gird in two opposite directions from a given starting point and alignment
    // and checks whether there are k consecutive discs of the same type.
    // The algorithm runs in O(k) time where k is the number of discs required to connect.
    private boolean computeWin(short row, short column, short[][] grid, short disc, short align) {
        short i = row, j = column, startRow, startColumn, count = 0;

        // Traversing up
        do {
            startRow = i;
            startColumn = j;
            ++count;
            Pair pair = getIndices(i, j, align, DIRECTION_UP);
            i = pair.i;
            j = pair.j;
        } while (isMatch(i, j, grid, disc) && count <= CONNECT_COUNT);

        i = row;
        j = column;
        // Decrementing count else the starting position will be counted twice
        --count;

        // Traversing down
        do {
            ++count;
            Pair pair = getIndices(i, j, align, DIRECTION_DOWN);
            i = pair.i;
            j = pair.j;
        } while (isMatch(i, j, grid, disc) && count <= CONNECT_COUNT);

        // If win then update the grid to represent the winning connection of discs
        if (count == CONNECT_COUNT) {
            i = startRow;
            j = startColumn;
            count = 1;
            grid[i][j] = (disc == DISC_1 ? DISC_1_WIN : DISC_2_WIN);
            while (count < CONNECT_COUNT) {
                ++count;
                Pair pair = getIndices(i, j, align, DIRECTION_DOWN);
                i = pair.i;
                j = pair.j;
                grid[i][j] = (disc == DISC_1 ? DISC_1_WIN : DISC_2_WIN);
            }
            return true;
        }
        return false;
    }

    // The method returns the next cell to be visited for a given alignment and directions
    private Pair getIndices(short row, short column, short align, short direction) {
        switch (align) {
            // East-West
            case ALIGNMENT_E_W:
                column += direction == DIRECTION_UP ? -1 : 1;
                break;
            // North-South
            case ALIGNMENT_N_S:
                row += direction == DIRECTION_UP ? -1 : 1;
                break;
            // NW-SE
            case ALIGNMENT_NW_SE:
                row += direction == DIRECTION_UP ? -1 : 1;
                column += direction == DIRECTION_UP ? -1 : 1;
                break;
            // NE-SW
            default:
                row += direction == DIRECTION_UP ? -1 : 1;
                column += direction == DIRECTION_UP ? 1 : -1;
                break;
        }
        return new Pair(row, column);
    }

    // The method checks for boundary conditions and if the given disc matches the one in the current cell
    private boolean isMatch(short row, short column, short[][] grid, short disc) {
        return row >= 0 && row < ROWS && column >= 0 && column < COLUMNS && grid[row][column] == disc;
    }
}