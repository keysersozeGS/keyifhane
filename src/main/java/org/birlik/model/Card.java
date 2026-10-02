package org.birlik.model;

import java.util.Objects;

public class Card implements Comparable<Card> {
    private final Suit suit;
    private final Rank rank;
    private final String id;

    public Card(Suit suit, Rank rank) {
        this.suit = suit;
        this.rank = rank;
        this.id = suit.name().substring(0, 1) + rank.getValue();
    }

    public Suit getSuit() {
        return suit;
    }

    public Rank getRank() {
        return rank;
    }

    public String getId() {
        return id;
    }

    public int getValue() {
        return rank.getValue();
    }

    @Override
    public int compareTo(Card o) {
        if (this.suit != o.suit) {
            return this.suit.compareTo(o.suit);
        }
        return Integer.compare(this.rank.getValue(), o.rank.getValue());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        return suit == card.suit && rank == card.rank;
    }

    @Override
    public int hashCode() {
        return Objects.hash(suit, rank);
    }

    @Override
    public String toString() {
        return suit.getSymbol() + rank.getLabel();
    }
}
