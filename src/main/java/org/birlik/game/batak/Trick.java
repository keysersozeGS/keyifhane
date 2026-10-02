package org.birlik.game.batak;

import org.birlik.model.Card;
import org.birlik.model.Suit;

import java.util.LinkedHashMap;
import java.util.Map;

public class Trick {
    private final int starterSeat;
    private Suit leadSuit;
    private final Map<Integer, Card> playedCards = new LinkedHashMap<>(); // seatIndex -> Card

    public Trick(int starterSeat) {
        this.starterSeat = starterSeat;
    }

    public int getStarterSeat() {
        return starterSeat;
    }

    public Suit getLeadSuit() {
        return leadSuit;
    }

    public Map<Integer, Card> getPlayedCards() {
        return playedCards;
    }

    public void addCard(int seatIndex, Card card) {
        if (playedCards.isEmpty()) {
            this.leadSuit = card.getSuit();
        }
        playedCards.put(seatIndex, card);
    }

    public Card getHighestCardOfSuit(Suit suit) {
        Card highest = null;
        for (Card card : playedCards.values()) {
            if (card.getSuit() == suit) {
                if (highest == null || card.getValue() > highest.getValue()) {
                    highest = card;
                }
            }
        }
        return highest;
    }

    public boolean isComplete() {
        return playedCards.size() == 4;
    }
}
