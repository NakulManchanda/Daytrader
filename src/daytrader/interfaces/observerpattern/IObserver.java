/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.interfaces.observerpattern;

/**
 * Interface to be implemented by any class that will observe others for update 
 * notifications. Part of the Observer Design Pattern implementation see
 * http://en.wikipedia.org/wiki/Observer_pattern for a full description of this 
 * design pattern
 * @author Roy
 */
public interface IObserver {
    
    /**
     * The method containing the code needed to 'update' this object when it 
     * receives a notification to do so.
     */
    void update();
    
}
