/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.interfaces;

import daytrader.datamodel.CallbackType;

/**
 * Abstract concept of a callback identified by a call back type and an associated object
 * which may be used when processing a callback of this type.
 * @author Roy
 */
public interface ICallback {
    
    /**
     * A generic callback function. A callback type is used to select the appropriate
     * response while data for use in processing the callback may be provided via the 
     * optional data object
     * @param type - Enumeration of the CallbackType to be used in determining the action
     * to take in response to the callback
     * @param data - Data to be used when processing the callback may be NULL.
     */
    void callback(CallbackType type, Object data);
    
}