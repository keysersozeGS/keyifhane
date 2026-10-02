package org.birlik.game.batak;

import org.birlik.model.Card;
import org.birlik.model.Player;
import org.birlik.model.Rank;
import org.birlik.model.Suit;

import java.util.*;

public class BatakGame {
    private final List<Player> players = new ArrayList<>();
    private BatakPhase phase = BatakPhase.WAITING_FOR_PLAYERS;
    private int dealerSeat = 0;
    private int currentTurnSeat = 0;
    private int highestBidderSeat = -1;
    private int highestBid = 0;
    private Suit trumpSuit = null;
    private boolean kozBroken = false;
    private Trick currentTrick;
    private final List<Trick> tricksHistory = new ArrayList<>();

    private boolean trickWaitingCollection = false;
    private int trickWinnerSeat = -1;
    private Card trickWinningCard = null;

    private int currentRound = 1;
    private int totalRounds = 5;

    private int consecutivePassCount = 0;
    private final boolean[] passedSeats = new boolean[4];

    public BatakGame() {
        for (int i = 0; i < 4; i++) {
            players.add(new Player("bot_" + i, "Bot " + (i + 1), i, true));
        }
    }

    public void addPlayer(int seatIndex, String id, String name, boolean isBot) {
        if (seatIndex >= 0 && seatIndex < 4) {
            Player p = new Player(id, name, seatIndex, isBot);
            p.setScore(players.get(seatIndex).getScore());
            players.set(seatIndex, p);
        }
    }

    public void removePlayer(int seatIndex) {
        if (seatIndex >= 0 && seatIndex < 4) {
            Player bot = new Player("bot_" + seatIndex, "Bot " + (seatIndex + 1), seatIndex, true);
            bot.setScore(players.get(seatIndex).getScore());
            players.set(seatIndex, bot);
        }
    }

    public void startNewGame() {
        this.currentRound = 1;
        this.dealerSeat = 0;
        for (Player p : players) {
            p.setScore(0);
        }
        startRound();
    }

    public void startRound() {
        // Reset state
        highestBidderSeat = -1;
        highestBid = 0;
        trumpSuit = null;
        kozBroken = false;
        tricksHistory.clear();
        consecutivePassCount = 0;
        Arrays.fill(passedSeats, false);
        trickWaitingCollection = false;
        trickWinnerSeat = -1;
        trickWinningCard = null;

        for (Player p : players) {
            p.resetRoundState();
        }

        // Deal cards
        List<Card> deck = createDeck();
        Collections.shuffle(deck);

        for (int i = 0; i < 52; i++) {
            players.get(i % 4).getHand().add(deck.get(i));
        }

        for (Player p : players) {
            p.sortHand();
        }

        // Set phase & first bidder
        phase = BatakPhase.BIDDING;
        currentTurnSeat = (dealerSeat + 1) % 4;
    }

    private List<Card> createDeck() {
        List<Card> deck = new ArrayList<>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(suit, rank));
            }
        }
        return deck;
    }

    public boolean placeBid(int seatIndex, int bidAmount) {
        if (phase != BatakPhase.BIDDING || seatIndex != currentTurnSeat) {
            return false;
        }

        if (bidAmount == 0) { // Pass
            passedSeats[seatIndex] = true;
            players.get(seatIndex).setBid(0);
            consecutivePassCount++;
        } else {
            int minBid = Math.max(4, highestBid + 1);
            if (bidAmount < minBid || bidAmount > 13) {
                return false;
            }
            highestBid = bidAmount;
            highestBidderSeat = seatIndex;
            players.get(seatIndex).setBid(bidAmount);
        }

        // Check if bidding phase ends
        int activeBiddersCount = 0;
        for (int i = 0; i < 4; i++) {
            if (!passedSeats[i]) activeBiddersCount++;
        }

        if (highestBidderSeat != -1 && activeBiddersCount == 1) {
            // Highest bidder won the bid
            endBiddingPhase(highestBidderSeat, highestBid);
            return true;
        }

        if (activeBiddersCount == 0) {
            // Everyone passed! Dealer is forced to bid 4
            int forcedSeat = (dealerSeat + 1) % 4; // or dealerSeat
            endBiddingPhase(forcedSeat, 4);
            return true;
        }

        // Move turn to next non-passed player
        int nextTurn = (currentTurnSeat + 1) % 4;
        while (passedSeats[nextTurn] && activeBiddersCount > 0) {
            nextTurn = (nextTurn + 1) % 4;
        }
        currentTurnSeat = nextTurn;

        return true;
    }

    private void endBiddingPhase(int winningSeat, int winningBid) {
        highestBidderSeat = winningSeat;
        highestBid = winningBid;
        players.get(winningSeat).setBid(winningBid);

        phase = BatakPhase.SELECTING_TRUMP;
        currentTurnSeat = winningSeat;
    }

    public boolean selectTrump(int seatIndex, Suit suit) {
        if (phase != BatakPhase.SELECTING_TRUMP || seatIndex != highestBidderSeat) {
            return false;
        }

        this.trumpSuit = suit;
        this.phase = BatakPhase.PLAYING_TRICKS;
        this.currentTurnSeat = highestBidderSeat;
        this.currentTrick = new Trick(highestBidderSeat);
        return true;
    }

    public List<Card> getLegalCards(int seatIndex) {
        Player player = players.get(seatIndex);
        List<Card> hand = player.getHand();

        if (phase != BatakPhase.PLAYING_TRICKS || seatIndex != currentTurnSeat || hand.isEmpty()) {
            return Collections.emptyList();
        }

        // If leading the trick
        if (currentTrick.getPlayedCards().isEmpty()) {
            if (kozBroken) {
                return new ArrayList<>(hand);
            }
            // Check if player has non-trump cards
            List<Card> nonTrumpCards = new ArrayList<>();
            for (Card c : hand) {
                if (c.getSuit() != trumpSuit) {
                    nonTrumpCards.add(c);
                }
            }
            if (!nonTrumpCards.isEmpty()) {
                return nonTrumpCards;
            }
            // Only has trump cards, so Koz can be led
            return new ArrayList<>(hand);
        }

        // Following trick
        Suit leadSuit = currentTrick.getLeadSuit();

        // 1. Cards matching lead suit
        List<Card> leadSuitCards = new ArrayList<>();
        for (Card c : hand) {
            if (c.getSuit() == leadSuit) {
                leadSuitCards.add(c);
            }
        }

        if (!leadSuitCards.isEmpty()) {
            // Must play lead suit. Try to enforce raising rank if possible,
            // unless the lead suit is not trump and another player already trumped (koz attı / çaktı) the trick.
            boolean trickTrumped = (leadSuit != trumpSuit) && (currentTrick.getHighestCardOfSuit(trumpSuit) != null);
            if (!trickTrumped) {
                Card highestLeadInTrick = currentTrick.getHighestCardOfSuit(leadSuit);
                if (highestLeadInTrick != null) {
                    List<Card> higherLeadCards = new ArrayList<>();
                    for (Card c : leadSuitCards) {
                        if (c.getValue() > highestLeadInTrick.getValue()) {
                            higherLeadCards.add(c);
                        }
                    }
                    if (!higherLeadCards.isEmpty()) {
                        return higherLeadCards;
                    }
                }
            }
            return leadSuitCards;
        }

        // 2. Has no lead suit cards -> Must play Koz (trump)
        List<Card> trumpCards = new ArrayList<>();
        for (Card c : hand) {
            if (c.getSuit() == trumpSuit) {
                trumpCards.add(c);
            }
        }

        if (!trumpCards.isEmpty()) {
            Card highestTrumpInTrick = currentTrick.getHighestCardOfSuit(trumpSuit);
            if (highestTrumpInTrick != null) {
                List<Card> higherTrumpCards = new ArrayList<>();
                for (Card c : trumpCards) {
                    if (c.getValue() > highestTrumpInTrick.getValue()) {
                        higherTrumpCards.add(c);
                    }
                }
                if (!higherTrumpCards.isEmpty()) {
                    return higherTrumpCards;
                }
            }
            return trumpCards;
        }

        // 3. No lead suit & no trump -> Can play any card (çöp)
        return new ArrayList<>(hand);
    }

    public boolean playCard(int seatIndex, Card card) {
        if (phase != BatakPhase.PLAYING_TRICKS || seatIndex != currentTurnSeat || trickWaitingCollection) {
            return false;
        }

        List<Card> legalCards = getLegalCards(seatIndex);
        if (!legalCards.contains(card)) {
            return false;
        }

        Player player = players.get(seatIndex);
        player.removeCard(card);

        if (card.getSuit() == trumpSuit) {
            kozBroken = true;
        }

        currentTrick.addCard(seatIndex, card);

        if (currentTrick.isComplete()) {
            trickWinnerSeat = evaluateTrickWinner(currentTrick);
            trickWinningCard = evaluateWinningCard(currentTrick);
            trickWaitingCollection = true;
        } else {
            currentTurnSeat = (currentTurnSeat + 1) % 4;
        }

        return true;
    }

    public void collectCurrentTrick() {
        if (!trickWaitingCollection || currentTrick == null || !currentTrick.isComplete()) {
            return;
        }

        int winnerSeat = trickWinnerSeat;
        players.get(winnerSeat).incrementTricksWon();
        tricksHistory.add(currentTrick);

        trickWaitingCollection = false;
        trickWinnerSeat = -1;
        trickWinningCard = null;

        if (tricksHistory.size() == 13) {
            // Round completed
            evaluateRoundScores();
            if (currentRound >= totalRounds) {
                phase = BatakPhase.GAME_OVER;
            } else {
                phase = BatakPhase.ROUND_OVER;
            }
        } else {
            // Next trick starts with winner
            currentTurnSeat = winnerSeat;
            currentTrick = new Trick(winnerSeat);
        }
    }

    public Card evaluateWinningCard(Trick trick) {
        Map<Integer, Card> cards = trick.getPlayedCards();
        Suit leadSuit = trick.getLeadSuit();
        Card winningCard = null;

        for (Map.Entry<Integer, Card> entry : cards.entrySet()) {
            Card card = entry.getValue();

            if (winningCard == null) {
                winningCard = card;
                continue;
            }

            boolean cardIsTrump = card.getSuit() == trumpSuit;
            boolean winIsTrump = winningCard.getSuit() == trumpSuit;

            if (cardIsTrump && !winIsTrump) {
                winningCard = card;
            } else if (cardIsTrump && winIsTrump) {
                if (card.getValue() > winningCard.getValue()) {
                    winningCard = card;
                }
            } else if (!cardIsTrump && !winIsTrump) {
                if (card.getSuit() == leadSuit && winningCard.getSuit() == leadSuit) {
                    if (card.getValue() > winningCard.getValue()) {
                        winningCard = card;
                    }
                }
            }
        }
        return winningCard;
    }

    public int evaluateTrickWinner(Trick trick) {
        Map<Integer, Card> cards = trick.getPlayedCards();
        Card winningCard = evaluateWinningCard(trick);
        for (Map.Entry<Integer, Card> entry : cards.entrySet()) {
            if (entry.getValue().equals(winningCard)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    private void evaluateRoundScores() {
        for (Player p : players) {
            int won = p.getTricksWon();
            if (p.getSeatIndex() == highestBidderSeat) {
                if (won >= highestBid) {
                    p.addScore(won); // Took contract or more
                } else {
                    p.addScore(-highestBid); // Failed contract!
                }
            } else {
                if (won == 0) {
                    p.addScore(-highestBid); // Batak penalty for 0 tricks
                } else {
                    p.addScore(won);
                }
            }
        }
    }

    public void nextRound() {
        if (phase == BatakPhase.ROUND_OVER) {
            currentRound++;
            dealerSeat = (dealerSeat + 1) % 4;
            startRound();
        }
    }

    // Getters
    public List<Player> getPlayers() {
        return players;
    }

    public BatakPhase getPhase() {
        return phase;
    }

    public int getDealerSeat() {
        return dealerSeat;
    }

    public int getCurrentTurnSeat() {
        return currentTurnSeat;
    }

    public int getHighestBidderSeat() {
        return highestBidderSeat;
    }

    public int getHighestBid() {
        return highestBid;
    }

    public Suit getTrumpSuit() {
        return trumpSuit;
    }

    public boolean isKozBroken() {
        return kozBroken;
    }

    public Trick getCurrentTrick() {
        return currentTrick;
    }

    public List<Trick> getTricksHistory() {
        return tricksHistory;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public int getTotalRounds() {
        return totalRounds;
    }

    public void setTotalRounds(int totalRounds) {
        this.totalRounds = totalRounds;
    }

    public boolean isTrickWaitingCollection() {
        return trickWaitingCollection;
    }

    public int getTrickWinnerSeat() {
        return trickWinnerSeat;
    }

    public Card getTrickWinningCard() {
        return trickWinningCard;
    }
}
