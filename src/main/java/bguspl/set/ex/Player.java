package bguspl.set.ex;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;
    BlockingQueue<Integer> playerQueue;
    AtomicInteger numOfTokens = new AtomicInteger();
    public AtomicBoolean waitForDealerCheck = new AtomicBoolean();
    public long penaltyTime;
    int counter = 0;
    private Dealer dealer;
    private volatile boolean secondaryTermination;



    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        score = 0;
        penaltyTime = System.currentTimeMillis();
        playerQueue = new ArrayBlockingQueue<Integer>(env.config.featureSize, true);
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            /*synchronized(playerQueue){
                while(playerQueue.isEmpty() && !terminate){
                    try{
                        playerQueue.wait();
                    }catch (InterruptedException ignored) {}
                }
            }*/
            actionsInQueue();
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        System.out.println("thread " + Thread.currentThread().getName() + " terminated.");
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        env.logger.info("thread " + Thread.currentThread().getName() + " offered " + counter +"  times");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                Random r = new Random();
                int i = r.nextInt(12);
                keyPressed(i);
                //superAI();
            }
            System.out.println("computer thread " + Thread.currentThread().getName() + " terminated.");
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
            secondaryTermination = true;
        }, "computer-" + (id+1));
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        if(!human) aiThread.interrupt();
        playerThread.interrupt();
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if(table.tableIsReady.get()
            && !waitForDealerCheck.get()
            && penaltyTime - System.currentTimeMillis() <= 0){
                //synchronized(playerQueue){
                    try{
                        playerQueue.put(slot);
                    }catch (InterruptedException ignored) {}
                    //playerQueue.notifyAll();
                //}
            }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        score++;
        env.ui.setScore(id, score);
        penaltyTime = System.currentTimeMillis() + env.config.pointFreezeMillis;
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        penaltyTime = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
    }

    public void decreaseToken(){
        int i;
        do{
            i = numOfTokens.get();
        }while(!numOfTokens.compareAndSet(i,i-1));
    }

    public void increaseToken(){
        int i;
        do{
            i = numOfTokens.get();
        }while(!numOfTokens.compareAndSet(i,i+1));
    }

    public void updatePenaltyDisplay(){
        env.ui.setFreeze(id,penaltyTime-System.currentTimeMillis());
    }

    public int score() {
        return score;
    }

    private void actionsInQueue(){
        //while(!playerQueue.isEmpty()){
            int slot = -1;
            //synchronized(playerQueue){
                try{
                    slot = playerQueue.take();
                }catch (InterruptedException ignored) {}
                
            //}
            dealer.tableLock.readLock().lock();
            try{
                if(slot != -1 && table.slotToCard[slot] != null){
                    if(table.removeToken(id, slot)){
                        decreaseToken();
                    }
                    else if(numOfTokens.get() >= env.config.featureSize){}
                    else{
                        if(table.placeToken(id, slot))
                            increaseToken();
                        if(numOfTokens.get() >= env.config.featureSize){
                            waitForDealerCheck.set(true); 
                            dealer.tableLock.readLock().unlock();
                            //Add to dealer queue and wait until the dealer checks him
                            dealer.addToQueue(id);
                            counter ++;
                            //If the player gets a penalty he waits until the penatly time is done
                            needToWait();
                            playerQueue.clear();
                        }   
                    }             
                }                                
            }catch (Exception ignored) {}
            finally{try{
                    dealer.tableLock.readLock().unlock();
                }catch (IllegalMonitorStateException ignored) {}
            }
        //} 
    }
    
    private synchronized void needToWait(){
        long effectiveWait = penaltyTime - System.currentTimeMillis();
        if(effectiveWait > 0){
            try{
                wait(effectiveWait);
            }catch (InterruptedException ignored) {}
        }
    }

    public void betweenLoops(){
        numOfTokens.set(0);
        playerQueue.clear();
        waitForDealerCheck.set(false);
        penaltyTime = System.currentTimeMillis();
    }

    private void superAI(){
        List<Integer> deck = Arrays.stream(table.slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        List<int[]> hint = env.util.findSets(deck, 1);
        int[] goodSlots = {};
        if(hint.size() > 0)
             goodSlots = hint.get(0);

        for(int press:goodSlots){
            try{
                Thread.sleep(0);
            }catch (InterruptedException ignored) {}
            if(table.cardToSlot[press] != null)
                keyPressed(table.cardToSlot[press]);
        }
    }
}
