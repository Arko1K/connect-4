var disc, gameId, ws;

var receiveEvent = function (event) {
    var obj = JSON.parse(event.data);

    if (obj.type == "CONNECT") {
        disc = obj.disc;
        gameId = obj.data;
        addMessage("disc: " + disc + ", gameId: " + gameId);
    } else if (obj.type == "LOCKED")
        addMessage(obj.data);
    else if (obj.type == "START" || obj.type == "INVALID")
        addMessage(obj.type);
    else {
        var game = JSON.parse(obj.data);
        var line = "<br>";
        for (var i = 0; i < 6; i++) {
            line = line + (i + 1) + "&nbsp&nbsp&nbsp";
            for (var j = 0; j < 7; j++)
                line = line + game.grid[i][j] + " ";
            line = line + "<br>";
        }
        document.getElementById("grid").innerHTML = line;
    }
};

function setupWebSocket() {
    var WSX = window['MozWebSocket'] ? MozWebSocket : WebSocket;
    if (gameId != null)
        ws = new WSX("ws://localhost:9000/game?disc=" + disc + "&gameId=" + gameId);
    else
        ws = new WSX("ws://localhost:9000/game/join");
    ws.onmessage = receiveEvent;
    ws.onclose = function () {
        addMessage("WebSocket closed");
        setTimeout(setupWebSocket, 1000);
    };
    ws.onopen = function () {
        addMessage("WebSocket opened");
    };
    ws.onerror = function (e) {
        addMessage(e);
    };
}

setupWebSocket();

function makeMove() {
    var txtbox = document.getElementById("column");
    var value = txtbox.value;
    ws.send(value - 1);
}

function addMessage(msg) {
    var newMessage = document.createElement('div');
    newMessage.innerHTML = msg;
    var div = document.getElementById("messages");
    div.appendChild(newMessage);
}