package org.birlik.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.birlik.game.batak.BatakGame;
import org.birlik.game.batak.BatakPhase;
import org.birlik.model.Card;
import org.birlik.model.GameType;
import org.birlik.model.Player;
import org.birlik.model.Suit;
import org.birlik.room.Room;
import org.birlik.room.RoomManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final RoomManager roomManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService botScheduler = Executors.newScheduledThreadPool(2);

    public GameWebSocketHandler(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        sendLobbyState(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        Room room = roomManager.findRoomBySession(sessionId);
        if (room != null) {
            room.leavePlayer(sessionId);
            broadcastRoomState(room);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode json = objectMapper.readTree(message.getPayload());
        String action = json.has("action") ? json.get("action").asText() : "";

        switch (action) {
            case "CREATE_ROOM":
                handleCreateRoom(session, json);
                break;
            case "JOIN_ROOM":
                handleJoinRoom(session, json);
                break;
            case "QUICK_PLAY":
                handleQuickPlay(session, json);
                break;
            case "TOGGLE_BOT":
                handleToggleBot(session, json);
                break;
            case "START_GAME":
                handleStartGame(session);
                break;
            case "PLACE_BID":
                handlePlaceBid(session, json);
                break;
            case "SELECT_TRUMP":
                handleSelectTrump(session, json);
                break;
            case "PLAY_CARD":
                handlePlayCard(session, json);
                break;
            case "NEXT_ROUND":
                handleNextRound(session);
                break;
            case "SEND_CHAT":
                handleSendChat(session, json);
                break;
            case "LEAVE_ROOM":
                handleLeaveRoom(session);
                break;
            case "GET_LOBBY":
                sendLobbyState(session);
                break;
        }
    }

    private void handleCreateRoom(WebSocketSession session, JsonNode json) throws IOException {
        String roomName = json.has("roomName") ? json.get("roomName").asText() : "";
        String playerName = json.has("playerName") ? json.get("playerName").asText("Oyuncu") : "Oyuncu";

        Room room = roomManager.createRoom(roomName, session.getId(), GameType.BATAK_IHALELI);
        int seat = room.joinPlayer(session.getId(), playerName, 0);

        broadcastRoomState(room);
    }

    private void handleJoinRoom(WebSocketSession session, JsonNode json) throws IOException {
        String roomId = json.has("roomId") ? json.get("roomId").asText() : "";
        String playerName = json.has("playerName") ? json.get("playerName").asText("Oyuncu") : "Oyuncu";

        Room room = roomManager.getRoom(roomId);
        if (room == null) {
            sendError(session, "Masa bulunamadı!");
            return;
        }

        int seat = room.joinPlayer(session.getId(), playerName, -1);
        if (seat == -1) {
            sendError(session, "Masa dolu!");
            return;
        }

        broadcastRoomState(room);
    }

    private void handleQuickPlay(WebSocketSession session, JsonNode json) throws IOException {
        String playerName = json.has("playerName") ? json.get("playerName").asText("Oyuncu") : "Oyuncu";

        // Find available room with open seat
        Room targetRoom = null;
        for (Room r : roomManager.getAllRooms()) {
            if (r.getGame().getPhase() == BatakPhase.WAITING_FOR_PLAYERS) {
                for (String sId : r.getSeatSessionIds()) {
                    if (sId == null) {
                        targetRoom = r;
                        break;
                    }
                }
            }
            if (targetRoom != null) {
                break;
            }
        }

        if (targetRoom == null) {
            targetRoom = roomManager.createRoom("Hızlı Oyuncular", session.getId(), GameType.BATAK_IHALELI);
        }

        targetRoom.joinPlayer(session.getId(), playerName, -1);
        broadcastRoomState(targetRoom);
    }

    private void handleToggleBot(WebSocketSession session, JsonNode json) throws IOException {
        Room room = roomManager.findRoomBySession(session.getId());
        if (room != null && room.getGame().getPhase() == BatakPhase.WAITING_FOR_PLAYERS) {
            int seat = json.has("seatIndex") ? json.get("seatIndex").asInt() : -1;
            room.toggleBotSeat(seat);
            broadcastRoomState(room);
        }
    }

    private void handleStartGame(WebSocketSession session) throws IOException {
        Room room = roomManager.findRoomBySession(session.getId());
        if (room != null) {
            room.getGame().startNewGame();
            broadcastRoomState(room);
            scheduleBotTurnIfNeeded(room);
        }
    }

    private void handlePlaceBid(WebSocketSession session, JsonNode json) throws IOException {
        Room room = roomManager.findRoomBySession(session.getId());
        if (room == null) {
            return;
        }

        int seatIndex = getPlayerSeatInRoom(room, session.getId());
        int bid = json.has("bid") ? json.get("bid").asInt() : 0;

        if (seatIndex != -1 && room.getGame().placeBid(seatIndex, bid)) {
            broadcastRoomState(room);
            scheduleBotTurnIfNeeded(room);
        } else {
            sendError(session, "Geçersiz ihale teklifi!");
        }
    }

    private void handleSelectTrump(WebSocketSession session, JsonNode json) throws IOException {
        Room room = roomManager.findRoomBySession(session.getId());
        if (room == null) {
            return;
        }

        int seatIndex = getPlayerSeatInRoom(room, session.getId());
        String suitStr = json.has("suit") ? json.get("suit").asText() : "";

        try {
            Suit suit = Suit.valueOf(suitStr);
            if (seatIndex != -1 && room.getGame().selectTrump(seatIndex, suit)) {
                broadcastRoomState(room);
                scheduleBotTurnIfNeeded(room);
            }
        } catch (IllegalArgumentException e) {
            sendError(session, "Geçersiz koz seçimi!");
        }
    }

    private void handlePlayCard(WebSocketSession session, JsonNode json) throws IOException {
        Room room = roomManager.findRoomBySession(session.getId());
        if (room == null) {
            return;
        }

        int seatIndex = getPlayerSeatInRoom(room, session.getId());
        String cardId = json.has("cardId") ? json.get("cardId").asText() : "";

        Player p = room.getGame().getPlayers().get(seatIndex);
        Card targetCard = null;
        for (Card c : p.getHand()) {
            if (c.getId().equals(cardId)) {
                targetCard = c;
                break;
            }
        }

        if (targetCard != null && room.getGame().playCard(seatIndex, targetCard)) {
            broadcastRoomState(room);
            scheduleNextStep(room);
        } else {
            sendError(session, "Geçersiz hamle! Kurallara uygun bir kart oynamalısınız.");
        }
    }

    private void handleNextRound(WebSocketSession session) throws IOException {
        Room room = roomManager.findRoomBySession(session.getId());
        if (room != null && room.getGame().getPhase() == BatakPhase.ROUND_OVER) {
            room.getGame().nextRound();
            broadcastRoomState(room);
            scheduleBotTurnIfNeeded(room);
        }
    }

    private void handleSendChat(WebSocketSession session, JsonNode json) throws IOException {
        Room room = roomManager.findRoomBySession(session.getId());
        if (room == null) {
            return;
        }

        int seatIndex = getPlayerSeatInRoom(room, session.getId());
        String sender = (seatIndex != -1) ? room.getGame().getPlayers().get(seatIndex).getName() : "İzleyici";
        String text = json.has("text") ? json.get("text").asText() : "";

        if (!text.trim().isEmpty()) {
            room.addChatMessage(sender, text);
            broadcastRoomState(room);
        }
    }

    private void handleLeaveRoom(WebSocketSession session) throws IOException {
        Room room = roomManager.findRoomBySession(session.getId());
        if (room != null) {
            room.leavePlayer(session.getId());
            sendLobbyState(session);
            broadcastRoomState(room);
        }
    }

    private int getPlayerSeatInRoom(Room room, String sessionId) {
        String[] seats = room.getSeatSessionIds();
        for (int i = 0; i < 4; i++) {
            if (sessionId.equals(seats[i])) {
                return i;
            }
        }
        return -1;
    }

    private void scheduleNextStep(Room room) {
        if (room.getGame().isTrickWaitingCollection()) {
            botScheduler.schedule(() -> {
                try {
                    synchronized (room) {
                        room.getGame().collectCurrentTrick();
                        broadcastRoomState(room);
                        scheduleNextStep(room);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 1800, TimeUnit.MILLISECONDS);
            return;
        }

        scheduleBotTurnIfNeeded(room);
    }

    private void scheduleBotTurnIfNeeded(Room room) {
        if (room.getGame().getPhase() == BatakPhase.WAITING_FOR_PLAYERS
                || room.getGame().getPhase() == BatakPhase.ROUND_OVER
                || room.getGame().getPhase() == BatakPhase.GAME_OVER
                || room.getGame().isTrickWaitingCollection()) {
            return;
        }

        int turnSeat = room.getGame().getCurrentTurnSeat();
        Player p = room.getGame().getPlayers().get(turnSeat);

        if (p.isBot()) {
            botScheduler.schedule(() -> {
                try {
                    synchronized (room) {
                        boolean moved = room.processBotTurnIfNeeded();
                        if (moved) {
                            broadcastRoomState(room);
                            scheduleNextStep(room);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 800, TimeUnit.MILLISECONDS);
        }
    }

    private void broadcastRoomState(Room room) {
        String[] seats = room.getSeatSessionIds();
        for (int i = 0; i < 4; i++) {
            String sId = seats[i];
            if (sId != null && sessions.containsKey(sId)) {
                WebSocketSession s = sessions.get(sId);
                if (s.isOpen()) {
                    try {
                        Map<String, Object> payload = buildRoomStatePayload(room, i);
                        s.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void sendLobbyState(WebSocketSession session) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "LOBBY_STATE");

        List<Map<String, Object>> roomList = new ArrayList<>();
        for (Room r : roomManager.getAllRooms()) {
            Map<String, Object> rMap = new HashMap<>();
            rMap.put("roomId", r.getRoomId());
            rMap.put("roomName", r.getRoomName());
            rMap.put("gameType", r.getGameType().getTitle());
            rMap.put("phase", r.getGame().getPhase().name());

            int playerCount = 0;
            for (String sId : r.getSeatSessionIds()) {
                if (sId != null) {
                    playerCount++;
                }
            }
            rMap.put("playerCount", playerCount);
            roomList.add(rMap);
        }

        payload.put("rooms", roomList);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ERROR");
        payload.put("message", message);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }

    private Map<String, Object> buildRoomStatePayload(Room room, int viewerSeatIndex) {
        BatakGame game = room.getGame();
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ROOM_STATE");
        payload.put("roomId", room.getRoomId());
        payload.put("roomName", room.getRoomName());
        payload.put("mySeatIndex", viewerSeatIndex);
        payload.put("gamePhase", game.getPhase().name());
        payload.put("currentTurnSeat", game.getCurrentTurnSeat());
        payload.put("dealerSeat", game.getDealerSeat());
        payload.put("highestBidderSeat", game.getHighestBidderSeat());
        payload.put("highestBid", game.getHighestBid());
        payload.put("trumpSuit", game.getTrumpSuit() != null ? game.getTrumpSuit().name() : null);
        payload.put("kozBroken", game.isKozBroken());
        payload.put("currentRound", game.getCurrentRound());
        payload.put("totalRounds", game.getTotalRounds());

        // Players info
        List<Map<String, Object>> playerList = new ArrayList<>();
        for (Player p : game.getPlayers()) {
            Map<String, Object> pMap = new HashMap<>();
            pMap.put("seatIndex", p.getSeatIndex());
            pMap.put("name", p.getName());
            pMap.put("isBot", p.isBot());
            pMap.put("handCount", p.getHand().size());
            pMap.put("bid", p.getBid());
            pMap.put("tricksWon", p.getTricksWon());
            pMap.put("score", p.getScore());
            playerList.add(pMap);
        }
        payload.put("players", playerList);

        // Viewer's hand
        Player viewer = game.getPlayers().get(viewerSeatIndex);
        List<Map<String, Object>> myHandList = new ArrayList<>();
        for (Card c : viewer.getHand()) {
            Map<String, Object> cMap = new HashMap<>();
            cMap.put("id", c.getId());
            cMap.put("suit", c.getSuit().name());
            cMap.put("symbol", c.getSuit().getSymbol());
            cMap.put("color", c.getSuit().getColor());
            cMap.put("rankLabel", c.getRank().getLabel());
            cMap.put("value", c.getValue());
            myHandList.add(cMap);
        }
        payload.put("myHand", myHandList);

        // Legal cards if it's viewer's turn
        List<String> legalCardIds = new ArrayList<>();
        if (game.getPhase() == BatakPhase.PLAYING_TRICKS && game.getCurrentTurnSeat() == viewerSeatIndex && !game.isTrickWaitingCollection()) {
            for (Card c : game.getLegalCards(viewerSeatIndex)) {
                legalCardIds.add(c.getId());
            }
        }
        payload.put("legalCardIds", legalCardIds);

        // Current trick cards
        List<Map<String, Object>> trickList = new ArrayList<>();
        if (game.getCurrentTrick() != null) {
            for (Map.Entry<Integer, Card> entry : game.getCurrentTrick().getPlayedCards().entrySet()) {
                Map<String, Object> item = new HashMap<>();
                item.put("seatIndex", entry.getKey());
                Card c = entry.getValue();
                item.put("cardId", c.getId());
                item.put("suit", c.getSuit().name());
                item.put("symbol", c.getSuit().getSymbol());
                item.put("color", c.getSuit().getColor());
                item.put("rankLabel", c.getRank().getLabel());
                trickList.add(item);
            }
        }
        payload.put("currentTrick", trickList);
        payload.put("trickWaitingCollection", game.isTrickWaitingCollection());
        payload.put("trickWinnerSeat", game.getTrickWinnerSeat());
        payload.put("winningCardId", game.getTrickWinningCard() != null ? game.getTrickWinningCard().getId() : null);

        payload.put("chatMessages", room.getChatMessages());
        return payload;
    }
}
