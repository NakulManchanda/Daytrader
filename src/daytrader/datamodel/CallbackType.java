/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

/**
 * This enumeration provides flags that indicate the callback type. This enumeration
 * may be used in combination with the ICallback interface to create a callback system
 * where a thread can pass data / processing results to another object while using this
 * flag to define the type of data sent / further processing that should be performed.
 * @author Roy
 */
public enum CallbackType {
    
    /**
     * Indicates that the data contains price / time points that should be stored in 
     * a Real Time Run managers primary historic data graph
     */
    HISTORICDATATODAY,
    /**
     * Indicates that the data contains price / time points that should be stored in 
     * a Real Time Run managers previous day data graph and that the final point
     * represents the closing price for the security
     */
    HISTORICDATACLOSEPREVDAY,
    /**
     *Indicates that the data contains price / time points that should be stored in 
     * a Real Time Run managers previous day data graph.
     */
    HISTORICDATAPREVIOUSDAYS,
    /**
     * The associated data element encapsulates an exception that occurred while 
     * requesting historic data. The callback should provide an error handler to
     * process the exception.
     */
    HISTORICDATAERROR,
    /**
     * Indicates that the data contains price / time points, one for each day. The
     * time period covered is not fixed but is usually the previous week. Each day
     * that has a time point can be considered a 'trading day' ie. the stock market
     * was open and trading occurred. Days without time points should be considered
     * as non-trading days. 
     */
    HISTORICDATATRADINGDAYS,
    /**
     * Indicates that the data contains price / time points and that the historic
     * request that generated them completed successfully. 
     */
    HISTORICDATAFINISHED,
    /**
     * Indicates that the data contains price / time points of potential Y-Line
     * start points at a resolution of 1 point per a day
     */
    YLINES1DAYBARS,
    /**
     * Indicates that the data contains price / time points of potential Y-Line
     * start points at a resolution of 1 point per an hour
     */
    YLINES1HOURBARS,
    /**
     * Indicates that the data contains price / time points of potential Y-Line
     * start points at a resolution of 1 point per every fifteen minutes
     */
    YLINES15MINBARS,
    /**
     * Indicates that the data contains price / time points of potential Y-Line
     * start points at a resolution of 1 point per a second (normal historic data
     * resolution)
     */
    YLINES1SECBARS,
    /**
     * Indicates that the Y-Lines generation process is complete and the data should
     * be stored as the securities provisional Y-Lines
     */
    YLINESCOMPLETE,
    /**
     * Indicates that an error occurred while processing / retrieving Y-Line data.
     * The callback should provide an error handler
     */
    YLINESLOADERROR,
    /**
     * Indicates that the data contains price / time points of potential Y-Line
     * start points at a resolution of 1 point per a day (SAME AS YLINES1DAYBARS),
     * but this flag is used to indicate to a Putup that it should cache this data
     * and use it for graph line gradient calculations
     */
    YLINEMONTHCACHE,
    /**
     * Indicates that the data object - A SingleDouble should be marked as invalid and
     * not be considered further.
     */
    REJECTSINGLEDOUBLECLUE;
}
