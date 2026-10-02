package org.birlik.model;

public enum Suit {
    SPADES("♠", "Maça", "black"),
    HEARTS("♥", "Kupa", "red"),
    CLUBS("♣", "Sinek", "black"),
    DIAMONDS("♦", "Karo", "red");

    private final String symbol;
    private final String nameTr;
    private final String color;

    Suit(String symbol, String nameTr, String color) {
        this.symbol = symbol;
        this.nameTr = nameTr;
        this.color = color;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getNameTr() {
        return nameTr;
    }

    public String getColor() {
        return color;
    }
}
