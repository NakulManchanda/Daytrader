/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.interfaces;

/**
 * This interface is implemented by a thread safe class that can be locked against
 * changes from other threads until its lock is released
 * @author Roy
 */
public interface Lockable {
    
    /**
     * Allows the calling thread to lock the object against changes from other 
     * threads. A call to this function that completes successfully MUST be followed
     * by a call to releaseObjectLock at a later point in the code or the locked
     * object will be inaccessible by any other thread. (A deadlock is likely if
     * the lock is not released).
     */
    void acquireObjectLock();
    
    /**
     * Allows the calling thread to release the lock on the object if it is holding it.
     * Calls to this method may safely be made even if the object is not locked by
     * this thread. The method guarantees that the calling thread will not hold the
     * object lock when it completes.
     */
    void releaseObjectLock();
    
}
