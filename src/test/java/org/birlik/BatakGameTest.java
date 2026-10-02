package org.birlik;

import org.birlik.game.batak.BatakGame;
import org.birlik.game.batak.BatakPhase;
import org.birlik.model.Card;
import org.birlik.model.Suit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BatakGameTest {

    private BatakGame game;

    @BeforeEach
    public void setUp() {
        game = new BatakGame();
    }

    @Test
    public void testGameInitializationAndDealing() {
        game.startNewGame();
        assertEquals(BatakPhase.BIDDING, game.getPhase());
        assertEquals(1, game.getCurrentRound());

        for (int i = 0; i < 4; i++) {
            assertEquals(13, game.getPlayers().get(i).getHand().size());
        }
    }

    @Test
    public void testBiddingAndTrumpSelection() {
        game.startNewGame();

        int bidder = game.getCurrentTurnSeat();
        assertTrue(game.placeBid(bidder, 5));
        assertEquals(5, game.getHighestBid());

        // Other 3 pass
        for (int i = 0; i < 3; i++) {
            int turn = game.getCurrentTurnSeat();
            assertTrue(game.placeBid(turn, 0));
        }

        assertEquals(BatakPhase.SELECTING_TRUMP, game.getPhase());
        assertEquals(bidder, game.getHighestBidderSeat());

        // Select trump
        assertTrue(game.selectTrump(bidder, Suit.SPADES));
        assertEquals(BatakPhase.PLAYING_TRICKS, game.getPhase());
        assertEquals(Suit.SPADES, game.getTrumpSuit());
    }

    @Test
    public void testLegalCardsValidation() {
        game.startNewGame();
        int bidder = game.getCurrentTurnSeat();
        game.placeBid(bidder, 4);
        for (int i = 0; i < 3; i++) {
            game.placeBid(game.getCurrentTurnSeat(), 0);
        }
        game.selectTrump(bidder, Suit.SPADES);

        List<Card> legalCards = game.getLegalCards(bidder);
        assertNotNull(legalCards);
        assertFalse(legalCards.isEmpty());
    }

    @Test
    public void testTrickCompletionAndCollection() {
        game.startNewGame();
        int bidder = game.getCurrentTurnSeat();
        game.placeBid(bidder, 4);
        for (int i = 0; i < 3; i++) {
            game.placeBid(game.getCurrentTurnSeat(), 0);
        }
        game.selectTrump(bidder, Suit.SPADES);

        // 4 players play a card
        for (int i = 0; i < 4; i++) {
            int turn = game.getCurrentTurnSeat();
            List<Card> legal = game.getLegalCards(turn);
            assertFalse(legal.isEmpty());
            assertTrue(game.playCard(turn, legal.get(0)));
        }

        // Now trick should be complete and waiting for collection
        assertTrue(game.isTrickWaitingCollection());
        assertEquals(4, game.getCurrentTrick().getPlayedCards().size());
        assertTrue(game.getTrickWinnerSeat() >= 0 && game.getTrickWinnerSeat() < 4);
        assertNotNull(game.getTrickWinningCard());

        // Collect trick
        int winner = game.getTrickWinnerSeat();
        game.collectCurrentTrick();
        assertFalse(game.isTrickWaitingCollection());
        assertEquals(1, game.getPlayers().get(winner).getTricksWon());
        assertEquals(winner, game.getCurrentTurnSeat());
        assertTrue(game.getCurrentTrick().getPlayedCards().isEmpty());
    }

    @Test
    public void testLeadSuitNoNeedToRaiseIfTrumped() {
        game.startNewGame();
        int seat0 = game.getCurrentTurnSeat();
        int seat1 = (seat0 + 1) % 4;
        int seat2 = (seat0 + 2) % 4;
        int seat3 = (seat0 + 3) % 4;

        game.placeBid(seat0, 4);
        game.placeBid(seat1, 0);
        game.placeBid(seat2, 0);
        game.placeBid(seat3, 0);

        // Trump is SPADES
        game.selectTrump(seat0, Suit.SPADES);

        // Seat 0 plays HEARTS 5 (non-trump)
        Card hearts5 = new Card(Suit.HEARTS, org.birlik.model.Rank.FIVE);
        game.getPlayers().get(seat0).getHand().clear();
        game.getPlayers().get(seat0).getHand().add(hearts5);
        assertTrue(game.playCard(seat0, hearts5));

        // Seat 1 plays SPADES 2 (trumped)
        Card spades2 = new Card(Suit.SPADES, org.birlik.model.Rank.TWO);
        game.getPlayers().get(seat1).getHand().clear();
        game.getPlayers().get(seat1).getHand().add(spades2);
        assertTrue(game.playCard(seat1, spades2));

        // Seat 2 has HEARTS 3 (lower than 5) and HEARTS 10 (higher than 5)
        Card hearts3 = new Card(Suit.HEARTS, org.birlik.model.Rank.THREE);
        Card hearts10 = new Card(Suit.HEARTS, org.birlik.model.Rank.TEN);
        game.getPlayers().get(seat2).getHand().clear();
        game.getPlayers().get(seat2).getHand().add(hearts3);
        game.getPlayers().get(seat2).getHand().add(hearts10);

        List<Card> legalCards = game.getLegalCards(seat2);
        // Since the trick was trumped by Seat 1, Seat 2 does not have to raise above HEARTS 5!
        assertEquals(2, legalCards.size());
        assertTrue(legalCards.contains(hearts3));
        assertTrue(legalCards.contains(hearts10));
    }
}
