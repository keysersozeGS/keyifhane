package org.birlik.game.batak;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.birlik.model.Card;
import org.birlik.model.Player;
import org.birlik.model.Suit;

public class BatakBotAI {

    public static int calculateBid(Player player, int currentHighestBid) {
        List<Card> hand = player.getHand();
        Map<Suit, Integer> suitCounts = new HashMap<>();
        Map<Suit, Integer> suitHighCardPoints = new HashMap<>();

        for (Suit suit : Suit.values()) {
            suitCounts.put(suit, 0);
            suitHighCardPoints.put(suit, 0);
        }

        for (Card card : hand) {
            Suit s = card.getSuit();
            suitCounts.put(s, suitCounts.get(s) + 1);
            if (card.getValue() == 14) {
                suitHighCardPoints.put(s, suitHighCardPoints.get(s) + 3); // Ace
            } else if (card.getValue() == 13) {
                suitHighCardPoints.put(s, suitHighCardPoints.get(s) + 2); // King
            } else if (card.getValue() == 12) {
                suitHighCardPoints.put(s, suitHighCardPoints.get(s) + 1); // Queen

            }
        }

        int maxTrumpCount = 0;
        Suit bestSuit = Suit.SPADES;
        for (Suit s : Suit.values()) {
            int count = suitCounts.get(s);
            if (count > maxTrumpCount) {
                maxTrumpCount = count;
                bestSuit = s;
            }
        }

        // Estimate tricks: high card points in best suit + length of best suit above 4 + aces in other suits
        int totalPts = suitHighCardPoints.get(bestSuit);
        int lengthBonus = Math.max(0, maxTrumpCount - 3);

        int otherAces = 0;
        for (Suit s : Suit.values()) {
            if (s != bestSuit && suitHighCardPoints.get(s) >= 3) {
                otherAces++;
            }
        }

        int estimatedTricks = Math.min(11, (totalPts / 2) + lengthBonus + otherAces);

        int minBid = Math.max(4, currentHighestBid + 1);
        if (estimatedTricks >= minBid) {
            return Math.min(10, Math.max(minBid, estimatedTricks));
        }

        return 0; // Pass
    }

    public static Suit selectTrump(Player player) {
        List<Card> hand = player.getHand();
        Map<Suit, Integer> suitCounts = new EnumMap<>(Suit.class);
        Map<Suit, Integer> suitValues = new EnumMap<>(Suit.class);

        for (Suit s : Suit.values()) {
            suitCounts.put(s, 0);
            suitValues.put(s, 0);
        }

        for (Card c : hand) {
            suitCounts.put(c.getSuit(), suitCounts.get(c.getSuit()) + 1);
            suitValues.put(c.getSuit(), suitValues.get(c.getSuit()) + c.getValue());
        }

        Suit bestSuit = Suit.SPADES;
        int maxCount = -1;
        int maxValue = -1;

        for (Suit s : Suit.values()) {
            int count = suitCounts.get(s);
            int val = suitValues.get(s);
            if (count > maxCount || (count == maxCount && val > maxValue)) {
                maxCount = count;
                maxValue = val;
                bestSuit = s;
            }
        }

        return bestSuit;
    }

    public static Card selectCardToPlay(Player player, List<Card> legalCards, Trick currentTrick, Suit trumpSuit, boolean kozBroken) {
        if (legalCards == null || legalCards.isEmpty()) {
            return null;
        }

        if (legalCards.size() == 1) {
            return legalCards.get(0);
        }

        // If leading trick
        if (currentTrick.getPlayedCards().isEmpty()) {
            // Try playing non-trump Ace first
            for (Card c : legalCards) {
                if (c.getSuit() != trumpSuit && c.getValue() == 14) {
                    return c;
                }
            }
            // Otherwise play highest card of longest non-trump suit
            Card bestLead = legalCards.get(0);
            for (Card c : legalCards) {
                if (c.getSuit() != trumpSuit && c.getValue() > bestLead.getValue()) {
                    bestLead = c;
                }
            }
            return bestLead;
        }

        // Following trick: check if we can win
        Card currentWinnerCard = getWinningCardInTrick(currentTrick, trumpSuit);

        List<Card> winningLegalCards = new ArrayList<>();
        for (Card c : legalCards) {
            if (wouldCardBeat(c, currentWinnerCard, currentTrick.getLeadSuit(), trumpSuit)) {
                winningLegalCards.add(c);
            }
        }

        if (!winningLegalCards.isEmpty()) {
            // Play lowest winning card to save high cards
            winningLegalCards.sort(Comparator.comparingInt(Card::getValue));
            return winningLegalCards.get(0);
        }

        // If can't win, play lowest value legal card (throw junk)
        legalCards.sort(Comparator.comparingInt(Card::getValue));
        return legalCards.get(0);
    }

    private static Card getWinningCardInTrick(Trick trick, Suit trumpSuit) {
        Card winningCard = null;
        for (Card card : trick.getPlayedCards().values()) {
            if (winningCard == null || wouldCardBeat(card, winningCard, trick.getLeadSuit(), trumpSuit)) {
                winningCard = card;
            }
        }
        return winningCard;
    }

    private static int getWinningSeatInTrick(Trick trick, Suit trumpSuit) {
        Card winningCard = null;
        int winningSeat = -1;
        for (Map.Entry<Integer, Card> entry : trick.getPlayedCards().entrySet()) {
            if (winningCard == null || wouldCardBeat(entry.getValue(), winningCard, trick.getLeadSuit(), trumpSuit)) {
                winningCard = entry.getValue();
                winningSeat = entry.getKey();
            }
        }
        return winningSeat;
    }

    private static boolean wouldCardBeat(Card newCard, Card currentWinningCard, Suit leadSuit, Suit trumpSuit) {
        if (currentWinningCard == null) {
            return true;
        }

        boolean newIsTrump = newCard.getSuit() == trumpSuit;
        boolean winIsTrump = currentWinningCard.getSuit() == trumpSuit;

        if (newIsTrump && !winIsTrump) {
            return true;
        }
        if (!newIsTrump && winIsTrump) {
            return false;
        }

        if (newIsTrump && winIsTrump) {
            return newCard.getValue() > currentWinningCard.getValue();
        }

        // Neither is trump
        if (newCard.getSuit() == leadSuit && currentWinningCard.getSuit() == leadSuit) {
            return newCard.getValue() > currentWinningCard.getValue();
        }

        return false;
    }
}
