/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.historicRequestSystem;

import daytrader.gui.HistoricAccRequestPendingDisplay;
import daytrader.gui.HistoricAccountRequestDisplay;
import daytrader.historicRequestSystem.exceptions.AllProcessingQueuesFullException;
import daytrader.historicRequestSystem.exceptions.TaskRejectedQueueFullException;
import daytrader.interfaces.observerpattern.IObserver;
import daytrader.interfaces.observerpattern.ISubject;
import daytrader.interfaces.observerpattern.ISubjectDelegate;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import javax.swing.JFrame;

/**
 * This class is a singleton that may be used to submit requests for historic
 * data. Its primary task is to prevent pacing violations and manage errors
 * communicating with the stock broker API. For details of the singleton
 * design pattern see http://en.wikipedia.org/wiki/Singleton_pattern
 *
 * @author Roy
 */
public class HistoricRequestProcessingSystem implements ISubject {

    //Singleton reference to self
    private static HistoricRequestProcessingSystem self;
    //The list of accounts that can be used to submit requests
    private TWSAccountList accounts;
    //These attributes represent the queues
    private Queue<HRSCallableWrapper> pendingRequests;                          //Requests awaiting processing are held here
    private ExecutorService pool;                                               //Executor to run thread that moves pending requests to an account for execution
    //Lock for thread safety
    private ReentrantLock lock;
    private HistoricAccRequestPendingDisplay display;
    private ISubjectDelegate iSubjectDelegate;

    /**
     * Retrieves the one instance of this class (creating it if necessary)
     *
     * @return The singleton HistoricRequestProcessingSystem object
     */
    public static HistoricRequestProcessingSystem getInstance() {
        if (null == self) {
            self = new HistoricRequestProcessingSystem();
        }
        return self;
    }

    /**
     * This method submits a new request for data to the processing system. It will
     * execute as soon as an account is available that can handle the request and
     * it has reached the top of the systems priority queue.
     * @param task - The data request to run as an asynchronous operation
     * @return boolean True if the request was accepted and added to the processing 
     * systems 'Pending' queue. False otherwise.
     */
    public boolean submitRequest(HRSCallableWrapper task) {
        boolean result = false;
        if (null != task) {
            lock.lock();
            try {
                result = this.pendingRequests.offer(task);
                if (result) {
                    this.notifyObservers();
                }
            } finally {
                lock.unlock();
            }
        }
        return result;
    }

    /**
     * Accessor method to retrieve the size in terms of unprocessed requests of
     * the Historic Processing Systems pending request queue.
     * @return integer being the number of pending requests held in the system.
     */
    public int getPendingRequestCount() {
        int result = 0;
        lock.lock();
        try {
            result = this.pendingRequests.size();
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Without a proper shutdown procedure a Singleton will result in a memory leak.
     * (Yes this will occur even though the Garbage Collector manages memory in Java)
     * This method provides a means to safely shutdown the Historic Request Processing System
     * and make it available for garbage collection in the usual way.
     * This method should be invoked at application exit to properly clean up memory.
     */
    public void shutdown() {
        //Do Queue and executor shutdown here
        if (null != this.pool) {
            this.pool.shutdown();
            this.pool = null;
        }
        //Shutdown all thread pools in all accounts
        for (TWSAccount currAcc : this.accounts) {
            currAcc.shutdown();
        }
        //Release reference to singleton for garbage collection
        self = null;
    }

    /**
     * Default Constructor can only be called by the factory method responsible for
     * object creation.
     */
    private HistoricRequestProcessingSystem() {
        this.iSubjectDelegate = new ISubjectDelegate();
        this.lock = new ReentrantLock();
        this.pool = Executors.newFixedThreadPool(1);
        this.startMonitor();
        //Load the accounts list
        this.accounts = TWSAccountList.loadAccountsList();
        //Now show a monitor window for each account
        for (TWSAccount currAccount : this.accounts) {
            HistoricAccountRequestDisplay accDisplay = currAccount.getDisplay();
            JFrame window = new JFrame();
            window.add(accDisplay);
            window.pack();
            window.setVisible(true);
        }
        //Pending requests is an unbounded jave queue (we may queue as many requests as we like)
        //A Priority queue is used with the HRSCallableWrapper defining a natural ordering
        //based on the priority level and time when the task was created
        this.pendingRequests = new PriorityQueue<HRSCallableWrapper>();

        this.display = new HistoricAccRequestPendingDisplay();
        this.display.setModel(this);
        JFrame window = new JFrame();
        window.add(display);
        window.setTitle("Pending Historic Request Queue");
        window.pack();
        window.setVisible(true);
    }

    /**
     * Starts an internal task that monitors the pending requests queue and assigns
     * the requests to a TWSAccount for execution as they become available.
     */
    private void startMonitor() {
        ProcessPendingRequestsTask newMonitor = new ProcessPendingRequestsTask();
        this.pool.submit(newMonitor);
    }

    /**
     * Accessor Method to test if the Historic Request Processing System has been
     * initialised and therefore if a shutdown() call should be made at application
     * exit to release memory - You cannot rely on Java's memory management to
     * do this correctly.
     * @return boolean True if an object of this class has been instanciated, 
     * False otherwise.
     */
    public static boolean isInitialised() {
        boolean result = false;
        if (null != HistoricRequestProcessingSystem.self) {
            result = true;
        }
        return result;
    }

    /**
     * Retrieves the TWSAccount with the MOST REMAINING REQUESTS. Hence it
     * identifies the BEST account to make a historic request on.
     *
     * @throws An AllProcessingQueuesFullException if no account can be found
     * with at least one free request.
     */
    private TWSAccount getNextTWSAccount() throws AllProcessingQueuesFullException {
        TWSAccount result = null;
        if (null != this.accounts) {
            lock.lock();
            this.accounts.acquireObjectLock();
            try {
                TWSAccount leastUsedAcc = this.accounts.getLeastUsedAcc();
                if (null != leastUsedAcc) {
                    result = leastUsedAcc;
                }
            } finally {
                this.accounts.releaseObjectLock();
                lock.unlock();
            }
        }
        return result;
    }

    /**
     * Gets a list of the TWSAccount objects currently available for processing requests
     * @return A TWSAccountList object that encapsulates the individual TWSAccounts
     * that are logged in and available for use by the system
     */
    public TWSAccountList getAccounts() {
        TWSAccountList result = new TWSAccountList();
        lock.lock();
        try {
            for (TWSAccount currAcc : this.accounts) {
                result.addAccount(currAcc);
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public boolean registerObserver(IObserver newObserver) {
        return this.iSubjectDelegate.registerObserver(newObserver);
    }

    @Override
    public boolean removeObserver(IObserver oldObserver) {
        return this.iSubjectDelegate.removeObserver(oldObserver);
    }

    @Override
    public void notifyObservers() {
        this.iSubjectDelegate.notifyObservers();
    }

    /**
     * Inner Class - A Callable that monitors the pending requests queue and
     * submits requests to a TWS account when one is avaliable to process it.
     */
    private class ProcessPendingRequestsTask implements Callable<Void> {

        private static final boolean DEBUG = true;

        @Override
        public Void call() throws Exception {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        lock.lock();
                        if (null != pendingRequests) {
                            if (0 < pendingRequests.size()) {
                                HRSCallableWrapper nextReq = pendingRequests.peek();
                                if (null != nextReq) {
                                    try {
                                        TWSAccount usableAcc = getNextTWSAccount();
                                        if (null != usableAcc) {
                                            //Try to submit request
                                            try {
                                                if (usableAcc.submitRequest(nextReq)) {
                                                    //Submitted remove from task from pending queue
                                                    pendingRequests.poll();
                                                    notifyObservers();
                                                }
                                            } catch (TaskRejectedQueueFullException ex) {
                                                //Error (should never get here) but as damage control yield time slice
                                                if (DEBUG) {
                                                    System.err.println("Task Rejected when account reported space avaliable");
                                                }
                                            }
                                        } else {
                                            throw new AllProcessingQueuesFullException();
                                        }
                                    } catch (AllProcessingQueuesFullException ex) {
                                        //All processing queues are full must wait for
                                        //capacity to be freed. yield the time slice
                                    }
                                }
                            }
                        }
                        //Finally here
                    } finally {
                        lock.unlock();
                        Thread.yield();
                    }
                }
            } catch (Exception ex) {
                //The only way to reach this point SHOULD BE to interrupt the thread (in which case this is a shutdown)
                //However an uncaught exception would also break the above while loop. If this happens the monitor
                //task must restart itself
                if (!(ex instanceof InterruptedException)) {
                    ProcessPendingRequestsTask restartTask = new ProcessPendingRequestsTask();
                    pool.submit(restartTask);
                    if (DEBUG) {
                        System.out.println("Restarted ProcessPendingRequestsTask");
                    }
                }
            }
            return null;
        }
    }
}
