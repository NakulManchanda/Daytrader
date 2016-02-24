/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.historicRequestSystem.exceptions;

import daytrader.historicRequestSystem.HRSCallableWrapper;
import daytrader.historicRequestSystem.TWSAccount;

/**
 * This exception is to be thrown when an attempt is made to add a task to a queue
 * that is full
 * @author Roy
 */
public class TaskRejectedQueueFullException extends Exception {
    
    /**
     * The task that was rejected.
     */
    private HRSCallableWrapper task;
    /**
     * The TWSAccount that rejected the task
     */
    private TWSAccount account;
    
    /**
     * Constructs a new exception with null as its detail message.
     */
    public TaskRejectedQueueFullException(){
        super();
    }
    
    /**
     * Constructs a new exception with the specified detail message.
     * @param message - the detail message (which is saved for later retrieval by the Throwable.getMessage() method).
     */
    public TaskRejectedQueueFullException(String message) {
	super(message);
    }
    
    /**
     * Constructs a new exception with the specified detail message and cause.
     * @param message - the detail message (which is saved for later retrieval by the Throwable.getMessage() method).
     * @param cause - the cause (which is saved for later retrieval by the Throwable.getCause() method). (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public TaskRejectedQueueFullException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new exception with the specified cause and a detail message of (cause==null ? null : cause.toString()) (which typically contains the class and detail message of cause). This constructor is useful for exceptions that are little more than wrappers for other throwables (for example, PrivilegedActionException). 
     * @param cause - the cause (which is saved for later retrieval by the Throwable.getCause() method). (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public TaskRejectedQueueFullException(Throwable cause) {
        super(cause);
    }
    
    /**
     * Constructor that records the details of the rejected task and rejecting TWSAccount
     * @param rejectedTask - The HRSCallableWrapper for the rejected task
     * @param rejectingAcc - The TWSAccount that rejected this task
     */
    public TaskRejectedQueueFullException(HRSCallableWrapper rejectedTask, TWSAccount rejectingAcc){
        super();
        this.task = rejectedTask;
        this.account = rejectingAcc;
    }
    
    /**
     * Constructs a new exception with the specified detail message and that records the details of the rejected task and rejecting TWSAccount.
     * @param message - the detail message (which is saved for later retrieval by the Throwable.getMessage() method).
     * @param rejectedTask - The HRSCallableWrapper for the rejected task
     * @param rejectingAcc - The TWSAccount that rejected this task
     */
    public TaskRejectedQueueFullException(String message, HRSCallableWrapper rejectedTask, TWSAccount rejectingAcc) {
	super(message);
        this.task = rejectedTask;
        this.account = rejectingAcc;
    }
    
    /**
     * Constructs a new exception with the specified detail message, cause and that records the details of the rejected task and rejecting TWSAccount..
     * @param message - the detail message (which is saved for later retrieval by the Throwable.getMessage() method).
     * @param cause - the cause (which is saved for later retrieval by the Throwable.getCause() method). (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
     * @param rejectedTask - The HRSCallableWrapper for the rejected task
     * @param rejectingAcc - The TWSAccount that rejected this task
     */
    public TaskRejectedQueueFullException(String message, Throwable cause, HRSCallableWrapper rejectedTask, TWSAccount rejectingAcc) {
        super(message, cause);
        this.task = rejectedTask;
        this.account = rejectingAcc;
    }
    
    /**
     * Constructs a new exception with the specified cause and a detail message of (cause==null ? null : cause.toString()) (which typically contains the class and detail message of cause). This constructor is useful for exceptions that are little more than wrappers for other throwables (for example, PrivilegedActionException). 
     * @param cause - the cause (which is saved for later retrieval by the Throwable.getCause() method). (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
     * @param rejectedTask - The HRSCallableWrapper for the rejected task
     * @param rejectingAcc - The TWSAccount that rejected this task
     */
    public TaskRejectedQueueFullException(Throwable cause, HRSCallableWrapper rejectedTask, TWSAccount rejectingAcc) {
        super(cause);
        this.task = rejectedTask;
        this.account = rejectingAcc;
    }

    /**
     * Accesor method to retrieve the rejected task.
     * @return A HRSCallableWrapper that wraps the rejected task
     */
    public HRSCallableWrapper getTask() {
        return task;
    }

    /**
     * Accesor method to retrieve the TWSAccount that rejected the task.
     * @return A TWSAccount object that rejected the task
     */
    public TWSAccount getAccount() {
        return account;
    }
}
