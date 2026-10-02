package org.birlik.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Player {
    private String id;
    private String name;
    private int seatIndex; // 0, 1, 2, 3
    private boolean isBot;
    private boolean connected;
    private List<Card> hand = new ArrayList<>();
    private int bid = -1; // -1: not set, 0: pass, 4-13: bid amount
    private int tricksWon = 0;
    private int score = 0;

    public Player(String id, String name, int seatIndex, boolean isBot) {
        this.id = id;
        this.name = name;
        this.seatIndex = seatIndex;
        this.isBot = isBot;
        this.connected = true;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSeatIndex() {
        return seatIndex;
    }

    public void setSeatIndex(int seatIndex) {
        this.seatIndex = seatIndex;
    }

    public boolean isBot() {
        return isBot;
    }

    public void setBot(boolean bot) {
        isBot = bot;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public List<Card> getHand() {
        return hand;
    }

    public void setHand(List<Card> hand) {
        this.hand = new ArrayList<>(hand);
        sortHand();
    }

    public void sortHand() {
        Collections.sort(this.hand);
    }

    public int getBid() {
        return bid;
    }

    public void setBid(int bid) {
        this.bid = bid;
    }

    public int getTricksWon() {
        return tricksWon;
    }

    public void setTricksWon(int tricksWon) {
        this.tricksWon = tricksWon;
    }

    public void incrementTricksWon() {
        this.tricksWon++;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void addScore(int points) {
        this.score += points;
    }

    public void removeCard(Card card) {
        this.hand.remove(card);
    }

    public void resetRoundState() {
        this.hand.clear();
        this.bid = -1;
        this.tricksWon = 0;
    }
}
