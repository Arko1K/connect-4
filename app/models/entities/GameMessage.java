package models.entities;


import models.MessageType;
import play.libs.Json;

public class GameMessage {

    private MessageType type;
    private short disc;
    private String data;


    // Empty constructor for framework
    public GameMessage() {
    }

    public GameMessage(MessageType type, short disc, String data) {
        this.type = type;
        this.disc = disc;
        this.data = data;
    }

    public static GameMessage fromJsonString(String jsonString) {
        return Json.fromJson(Json.parse(jsonString), GameMessage.class);
    }


    public MessageType getType() {
        return type;
    }

    public short getDisc() {
        return disc;
    }

    public String getData() {
        return data;
    }

    @Override
    public String toString() {
        return Json.stringify(Json.toJson(this));
    }
}