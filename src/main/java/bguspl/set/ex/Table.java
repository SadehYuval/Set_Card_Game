package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)
    protected final List<Set<Integer>> tokenBoard;
    public AtomicBoolean tableIsReady = new AtomicBoolean();

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        tokenBoard = new ArrayList<Set<Integer>>(env.config.tableSize);
        for(int i=0; i<env.config.tableSize; i++){
            tokenBoard.add(i, new HashSet<Integer>());
        }
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        Set<Integer> currentSet = tokenBoard.get(slot);
        synchronized(currentSet){
            try {
                Thread.sleep(env.config.tableDelayMillis);
            } catch (InterruptedException ignored) {}
            cardToSlot[card] = slot;
            slotToCard[slot] = card;
            env.ui.placeCard(card, slot);
        }
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        Set<Integer> currentSet = tokenBoard.get(slot);
        synchronized(currentSet){
            int card = slotToCard[slot];
            slotToCard[slot]=null;
            cardToSlot[card]=null;
            try {
                Thread.sleep(env.config.tableDelayMillis);
            } catch (InterruptedException ignored) {}
            Set<Integer> set = tokenBoard.get(slot);
            for(int id: set){
                env.ui.removeToken(id, slot);
            }
            set.clear();
            env.ui.removeCard(slot);
        }
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public boolean placeToken(int player, int slot) {
        Set<Integer> currentSet = tokenBoard.get(slot);
        synchronized(currentSet){
            if(tableIsReady.get() && slotToCard[slot] != null){
                currentSet.add(player);
                env.ui.placeToken(player, slot);
                return true;
            }
            return false;
        }
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        Set<Integer> currentSet = tokenBoard.get(slot);
        boolean ans;
        synchronized(currentSet){
            ans = currentSet.remove(player);
            if(ans){
                env.ui.removeToken(player, slot);
            }
        }
        return ans;
    }

    public boolean hasToken(int player, int slot){
        return tokenBoard.get(slot).contains(player);
    }


    public int[] playerChosenCards(int player){
        List<Integer> ans = new LinkedList<Integer>();
        int j = 0;
        for(Set<Integer> set:tokenBoard){
            if(set.contains(player)){
                ans.add(slotToCard[j]);
            }
            j++;    
        }
        int[] realAns = new int[ans.size()];
        for(int i = 0; i< realAns.length; i++){
            realAns[i] = ans.get(i);
        }
        return realAns;
    }
}
