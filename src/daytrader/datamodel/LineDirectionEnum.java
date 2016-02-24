/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

/**
 * This enumeration represents the gradient of a graph line.
 * It provides a general flag telling the user if the line is going up, down
 * or is horizontal.
 * @author Roy
 */
public enum LineDirectionEnum {
    
    /**
     * Denotes that the GraphLine has a positive gradient
     */
    RISING,
    /**
     * Denotes that the GraphLine has a negative gradient
     */
    FALLING,
    /**
     * Denotes that the GraphLine has a zero gradient
     */
    HORIZONTAL;
}
