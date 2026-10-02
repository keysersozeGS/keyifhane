package org.birlik.room;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.birlik.game.batak.BatakBotAI;
import org.birlik.game.batak.BatakGame;
import org.birlik.game.batak.BatakPhase;
import org.birlik.model.Card;
import org.birlik.model.GameType;
import org.birlik.model.Player;
import org.birlik.model.Suit;

public class Room {

    private final String roomId;
    private final String roomName;
    private final String hostId;
    private final GameType gameType;
    private final BatakGame game;
    private final String[] seatSessionIds = new String[4];
    private final List<Map<String, String>> chatMessages = new ArrayList<>();
    private final long createdAt;

    public Room(String roomId, String roomName, String hostId, GameType gameType) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.hostId = hostId;
        this.gameType = gameType;
        this.game = new BatakGame();
        this.createdAt = System.currentTimeMillis();
    }

    public synchronized int joinPlayer(String sessionId, String playerName, int preferredSeat) {
        // Find seat
        int seat = -1;
        if (preferredSeat >= 0 && preferredSeat < 4 && seatSessionIds[preferredSeat] == null) {
            seat = preferredSeat;
        } else {
            for (int i = 0; i < 4; i++) {
                if (seatSessionIds[i] == null) {
                    seat = i;
                    break;
                }
            }
        }

        if (seat != -1) {
            seatSessionIds[seat] = sessionId;
            game.addPlayer(seat, sessionId, playerName, false);
        }
        return seat;
    }

    public synchronized void leavePlayer(String sessionId) {
        for (int i = 0; i < 4; i++) {
            if (sessionId.equals(seatSessionIds[i])) {
                seatSessionIds[i] = null;
                game.removePlayer(i);
            }
        }
    }

    public synchronized void toggleBotSeat(int seatIndex) {
        if (seatIndex >= 0 && seatIndex < 4) {
            if (seatSessionIds[seatIndex] == null) {
                Player p = game.getPlayers().get(seatIndex);
                p.setBot(!p.isBot());
            }
        }
    }

    public synchronized void addChatMessage(String sender, String text) {
        Map<String, String> msg = new HashMap<>();
        msg.put("sender", sender);
        msg.put("text", text);
        msg.put("time", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        chatMessages.add(msg);
        if (chatMessages.size() > 50) {
            chatMessages.remove(0);
        }
    }

    public synchronized boolean processBotTurnIfNeeded() {
        if (game.getPhase() == BatakPhase.WAITING_FOR_PLAYERS
                || game.getPhase() == BatakPhase.GAME_OVER
                || game.getPhase() == BatakPhase.ROUND_OVER
                || game.isTrickWaitingCollection()) {
            return false;
        }

        int turnSeat = game.getCurrentTurnSeat();
        Player p = game.getPlayers().get(turnSeat);

        if (!p.isBot()) {
            return false; // Human turn
        }

        // Execute Bot Move
        if (game.getPhase() == BatakPhase.BIDDING) {
            int bid = BatakBotAI.calculateBid(p, game.getHighestBid());
            return game.placeBid(turnSeat, bid);
        } else if (game.getPhase() == BatakPhase.SELECTING_TRUMP) {
            Suit suit = BatakBotAI.selectTrump(p);
            return game.selectTrump(turnSeat, suit);
        } else if (game.getPhase() == BatakPhase.PLAYING_TRICKS) {
            List<Card> legalCards = game.getLegalCards(turnSeat);
            Card card = BatakBotAI.selectCardToPlay(p, legalCards, game.getCurrentTrick(), game.getTrumpSuit(), game.isKozBroken());
            if (card != null) {
                return game.playCard(turnSeat, card);
            }
        }

        return false;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getHostId() {
        return hostId;
    }

    public GameType getGameType() {
        return gameType;
    }

    public BatakGame getGame() {
        return game;
    }

    public String[] getSeatSessionIds() {
        return seatSessionIds;
    }

    public List<Map<String, String>> getChatMessages() {
        return chatMessages;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
