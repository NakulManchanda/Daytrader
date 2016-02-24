/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.historicRequestSystem.exceptions;

/**
 * If no free processing queue on any avaliable TWS Account exists this exception 
 * will be thrown. No further requests can be submitted until space is freed in a
 * processing queue.
 * @author Roy
 */
public class AllProcessingQueuesFullException extends Exception {
    
    /**
     * Constructs a new exception with null as its detail message.
     */
    public AllProcessingQueuesFullException() {
	super();
    }
    
    /**
     * Constructs a new exception with the specified detail message.
     * @param message - the detail message (which is saved for later retrieval by the Throwable.getMessage() method).
     */
    public AllProcessingQueuesFullException(String message) {
	super(message);
    }
    
    /**
     * Constructs a new exception with the specified detail message and cause.
     * @param message - the detail message (which is saved for later retrieval by the Throwable.getMessage() method).
     * @param cause - the cause (which is saved for later retrieval by the Throwable.getCause() method). (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public AllProcessingQueuesFullException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new exception with the specified cause and a detail message of (cause==null ? null : cause.toString()) (which typically contains the class and detail message of cause). This constructor is useful for exceptions that are little more than wrappers for other throwables (for example, PrivilegedActionException). 
     * @param cause - the cause (which is saved for later retrieval by the Throwable.getCause() method). (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public AllProcessingQueuesFullException(Throwable cause) {
        super(cause);
    }
}
