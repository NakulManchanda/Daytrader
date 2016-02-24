/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

/**
 * This exception should be thrown by any rule that finds it needs to load more data
 * AFTER it has submitted a request to the Historic Request System. The rule throwing
 * this exception should fail until the data is avaliable
 * @author Roy
 */
public class LoadingAdditionalDataException extends Exception {
    
    /**
     * Constructs a new exception with null as its detail message.
     */
    public LoadingAdditionalDataException() {
	super();
    }
    
    /**
     * Constructs a new exception with the specified detail message.
     * @param message - the detail message (which is saved for later retrieval by the Throwable.getMessage() method).
     */
    public LoadingAdditionalDataException(String message) {
	super(message);
    }
    
    /**
     * Constructs a new exception with the specified detail message and cause.
     * @param message - the detail message (which is saved for later retrieval by the Throwable.getMessage() method).
     * @param cause - the cause (which is saved for later retrieval by the Throwable.getCause() method). (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public LoadingAdditionalDataException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new exception with the specified cause and a detail message of (cause==null ? null : cause.toString()) (which typically contains the class and detail message of cause). This constructor is useful for exceptions that are little more than wrappers for other throwables (for example, PrivilegedActionException). 
     * @param cause - the cause (which is saved for later retrieval by the Throwable.getCause() method). (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public LoadingAdditionalDataException(Throwable cause) {
        super(cause);
    }
    
}
