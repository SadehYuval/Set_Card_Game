package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    public BlockingQueue<Integer> dealerQueue;
    Thread[] playersThreads;
    Thread dealThread;
    public ReentrantReadWriteLock tableLock = new ReentrantReadWriteLock();

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        playersThreads = new Thread[players.length];
        dealerQueue = new ArrayBlockingQueue<Integer>(players.length, true);
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for(int i =0 ; i<players.length;i++){
            playersThreads[i] = new Thread(players[i]);
            playersThreads[i].start();
        }
        dealThread = Thread.currentThread();
        while (!shouldFinish()) {
            Collections.shuffle(deck);
            placeCardsOnTable();
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            updateTimerDisplay(false);
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        if(!terminate) terminate();
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
        betweenLoops();
    }

    private void betweenLoops(){
        table.tableIsReady.set(false);
        for(Player player : players){
            player.betweenLoops();
        }
        synchronized(dealerQueue){
            dealerQueue.notifyAll();
        }
        dealerQueue.clear();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        
        
        for(int id = players.length-1; id>=0;id --){
            players[id].terminate();
            try{
                playersThreads[id].join();
            }catch(InterruptedException ignore){}
        }
        terminate = true;
        dealThread.interrupt();
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        if(!dealerQueue.isEmpty()){
            int playerID;
            synchronized(dealerQueue){
                playerID = dealerQueue.remove();
            }
            int[] checkIfSet = table.playerChosenCards(playerID);
            if(checkIfSet.length == env.config.featureSize){
                //If it's a relevant set to check
                if(env.util.testSet(checkIfSet)){
                    //If the set is indeed legal
                    tableLock.writeLock().lock();
                    try{
                        table.tableIsReady.set(false);
                        for(int card: checkIfSet){
                            Set<Integer> set = table.tokenBoard.get(table.cardToSlot[card]);
                            for(int id: set){
                                players[id].decreaseToken();
                            }
                            table.removeCard(table.cardToSlot[card]);
                        }
                        players[playerID].point();
                        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
                    }catch (Exception ignored) {}
                    finally{tableLock.writeLock().unlock();};

                }
                //The set was found to be illegal
                else{
                    players[playerID].penalty();
                }
            }
            players[playerID].waitForDealerCheck.set(false);
            synchronized(dealerQueue){
                dealerQueue.notifyAll();
            }
            //Possibly add another display update here
            updateTimerDisplay(false);
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        List<Integer> places = new ArrayList<>();
        for(int j = 0; j<env.config.tableSize;j++)
            places.add(j);
        Collections.shuffle(places);
        if(table.countCards() != env.config.tableSize && !deck.isEmpty()){
            for(int i =0 ;i < env.config.tableSize ;i++){
                if(table.slotToCard[places.get(i)] == null){
                    if(!deck.isEmpty())
                        table.placeCard(deck.remove(0), places.get(i)); 
                }                
            }
        }
        table.tableIsReady.set(true);
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        synchronized(dealerQueue){
            if(dealerQueue.isEmpty()){
                long sleepInterval;
                if(reshuffleTime - System.currentTimeMillis() < env.config.turnTimeoutWarningMillis)
                    sleepInterval = 10;
                else{
                    sleepInterval = (reshuffleTime - System.currentTimeMillis())%1000;
                    for(Player player: players){
                        long playerPenatlyInterval = (player.penaltyTime - System.currentTimeMillis())%1000;
                        if(playerPenatlyInterval > 0 && playerPenatlyInterval < sleepInterval)
                            sleepInterval = playerPenatlyInterval;
                    }
                    sleepInterval = sleepInterval == 0 ? 500:sleepInterval;
                }
               
                try{
                    dealerQueue.wait(sleepInterval);
                }catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        long time = reshuffleTime- System.currentTimeMillis();
        reset = time < env.config.turnTimeoutWarningMillis & time >0;
        env.ui.setCountdown( Math.max(reshuffleTime- System.currentTimeMillis(),0), reset);
        for(Player player: players)
            player.updatePenaltyDisplay();
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        List<Integer> places = new ArrayList<>();
        for(int j = 0; j<env.config.tableSize;j++)
            places.add(j);
        Collections.shuffle(places);
        for(int i =0 ;i < env.config.tableSize ;i++){
            if(table.slotToCard[places.get(i)] != null){
                deck.add(table.slotToCard[places.get(i)]);
                table.removeCard(places.get(i));
            }
        }
        
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    public int[] announceWinners() { 
        int max = -1;
        int winnersAmount = 1;
        for(Player player:players){
            if(player.score()> max){
                max = player.score();
                winnersAmount = 1;
            }
            else if(player.score() == max){
                winnersAmount++;
            }         
        }
        int[] winners = new int[winnersAmount];
        int index = 0;
        for(Player player:players){
            if(player.score() == max){
                winners[index] = player.id;
                index++;
            }
        }
        env.ui.announceWinner(winners);
        return winners;
    }

    public void addToQueue(int playerID){
            dealerQueue.offer(playerID);
            synchronized(dealerQueue){
            //Awake the dealer from sleepUntilWokenOrTimeOut
                dealerQueue.notifyAll();
                while(players[playerID].waitForDealerCheck.get()){
                    try{
                         dealerQueue.wait();
                    }catch (InterruptedException ignored) {}
                }
            }
    }
}
