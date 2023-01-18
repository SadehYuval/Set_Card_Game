package bguspl.set.ex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assertions;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;

@ExtendWith(MockitoExtension.class)
class DealerTest {

    Player player_0;
    Player player_1;
    Player player_2;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Dealer dealer;
    @Mock
    private Logger logger;
    private Player[] players;





    void assertInvariants() {
        //assertTrue(player.id >= 0);
        //assertTrue(player.score() >= 0);
    }

    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        Env env = new Env(logger, new Config(logger, ""), ui, util);
        table = new Table(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
        player_0 = new Player(env, dealer, table, 0, false);
        player_1 = new Player(env, dealer, table, 1, false);
        player_2 = new Player(env, dealer, table, 2, false);
        players = new Player[3];
        players[0] = player_0;
        players[1] = player_1;
        players[2] = player_2;
        dealer = new Dealer(env, table, players);
        assertInvariants();
    }

    @AfterEach
    void tearDown() {
        assertInvariants();
    }
    @Test
    void findRightWinner(){
        for(int i = 0;i<players.length;i++){
            for(int j = 0; j<i; j++)
                players[i].point();
        }
        int[] expected = {2};
        int[] actual = dealer.announceWinners();
        Assertions.assertArrayEquals(expected, actual);
    }

    @Test
    void addToQueueCheck(){
        dealer.addToQueue(0);
        dealer.addToQueue(1);
        dealer.addToQueue(2);

        assertEquals(0,dealer.dealerQueue.remove());
        assertEquals(1,dealer.dealerQueue.remove());
        assertEquals(2,dealer.dealerQueue.remove());
    }
}
   