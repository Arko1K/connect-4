package models.entities;


import common.MongoUtils;
import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Transient;
import play.libs.Json;

import java.util.ArrayList;
import java.util.List;

@Entity(value = "game", noClassnameStored = true)
public class Game {

    @Transient
    private static final String KEY_ID = "id", KEY_STARTED_ON = "startedOn", KEY_CREATED_ON = "createdOn";

    @Id
    private ObjectId id;
    private short[][] grid;
    private short lastDisc;
    private long createdOn;
    private long startedOn;
    private List<Short> moveHistory = new ArrayList<>();


    // Empty constructor for framework
    public Game() {
    }

    public Game(short[][] grid) {
        this.grid = grid;
        createdOn = System.currentTimeMillis();
    }

    /**
     * Find game by Id
     */
    public static Game findById(String id) {
        return MongoUtils.getDatastore().get(Game.class, new ObjectId(id));
    }

    /**
     * Find the first game that hasn't yet been started
     */
    public static Game findOpenGame() {
        return MongoUtils.getDatastore().createQuery(Game.class).field(KEY_STARTED_ON).equal(0).order(KEY_CREATED_ON)
                .limit(1).get();
    }


    /**
     * Check if game with the given Id exists
     */
    public static boolean exists(String id) {
        return MongoUtils.getDatastore().find(Game.class, KEY_ID, new ObjectId(id)).countAll() > 0;
    }


    public String getId() {
        return id.toHexString();
    }

    public short[][] getGrid() {
        return grid;
    }

    public void setGrid(short[][] grid) {
        this.grid = grid;
    }

    public short getLastDisc() {
        return lastDisc;
    }

    public void setLastDisc(short lastDisc) {
        this.lastDisc = lastDisc;
    }

    public void setStartedOn(long startedOn) {
        this.startedOn = startedOn;
    }

    public void addMove(short column) {
        moveHistory.add(column);
    }

    @Override
    public String toString() {
        return Json.stringify(Json.toJson(this));
    }

    public String save() {
        MongoUtils.getDatastore().save(this);
        return getId();
    }

    public void update(short move, short[][] grid, short disc) {
        moveHistory.add(move);
        this.grid = grid;
        this.lastDisc = disc;
        save();
    }
}