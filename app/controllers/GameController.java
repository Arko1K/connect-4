package controllers;


import models.GameModel;
import play.mvc.Controller;
import play.mvc.LegacyWebSocket;

public class GameController extends Controller {

    private static final String KEY_GAME_ID = "gameId";
    private static final String KEY_DISC = "disc";


    public LegacyWebSocket<String> getNewGameSocket() {
        return new GameModel().getNewGameSocket();
    }

    public LegacyWebSocket<String> getOpenGameSocket() {
        return new GameModel().getOpenGameSocket();
    }

    public LegacyWebSocket<String> getGameSocketById() {
        return new GameModel().getGameSocketById(request().getQueryString(KEY_GAME_ID),
                Short.valueOf(request().getQueryString(KEY_DISC)));
    }
}