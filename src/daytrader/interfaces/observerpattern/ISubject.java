/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.interfaces.observerpattern;

/**
 * Interface to be implemented by any class that wishes to be the subject of an
 * observation. See the Observer Design Pattern
 * @author Roy
 */
public interface ISubject {
    
    /**
     * Registers an observer with this class so that a call to notifyObservers will 
     * invoke the update method of that observer
     * @param newObserver - Any object that implements the IObserver interface
     * @return boolean True if the observer was registered and will receive future
     * update notifications, False otherwise
     */
    boolean registerObserver(IObserver newObserver);
    
    /**
     * Removes an observer from the list of registered observers so that it will 
     * not receive future update notifications
     * @param oldObserver - Any object that implements the IObserver interface
     * @return boolean True if the observer was removed from the registered observers
     * list and will not receive future update notifications, False otherwise
     */
    boolean removeObserver(IObserver oldObserver);
    
    /**
     * When invoked this method will call the update() method of any object that is
     * registered with this object to receive update notifications.
     */
    void notifyObservers();
}
