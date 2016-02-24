/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.historicRequestSystem;

import com.ib.client.EClientSocket;
import daytrader.datamodel.AbstractDataRequest;
import daytrader.datamodel.CallbackType;
import daytrader.gui.HistoricAccountRequestDisplay;
import daytrader.historicRequestSystem.exceptions.TaskRejectedQueueFullException;
import daytrader.interfaces.ICallback;
import daytrader.interfaces.Lockable;
import daytrader.interfaces.XMLPersistable;
import daytrader.interfaces.observerpattern.IObserver;
import daytrader.interfaces.observerpattern.ISubject;
import daytrader.interfaces.observerpattern.ISubjectDelegate;
import daytradertasks.DummyHDT;
import daytradertasks.LoadHistoricDataPointBatchResult;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * This represents an account running on the stock brokers API. It is charged
 * with managing the connection to the stock brokers server and the requests
 * that are currently using that account to receive data from the stock broker
 *
 * @author Roy
 */
public class TWSAccount implements XMLPersistable<TWSAccount>, Lockable, ISubject {

    /**
     * The MAXIMUM number of historic data requests that one trading account is 
     * allowed to make in a 10 minute interval (See page 387 - 388 of the stock brokers
     * API documentation.)
     */
    public static final int MAX_REQUESTS = 60;
    /**
     * No more than this number of requests should be sent to the stock brokers socket
     * connection until previously sent requests have completed
     */
    public static final int MAX_CONCURRENT_EXECUTION = 4;
    /**
     * The String used by the stock brokers server to identify this account
     */
    private String accName;
    /**
     * The port number used by the Trader Workstation Software to connect this
     * account to the stock brokers server.
     */
    private int portNo;
    /**
     * Lock to control multi-threaded access
     */
    private ReentrantLock lock;
    /**
     * The GUI JPanel that shows this accounts status on-screen
     */
    private HistoricAccountRequestDisplay display;
    /**
     * The concrete implementation of the ISubject interface implemented by this class
     * to which the interfaces delegate their functionality
     */
    private ISubjectDelegate iSubjectDelegate;
    /**
     * Each account will be responsible for managing its own submissions
     * therefore each will have a executor pool with a number of threads equal
     * to the MAX_REQUESTS that can be concurrently executing on this account.
     * In addition each must track the number of request submitted in a 10
     * minute period and ensure it does not exceed MAX_REQUESTS hence a queue
     * ordered by submission time will be maintained for all submitted requests
     */
    private ExecutorService pool;
    /**
     * This ExecutorService monitors when tasks complete and removes them, as well
     * as drawing the next task from the task queue and submitting it for execution
     */
    private ExecutorService monitorPool;
    /**
     * The CompletionService from the Java Concurrency Framework that is used to 
     * execute tasks.
     */
    private CompletionService<LoadHistoricDataPointBatchResult> service;
    /**
     * The queue holding the task executed in the last 10 minute period
     */
    private Queue<HRSCallableWrapper> submittedRequests;

    /**
     * This ArrayList holds the futures for each executing task 
     */
    private LinkedList<Future<LoadHistoricDataPointBatchResult>> futures;
    /**
     * This queue holds tasks assigned to this account for execution but which
     * have not yet been executed (normally because the maximum number of 
     * concurrently executing tasks has been reached)
     */
    private Queue<HRSCallableWrapper> internalPendingQueue;
    
    //The TWSAccount is to be made responsible for managing the connection to the server
    //These attributes add that support
    /**
     * A Connection manager responsible for managing connecting and disconnecting
     */
    private ConnectionManager conManager;
    /**
     * The Socket Class provided by the Stock brokers API to connect to their server
     */
    private EClientSocket connectionSocket;
    /**
     * Linked List containing the currently executing tasks.
     */
    private LinkedList<ExecutionRecord> executingRecords;

    /**
     * Default constructor that starts thread pools and runs the monitors to 
     * move tasks between the various processing queues as needed
     */
    public TWSAccount() {
        this.initialise();
    }

    /**
     * Constructor that starts thread pools and runs the monitors to 
     * move tasks between the various processing queues as needed.
     * @param newName - The String containing the TWSAccount name
     * @param newPort - integer being the network port used to connect to the server
     */
    public TWSAccount(String newName, int newPort) {
        this();
        this.accName = newName;
        this.portNo = newPort;
    }

    /**
     * Initialises attributes and starts threads / tasks running
     */
    private void initialise() {
        this.accName = "";
        this.portNo = -1;
        lock = new ReentrantLock();
        this.iSubjectDelegate = new ISubjectDelegate();
        this.display = new HistoricAccountRequestDisplay();
        this.display.setModel(this);
        //The accounts thread pool has a number of threads equal to the maximum concurrently executing tasks
        this.pool = Executors.newFixedThreadPool(MAX_REQUESTS);
        //The monitor pool has two threads
        //one will ALWAYS be running the TaskRemover created below this is charged with obtaining the processing result
        //and making the call back with the result to the user defined call back code
        //The second will be run a task remover which after 10 min will remove the record of the task, freeing space for
        //a new task to be submitted to this accounts task queue.
        this.monitorPool = Executors.newFixedThreadPool(3);
        this.futures = new LinkedList<Future<LoadHistoricDataPointBatchResult>>();
        //this.workQueue = new LinkedBlockingQueue<Future<LoadHistoricDataPointBatchResult>>(MAX_CONCURRENT_EXECUTION);
        //service = new ExecutorCompletionService<LoadHistoricDataPointBatchResult>(pool, this.workQueue);
        service = new ExecutorCompletionService<LoadHistoricDataPointBatchResult>(pool);
        this.submittedRequests = new ArrayBlockingQueue<HRSCallableWrapper>(MAX_REQUESTS);
        this.internalPendingQueue = new ArrayBlockingQueue<HRSCallableWrapper>(MAX_REQUESTS);
        TaskRemover remover = new TaskRemover();
        this.monitorPool.submit(remover);
        ResultConsumer resultProcessor = new ResultConsumer();
        this.monitorPool.submit(resultProcessor);
        InternalQueueTaskTransferer transTask = new InternalQueueTaskTransferer();
        this.monitorPool.submit(transTask);
        
        //Initialise the Connection Manager and the socket
        this.conManager = new ConnectionManager();
        this.connectionSocket = new EClientSocket(conManager);
        this.executingRecords = new LinkedList<ExecutionRecord>();
    }

    /**
     * Tasks are assigned to this account for execution via this method
     * @param task - HRSCallableWrapper that wraps the task to be executed
     * @return boolean True if the task was assigned to a queue for execution
     * false otherwise
     * @throws TaskRejectedQueueFullException - thrown if no free space in the execution
     * queues exists to which the task may be allocated
     */
    public boolean submitRequest(HRSCallableWrapper task) throws TaskRejectedQueueFullException {
        boolean result = false;
        if (null != task) {
            lock.lock();
            try {
                task.setSubmissionTime(System.currentTimeMillis());
                if (this.submittedRequests.offer(task)) {
                    result = true;
                    task.setExecutingAcc(this);
                    try {
                        if (this.futures.size() < MAX_CONCURRENT_EXECUTION) {
                            Future<LoadHistoricDataPointBatchResult> submit = this.service.submit(task);
                            this.futures.add(submit);
                        } else {
                            //Place on the accounts internal queue for later execution
                            if (!this.internalPendingQueue.offer(task)) {
                                this.submittedRequests.remove(task);
                                task.setSubmissionTime(0);
                                throw new TaskRejectedQueueFullException("Processing Queue Full", task, this);
                            }
                        }
                    } catch (Exception ex) {
                        task.setSubmissionTime(0);
                        this.submittedRequests.remove(task);
                        System.err.println("Exception submitting to execution service was: " + ex.getMessage());
                        ex.printStackTrace();
                        System.err.println("BREAK");
                    }
                    this.notifyObservers();
                } else {
                    this.submittedRequests.remove(task);
                    task.setSubmissionTime(0);
                    throw new TaskRejectedQueueFullException("Processing Queue Full", task, this);
                }
            } finally {
                lock.unlock();
            }
        }
        return result;
    }

    /**
     * Accessor method to retrieve the size of the submitted request queue. This
     * value represents the number of requests for data made by this account in the 
     * previous 10 minutes
     * @return - integer the total number of requests in the queue.
     */
    public int getRequestsUsed() {
        int result = 0;
        if (null != this.submittedRequests) {
            lock.lock();
            try {
                result = this.submittedRequests.size();
            } finally {
                lock.unlock();
            }
        }
        return result;
    }

    /**
     * Accessor method to retrieve the total amount of free space available to 
     * accept further tasks for execution.
     * @return - integer being the number of tasks that can be accepted before 
     * the queue is full.
     */
    public int getRemainingRequests() {
        int result = 0;
        if (null != this.submittedRequests) {
            lock.lock();
            try {
                result = MAX_REQUESTS - this.submittedRequests.size();
            } finally {
                lock.unlock();
            }
        }
        return result;
    }

    /**
     * @return the accName
     */
    public String getAccName() {
        return accName;
    }

    /**
     * @param accName the accName to set
     */
    public void setAccName(String accName) {
        this.accName = accName;
    }

    /**
     * @return the portNo
     */
    public int getPortNo() {
        return portNo;
    }

    /**
     * @param portNo the portNo to set
     */
    public void setPortNo(int portNo) {
        this.portNo = portNo;
    }

    /**
     * This method performs a controlled shutdown of this object. Processing
     * queues are terminated (un-executed tasks are lost) and the threads running
     * the monitor tasks for this account are also terminated. Finally the account's
     * socket connection to the stock broker's server is disconnected.
     */
    public void shutdown() {
        this.monitorPool.shutdownNow();
        this.pool.shutdownNow();
        this.disconnect();
    }
    
    /**
     * This method is used to allow Real Time data requests to use the Historic
     * Data Processing system and register themselves as handlers for the real time
     * data returned.
     * @param newReq
     */
    public void registerDataHandler(AbstractDataRequest newReq){
        this.conManager.addAbstractDataRequest(newReq);
    }
    
    /**
     * This method is used to allow Real Time data requests to use the Historic
     * Data Processing system and un-register themselves as handlers for the real time
     * data returned when the request is terminated.
     * @param oldReq - The AbstractDataRequest that made the request for Real Time Data
     */
    public void unregisterDataHandler(AbstractDataRequest oldReq){
        this.conManager.removeAbstractDataRequest(oldReq);
    }

    @Override
    public boolean loadFromXMLStream(XMLEventReader reader, TWSAccount dest) {
        boolean result = false;
        if (null != reader) {
            boolean abort = false;
            if (null == dest) {
                dest = this;
            }
            while (!abort) {
                try {
                    XMLEvent nextEvent = reader.nextEvent();
                    if (nextEvent.isStartElement() || nextEvent.isEndElement()) {
                        if (nextEvent.isStartElement()) {
                            StartElement xmlStartEvent = nextEvent.asStartElement();
                            String name = xmlStartEvent.getName().getLocalPart();
                            if (name.equals("AccName")) {
                                XMLEvent dataEvent = reader.nextEvent();
                                Characters data = dataEvent.asCharacters();
                                String strData = data.toString();
                                dest.setAccName(strData);
                            }

                            if (name.equals("Port")) {
                                XMLEvent dataEvent = reader.nextEvent();
                                Characters data = dataEvent.asCharacters();
                                String strData = data.toString();
                                int newPort = Integer.parseInt(strData);
                                dest.setPortNo(newPort);
                            }
                        }
                        if (nextEvent.isEndElement()) {
                            EndElement xmlEndEvent = nextEvent.asEndElement();
                            String name = xmlEndEvent.getName().getLocalPart();
                            if (name.equals("TWSAccount")) {
                                result = true;
                                abort = true;
                            }
                        }
                    }
                } catch (XMLStreamException ex) {
                    Logger.getLogger(TWSAccount.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return result;
    }

    @Override
    public boolean writeAsXMLToStream(XMLStreamWriter writer) {
        boolean result = false;
        if (null != writer) {
            try {
                writer.writeStartElement("TWSAccount");

                writer.writeStartElement("AccName");
                writer.writeCharacters(this.accName);
                writer.writeEndElement();

                writer.writeStartElement("Port");
                writer.writeCharacters("" + this.portNo);
                writer.writeEndElement();

                writer.writeEndElement();
                result = true;
            } catch (XMLStreamException ex) {
                Logger.getLogger(TWSAccount.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return result;
    }

    @Override
    public void acquireObjectLock() {
        lock.lock();
    }

    @Override
    public void releaseObjectLock() {
        lock.unlock();
    }

    /**
     * Should a pacing violation occur this method should be called on the
     * failing account It immediately fills all remaining slots on this accounts
     * queue with dummy tasks timestamped to expire 10 min after this method is
     * called. The stock brokers API provides no way to measure the number of
     * remaining requests so calls to this method should be used to advise the
     * account that it has used all its remaining requests and must wait 10
     * minutes before accepting more.
     */
    public void recordPacingViolation() {
        lock.lock();
        try {
            //Get current time for pacing violation
            long pacingVolationTime = System.currentTimeMillis();
            //Obtain count of free slots in the request queue
            ArrayBlockingQueue queue = (ArrayBlockingQueue) this.submittedRequests;
            int remainingCapacity = queue.remainingCapacity();
            for (int i = 0; i < remainingCapacity; i++) {
                //Create Dummy task
                DummyHDT dummyTask = new DummyHDT();
                HRSCallableWrapper wrapper = new HRSCallableWrapper(dummyTask);
                wrapper.setSubmissionTime(pacingVolationTime);
                wrapper.setExecutingAcc(this);
                //Add To queue
                try {
                    queue.add(wrapper);
                } catch (IllegalStateException ex) {
                    //Queue is full break out of the loop
                    break;
                }
            }
            this.notifyObservers();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Each TWSAccount has a GUI display associated with it this method retrieves
     * the JPanel used to display activity
     * @return An HistoricAccountRequestDisplay (extends from JPanel) linked to 
     * this TWSAccount.
     */
    public HistoricAccountRequestDisplay getDisplay() {
        return display;
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
     * Retrieves the Stock broker provided socket object that is used to 
     * communicate with the server
     * @return An EClientSocket object used to connect to the stock brokers data
     * server by this account
     */
    public EClientSocket getConnectionSocket() {
        return this.connectionSocket;
    }

    /**
     * Accessor method to retrieve the ConnectionManager responsible for processing
     * data received from the stock brokers server
     * @return the ConnectionManager that manages assigning data to individual request handlers
     */
    public ConnectionManager getConManager() {
        return this.conManager;
    }
    
    /**
     * Disconnects this TWSAccount from the stock brokers server.
     */
    public void disconnect(){
        this.connectionSocket.eDisconnect();
    }

    /**
     * This inner class defines a task that removes the oldest job in the
     * submittedRequests queue after a 10 minute interval (measured in
     * milliseconds). The size of the submittedRequests queue will define how
     * many requests have been processed in the last 10 minute window of time.
     */
    private class TaskRemover implements Callable<Void> {

        //The task must remain in the submitted queue for 10 minutes and then be dropped
        //This is that time in milliseconds
        private long lngHoldInQueueTime = 10 * 60 * 1000;
        private static final boolean DEBUG = false;
        private long SLEEP_TIME = 250;

        public TaskRemover() {
        }

        @Override
        public Void call() throws Exception {
            while (!Thread.currentThread().isInterrupted()) {
                //Peek to see if anything in queue
                HRSCallableWrapper task = submittedRequests.peek();
                if (null != task) {
                    long submissionTime = task.getSubmissionTime();
                    long dropTime = submissionTime + lngHoldInQueueTime;
                    long currTime = System.currentTimeMillis();
                    if (currTime > dropTime) {
                        //Drop this task
                        lock.lock();
                        try {
                            submittedRequests.poll();
                            notifyObservers();
                        } finally {
                            lock.unlock();
                        }
                        if (TaskRemover.DEBUG) {
                            System.out.println("Task dropped at: " + currTime + ", its drop time was: " + dropTime);
                        }
                        //Thread.yield();
                        //Yield time slice
                        Thread.sleep(SLEEP_TIME);
                    } else {
                        if (TaskRemover.DEBUG) {
                            System.out.println("Nothing to remove at " + System.currentTimeMillis() + ", Next drop at: " + dropTime);
                        }
                        //Yield my time slice so other stuff can go on in it
                        //Thread.yield();
                        Thread.sleep(SLEEP_TIME);
                    }
                } else {
                    //Yield my time slice so other stuff can go on in it
                    //Thread.yield();
                    Thread.sleep(SLEEP_TIME);
                }
            }
            if (TaskRemover.DEBUG) {
                System.out.println("Shutdown on account " + getAccName() + ", occurred at " + System.currentTimeMillis());
            }
            return null;
        }
    }

    /**
     * This class transfers jobs from the internalPendingQueue to the execution
     * service. It is needed to ensure that no more than
     * MAX_CONCURRENT_EXECUTION jobs are running at one time. More than 4 jobs
     * running at the same time on one account seems to cause connection errors.
     */
    private class InternalQueueTaskTransferer implements Callable<Void> {

        private long SLEEP_TIME = 250;

        @Override
        public Void call() throws Exception {
            while (!Thread.currentThread().isInterrupted()) {
                //Peek to see if anything in queue
                HRSCallableWrapper task = internalPendingQueue.peek();
                if (null != task) {
                    lock.lock();
                    try {
                        //Ensure that the futures queue is less than MAX_CONCURRENT_EXECUTION in size
                        if (futures.size() < MAX_CONCURRENT_EXECUTION) {
                            //Submit the job to the execution service and store the resulting future
                            task.setSubmissionTime(System.currentTimeMillis());
                            Future<LoadHistoricDataPointBatchResult> submit = service.submit(task);
                            futures.add(submit);
                            internalPendingQueue.remove(task);
                            executingRecords.add(new ExecutionRecord(submit, task));
                        }
                        //Those tasks still held in the pending queue must have submission time updated to avoid being dropped until after actual submission
                        for (HRSCallableWrapper currTask : internalPendingQueue) {
                            currTask.setSubmissionTime(System.currentTimeMillis());
                        }
                    } finally {
                        lock.unlock();
                    }
                }
                //Yield any remaining timeslice for other threads
                //Thread.yield();
                Thread.sleep(SLEEP_TIME);
            }
            return null;
        }
    }

    /**
     * This inner class retrieves the result of executing a task and processes
     * the callback set in the task
     */
    private class ResultConsumer implements Callable<Void> {

        private long SLEEP_TIME = 250;

        @Override
        public Void call() throws Exception {
            try {
                while (!Thread.interrupted()) {
                    Future<LoadHistoricDataPointBatchResult> f = service.poll(2, TimeUnit.SECONDS);
                    if (null != f) {
                        try {
                            LoadHistoricDataPointBatchResult aResult = f.get();
                            //De-register this task with the connection manager
                            for(ExecutionRecord currRec : executingRecords){
                                if(currRec.ifThisFuture(f)){
                                    int reqId = currRec.getCallable().getReqId();
                                    conManager.removeCompletedTask(reqId);
                                    break;
                                }
                            }
                            //As the task has completed drop its future from the futures list
                            futures.remove(f);
                            if (null != aResult) {
                                //Look for a callback, if one exists make the callback with the result and appropriate flag
                                //                                ICallback cbDelegate = aResult.getCbDelegate();
                                //                                if (null != cbDelegate) {
                                //                                    //Send the callback off in a thread of its own so that it is seperate from processing
                                //                                    Thread cbThread = new Thread(new CallbackOperation(aResult.getCallbackType(), aResult, cbDelegate));
                                //                                    cbThread.start();
                                //                                }
                                //Changed to work with a list of callbacks
                                List<ICallback> cbList = aResult.getCbList();
                                if (null != cbList && 0 < cbList.size()) {
                                    for (ICallback currCallback : cbList) {
                                        //Send the callback off in a thread of its own so that it is seperate from processing
                                        Thread cbThread = new Thread(new CallbackOperation(aResult.getCallbackType(), aResult, currCallback));
                                        cbThread.start();
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            //An error has occured do the callback passing a null result and the exception
                            System.err.println("An error occured in executing a task. Message was: " + ex.getMessage());
                        }
                    }
                    Thread.sleep(SLEEP_TIME);
                }
            } catch (Exception ex) {
                //In the case of any exception other than an InterruptedException restart this task
                if (!(ex instanceof InterruptedException)) {
                    ResultConsumer consumer = new ResultConsumer();
                    monitorPool.submit(consumer);
                }
            }
            return null;
        }
    }

    /**
     * To isolate this system from errors that might occur during a callback all
     * callbacks are made in a separate thread. This Java Runnable is used to invoke
     * a call back in that separate thread.
     */
    private class CallbackOperation implements Runnable {

        private CallbackType type;
        private LoadHistoricDataPointBatchResult results;
        private ICallback cbDelegate;

        public CallbackOperation(CallbackType cbType, LoadHistoricDataPointBatchResult newResults, ICallback delegate) {
            this.type = cbType;
            this.results = newResults;
            this.cbDelegate = delegate;
        }

        @Override
        public void run() {
            cbDelegate.callback(type, results);
        }
    }
    
    //This class is used to track the task to which a future belongs and to de-regiser completed tasks
    //with the connection manager
    private class ExecutionRecord {
        
        private Future f;
        private HRSCallableWrapper callable;
        
        public ExecutionRecord(Future newf, HRSCallableWrapper newCallable){
            this.f = newf;
            this.callable = newCallable;
        }
        
        public boolean ifThisFuture(Future targetF){
            boolean result = false;
            if(null != targetF){
                if(this.f.equals(targetF)){
                    result = true;
                }
            }
            return result;
        }

        /**
         * Accessor to retrieve the HRSCallableWrapper that serves as the futures
         * callable task.
         * @return A HRSCallableWrapper object
         */
        public HRSCallableWrapper getCallable() {
            return callable;
        }
        
    }
}
