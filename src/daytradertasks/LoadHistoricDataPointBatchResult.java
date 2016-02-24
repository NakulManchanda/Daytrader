/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytradertasks;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.CallbackType;
import daytrader.datamodel.Putup;
import daytrader.interfaces.ICallback;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * This class encapsulates the result of the LoadHistoricDataPointBatchTask. It
 * contains the put up that was loaded and the tree set of loaded points
 *
 * @author Roy
 */
public class LoadHistoricDataPointBatchResult {

    /**
     * The putup encapsulating the stock market security to which this result relates
     */
    public Putup putup;
    /**
     * A TreeSet containing the time ordered sequence of Price / Time points loaded
     * by the system - ie. the result from the stock brokers data server
     */
    public TreeSet<AbstractGraphPoint> loadedPoints;
    private ICallback cbDelegate;
    private List<ICallback> cbList;
    private Exception execException;
    private CallbackType callbackType;

    /**
     * Constructor creates a result object that defines a result object containing
     * the provided putup and set of Price / Time point data that was loaded
     * @param newPutup - A Putup representing a stock market security to which this data relates
     * @param newPoints - The new set of historic Price / Time point data received 
     * from the stock brokers server.
     */
    public LoadHistoricDataPointBatchResult(Putup newPutup, TreeSet<AbstractGraphPoint> newPoints) {
        this.putup = newPutup;
        this.loadedPoints = newPoints;
        this.callbackType = CallbackType.HISTORICDATAFINISHED;
        this.cbList = new ArrayList<ICallback>();
    }

    /**
     * Constructor creates a result object that defines a result object containing
     * the provided putup and set of Price / Time point data that was loaded. The user also
     * provides a callback to which this object should be passed as a data element
     * @param newPutup - A Putup representing a stock market security to which this data relates
     * @param newPoints - The new set of historic Price / Time point data received 
     * from the stock brokers server.
     * @param newCallback - The callback that should be made by the Historic Data 
     * Request System passing this object as its result.
     */
    public LoadHistoricDataPointBatchResult(Putup newPutup, TreeSet<AbstractGraphPoint> newPoints, ICallback newCallback) {
        this(newPutup, newPoints);
        this.cbDelegate = newCallback;
        this.callbackType = CallbackType.HISTORICDATAFINISHED;
    }

    /**
     * Constructor creates a result object that defines a result object containing
     * the provided putup and set of Price / Time point data that was loaded. The user also
     * provides a callback to which this object should be passed as a data element
     * @param newPutup - A Putup representing a stock market security to which this data relates
     * @param newPoints - The new set of historic Price / Time point data received 
     * from the stock brokers server.
     * @param newCallback - The callback that should be made by the Historic Data 
     * Request System passing this object as its result.
     * @param newType - The type flag to be used when making the callback as defined in the
     * CallbackType enumeration class
     */
    public LoadHistoricDataPointBatchResult(Putup newPutup, TreeSet<AbstractGraphPoint> newPoints, ICallback newCallback, CallbackType newType) {
        this(newPutup, newPoints, newCallback);
        this.callbackType = newType;
    }
    
    /**
     * Constructor creates a result object that defines a result object containing
     * the provided putup and set of Price / Time point data that was loaded. The user also
     * provides a callback to which this object should be passed as a data element
     * @param newPutup - A Putup representing a stock market security to which this data relates
     * @param newPoints - The new set of historic Price / Time point data received 
     * from the stock brokers server.
     * @param newCallback - The callback that should be made by the Historic Data 
     * Request System passing this object as its result.
     * @param extraCallbacks A list of additional callbacks that should be made over and above 
     * the primary callback. Used by the rules system to advise a rule that the data 
     * it has been waiting for is now available.
     */
    public LoadHistoricDataPointBatchResult(Putup newPutup, TreeSet<AbstractGraphPoint> newPoints, ICallback newCallback, List<ICallback> extraCallbacks) {
        this(newPutup, newPoints, newCallback);
        if(null != extraCallbacks){
            this.cbList = extraCallbacks;
        } else {
            this.cbList = new ArrayList<ICallback>();
        }
        this.callbackType = CallbackType.HISTORICDATAFINISHED;
    }

    /**
     * Constructor creates a result object that defines a result object containing
     * the provided putup and set of Price / Time point data that was loaded. The user also
     * provides a callback to which this object should be passed as a data element
     * @param newPutup - A Putup representing a stock market security to which this data relates
     * @param newPoints - The new set of historic Price / Time point data received 
     * from the stock brokers server.
     * @param newCallback - The callback that should be made by the Historic Data 
     * Request System passing this object as its result.
     * @param extraCallbacks A list of additional callbacks that should be made over and above 
     * the primary callback. Used by the rules system to advise a rule that the data 
     * it has been waiting for is now available.
     * @param newType - The type flag to be used when making the callback as defined in the
     * CallbackType enumeration class
     */
    public LoadHistoricDataPointBatchResult(Putup newPutup, TreeSet<AbstractGraphPoint> newPoints, ICallback newCallback, List<ICallback> extraCallbacks, CallbackType newType) {
        this(newPutup, newPoints, newCallback, extraCallbacks);
        this.callbackType = newType;
    }

    /**
     * Use this constructor if the task fails to pass the exception back to the
     * callback code
     * @param ex - The exception to be returned to the callback code
     * @param newCBDelegate - The Callback to receive this exception message
     */
    public LoadHistoricDataPointBatchResult(Exception ex, ICallback newCBDelegate) {
        this.execException = ex;
        this.cbDelegate = newCBDelegate;
        this.callbackType = CallbackType.HISTORICDATAERROR;
    }

    /**
     * Use this constructor if the task fails to pass the exception back to the
     * callback code
     * @param ex - The exception to be returned to the callback code
     * @param newCBDelegate - The Callback to receive this exception message
     * @param type - The type flag to be used when making the callback as defined in the
     * CallbackType enumeration class
     */
    public LoadHistoricDataPointBatchResult(Exception ex, ICallback newCBDelegate, CallbackType type) {
        this(ex, newCBDelegate);
        this.callbackType = type;
    }
    
    /**
     * Use this constructor if the task fails to pass the exception back to the
     * callback code
     * @param ex - The exception to be returned to the callback code
     * @param newCBDelegate - The Callback to receive this exception message
     * @param extraCallbacks - A list of additional callbacks that should be made over and above 
     * the primary callback. Used by the rules system to advise a rule that the data 
     * it has been waiting for is now available.
     */
    public LoadHistoricDataPointBatchResult(Exception ex, ICallback newCBDelegate, List<ICallback> extraCallbacks) {
        this.execException = ex;
        this.cbDelegate = newCBDelegate;
        if(null != extraCallbacks){
            this.cbList = extraCallbacks;
        } else {
            this.cbList = new ArrayList<ICallback>();
        }
        this.callbackType = CallbackType.HISTORICDATAERROR;
    }

    /**
     * Use this constructor if the task fails to pass the exception back to the
     * callback code
     * @param ex - The exception to be returned to the callback code
     * @param newCBDelegate - The Callback to receive this exception message
     * @param extraCallbacks - A list of additional callbacks that should be made over and above 
     * the primary callback. Used by the rules system to advise a rule that the data 
     * it has been waiting for is now available.
     * @param type - The type flag to be used when making the callback as defined in the
     * CallbackType enumeration class
     */
    public LoadHistoricDataPointBatchResult(Exception ex, ICallback newCBDelegate, List<ICallback> extraCallbacks, CallbackType type) {
        this(ex, newCBDelegate, extraCallbacks);
        this.callbackType = type;
    }

    /**
     * Accessor method to retrieve the primary callback to be made with this data result
     * @return An ICallback interface to the object that should receive the loaded data
     *  and process this result
     */
    public ICallback getCbDelegate() {
        return cbDelegate;
    }

    /**
     * Accessor method to set the primary callback to be made with this data result
     * @param cbDelegate - An ICallback interface to the object that should receive the loaded data
     *  and process this result
     */
    public void setCbDelegate(ICallback cbDelegate) {
        this.cbDelegate = cbDelegate;
    }

    /**
     * A List of additional callback that should be made over and above th primary one.
     * This is used to notify any rules that are waiting for data from the stock broker
     * that it is now available.
     * @return the cbList - A list of ICallback interfaces that should process this result
     */
    public List<ICallback> getCbList() {
        List<ICallback> result = new ArrayList<ICallback>();
        if(null != this.cbDelegate){
            result.add(cbDelegate);
        }
        result.addAll(this.cbList);
        return result;
    }

    /**
     * Accessor method to set a List of additional callback that should be made over and above th primary one.
     * @param cbList The ICallback list to store for this result
     */
    public void setCbList(List<ICallback> cbList) {
        this.cbList = cbList;
    }

    /**
     * Accessor method to retrieve any processing exception that prevented a task 
     * from being executed. 
     * @return the Java Exception object that was thrown or NULL if no exception occurred
     */
    public Exception getExecException() {
        return execException;
    }

    /**
     * Accessor method to retrieve the callback type flag that should be associated 
     * with this result when the callback is made.
     * @return A CallbackType enumeration value describing the meaning of the data
     * in this result. used to define how it should be processed by the callback's code.
     */
    public CallbackType getCallbackType() {
        return callbackType;
    }

    /**
     * Normally the loaded data is stored as a TreeSet of time ordered 
     * Price / Time points however on occasion a BaseGraph is useful instead.
     * This method constructs a BaseGraph object from the loaded data points
     * @return A BaseGraph object containing the data represented by this
     * Historic Data result or NULL if no such graph can be constructed.
     */
    public BaseGraph<AbstractGraphPoint> getPointsAsGraph() {
        BaseGraph<AbstractGraphPoint> result = null;
        if (null != this.putup && null != this.loadedPoints) {
            result = new BaseGraph<AbstractGraphPoint>(this.loadedPoints);
            result.setPutup(this.putup);
        }
        return result;
    }
}
