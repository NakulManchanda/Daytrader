/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.interfaces;

/**
 * Base interface to be implemented by any class that round a value
 * @param <T> Any object that 'is a' number
 * @author Roy
 */
public interface IRoundFunction<T extends Number>{
    
    /**
     * This method applies the rounding defined by the implementing class to the
     * Number object provided and returns the result.
     * @param value - The Number to be rounded
     * @return - The Rounded value
     */
    T performRounding(T value);
    
}
