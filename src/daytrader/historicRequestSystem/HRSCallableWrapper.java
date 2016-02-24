/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.historicRequestSystem;

import daytrader.interfaces.IHDTCallable;
import daytradertasks.LoadHistoricDataPointBatchResult;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This is a wrapper class designed to wrap any callable and make it look the
 * same to the Historic Data request queue system.
 *
 * @author Roy
 */
public class HRSCallableWrapper implements Callable<LoadHistoricDataPointBatchResult>, Comparable<HRSCallableWrapper> {

    //The callable to submit for execution
    private IHDTCallable myCallable;
    //The priority level for this callable
    private PriorityEnum priority;
    //The result object returned from the callable
    private LoadHistoricDataPointBatchResult resultObject;
    //Creation time - the time this job was created
    private long creationTime;
    //Submission Time - the time this job was submitted to thread pool for execution
    private AtomicLong submissionTime;
    //This attribute will hold the account that this task was submitted on
    private TWSAccount executingAcc;

    /**
     * Constructor accepts any class that implements the IHDTCallable and wraps
     * it to present a common interface to the Historic Request Processing System
     * @param newCallable - A IHDTCallable object this interface extends the Java 
     * Concurrency Framworks Callable<V> interface and can therefore operate as 
     * a Callable task for multi-threaded execution.
     */
    public HRSCallableWrapper(IHDTCallable newCallable) {
        this.myCallable = newCallable;
        this.priority = PriorityEnum.STANDARD;
        this.creationTime = System.currentTimeMillis();
        this.submissionTime = new AtomicLong(0);
    }

    /**
     * Constructor accepts any class that implements the IHDTCallable and wraps
     * it to present a common interface to the Historic Request Processing System.
     * In addition it provides a callback function to be executed using the data
     * loaded by the task.
     * @param newCallable - A IHDTCallable object this interface extends the Java 
     * Concurrency Framworks Callable<V> interface and can therefore operate as 
     * a Callable task for multi-threaded execution.
     * @param newPriority - A callback object to be executed after passing the data
     * loaded by this request
     */
    public HRSCallableWrapper(IHDTCallable newCallable, PriorityEnum newPriority) {
        this(newCallable);
        this.priority = newPriority;
    }

    @Override
    public synchronized LoadHistoricDataPointBatchResult call() throws Exception {
        this.resultObject = null;
        if (this.isValid()) {
            this.resultObject = this.myCallable.call();
        }
        this.notifyAll();
        return this.resultObject;
    }

    /**
     * Test to ensure that a Callable task is wrapped by this object
     * @return boolean True if a Callable task is wrapped by this object, False otherwise
     */
    private boolean isValid() {
        boolean result = false;
        if (null != this.myCallable) {
            result = true;
        }
        return result;
    }

    /**
     * Accessor method to retrieve the priority level assigned to this request
     * @return A PriorityEnum value representing the priority level of this request
     */
    public PriorityEnum getPriority() {
        return priority;
    }

    /**
     * Accessor method to set the priority level assigned to this request
     * @param priority A PriorityEnum value representing the priority level to 
     * assign to this request.
     */
    public void setPriority(PriorityEnum priority) {
        this.priority = priority;
    }

    /**
     * Retrieves the Object that encapsulates the result of this request. This is 
     * almost always an instance of the LoadHistoricDataPointBatchResult but does
     * not have to be.
     * @return An Object usually of the LoadHistoricDataPointBatchResult class
     */
    public Object getResultObject() {
        return resultObject;
    }

    /**
     * Accessor Method to retrieve a flag recording the time that the task was originally 
     * created 
     * @return long being a timestamp of when this task was created.
     */
    public long getCreationTime() {
        return creationTime;
    }

    /**
     * Accessor Method to set a flag recording the time that the task was originally 
     * created 
     * @param creationTime long being a timestamp of when this task was created.
     */
    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    /**
     * Accessor Method to retrieve a flag recording the time that the task was originally 
     * submitted to the Historic Data Request processing system
     * @return long being a timestamp of when this task was submitted for execution.
     */
    public long getSubmissionTime() {
        return this.submissionTime.get();
    }

    /**
     * Accessor Method to set a flag recording the time that the task was originally 
     * submitted to the Historic Data Request processing system
     * @param submissionTime  long being a timestamp of when this task was submitted for execution.
     */
    public void setSubmissionTime(long submissionTime) {
        this.submissionTime.set(submissionTime);
    }

    @Override
    public int compareTo(HRSCallableWrapper o) {
        int result = 0;
        //First test by priority
        int myPriority = this.priority.getNumericType();
        int oPriority = o.getPriority().getNumericType();
        int diff = oPriority - myPriority;
        if (0 != diff) {
            result = diff;
        } else {
            Long timeDiff = o.getCreationTime() - this.creationTime;
            result = timeDiff.intValue();
        }
        //Flip result as I always think backwards on this one
        result *= -1;
        return result;
    }

    /**
     * Accessor Method to retrieve the TWSAccount object that is responsible for executing
     * this request and processing the stock brokers response(s).
     * @return A TWSAccount object that is managing this requests interactions with 
     * the stock brokers server.
     */
    public TWSAccount getExecutingAcc() {
        return executingAcc;
    }

    /**
     * Accessor Method to set the TWSAccount object that is responsible for executing
     * this request and processing the stock brokers response(s).
     * @param executingAcc A TWSAccount object that will manage this requests interactions with 
     * the stock brokers server.
     */
    public void setExecutingAcc(TWSAccount executingAcc) {
        this.executingAcc = executingAcc;
        this.myCallable.setExecutingAcc(executingAcc);
    }
    
    /**
     * Accessor method to retrieve the unique ID assigned to this request
     * @return integer being this requests unique ID number
     */
    public int getReqId(){
        return this.myCallable.getReqId();
    }
}
