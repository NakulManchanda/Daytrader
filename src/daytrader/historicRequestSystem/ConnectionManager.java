/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.historicRequestSystem;

import com.ib.client.CommissionReport;
import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EWrapper;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.UnderComp;
import daytrader.datamodel.AbstractDataRequest;
import java.util.HashMap;
import java.util.Set;

/**
 * This class makes a connection to the stock brokers server and allows multiple
 * requests to be made over it. Responses are delivered to the appropriate
 * handler based on the identifying request ID. Each request provides a unique
 * ID number as an integer and these are mapped using a HashMap to the corresponding
 * request. When a response from the stock brokers server arrives the ID number 
 * identifies the request that asked for the data from the HashMap. Each request
 * implements the stock brokers EWrapper interface to handle responses. This 
 * class implements that interface and delegates its methods to the data request
 * with the matching ID number.
 *
 * @author Roy
 */
public class ConnectionManager implements EWrapper {

    private HashMap<Integer, EWrapper> requestTable;

    /**
     * Default Constructor initialises an empty hash map
     */
    public ConnectionManager() {
        this.requestTable = new HashMap<Integer, EWrapper>();
    }

    /**
     * Given an ID number this method retrieve the appropriate handler from the
     * HashMap
     * @param id - ID number for the data request that should be used to handle 
     * the arriving data item
     * @return An object that implements the EWrapper interface to handle arriving
     * data from the stock brokers API
     */
    private EWrapper getHandler(Integer id) {
        EWrapper result = null;
        result = this.requestTable.get(id);
        return result;
    }
    
    /**
     * Method to add a new HISTORIC data request to the managed requests
     * @param newReq - AbstractHDTCallable being a request that is waiting for
     * a response from the stock brokers server
     */
    public void addAbstractHDTCallable(AbstractHDTCallable newReq){
        this.requestTable.put(newReq.getReqId(), newReq);
    }
    
    /**
     * Method to add a new REAL TIME data request to the managed requests
     * @param newReq - AbstractDataRequest being a request that is waiting for
     * a response from the stock brokers server
     */
    public void addAbstractDataRequest(AbstractDataRequest newReq){
        this.requestTable.put(newReq.getReqId(), newReq);
    }
    
    /**
     * Accessor to remove a completed REAL TIME data request
     * @param oldReq - AbstractDataRequest being a completed data request
     */
    public void removeAbstractDataRequest(AbstractDataRequest oldReq){
        this.requestTable.remove(oldReq.getReqId());
    }
    
    /**
     * Accessor Method to remove any type of task from the list of tasks awaiting data
     * based on the unique ID number assigned to that task
     * @param taskId - integer being a tasks unique ID number
     */
    public void removeCompletedTask(int taskId){
        this.requestTable.remove(taskId);
    }
    
    /**
     * In the event that the provided ID number does not match to any EWrapper data 
     * handler in the HashMap an IllegalArgumentException must be thrown. 
     * @param id - Integer being the unmatched ID number
     */
    private void throwIllegalArgumentException(Integer id){
        if(-1 != id){
            throw new IllegalArgumentException("The ID: " + id + " is not registered with the connection manager");
        }
    }

    //Stockbroker EWrapper interface - START
    /**
     * For documentation see page 265 - 266 of stockbroker API documentation
     * @param tickerId
     * @param field
     * @param price
     * @param canAutoExecute
     */
    @Override
    public void tickPrice(int tickerId, int field, double price, int canAutoExecute) {
        EWrapper handler = this.getHandler(tickerId);
        if (null != handler) {
            handler.tickPrice(tickerId, field, price, canAutoExecute);
        } else {
            this.throwIllegalArgumentException(tickerId);
        }
    }

    /**
     * For documentation see page 266 of stockbroker API documentation
     * @param tickerId
     * @param field
     * @param size
     */
    @Override
    public void tickSize(int tickerId, int field, int size) {
        EWrapper handler = this.getHandler(tickerId);
        if (null != handler) {
            handler.tickSize(tickerId, field, size);
        } else {
            this.throwIllegalArgumentException(tickerId);
        }
    }

    /**
     * For documentation see page 267 of stockbroker API documentation
     * @param tickerId
     * @param field
     * @param impliedVol
     * @param delta
     * @param optPrice
     * @param pvDividend
     * @param gamma
     * @param vega
     * @param theta
     * @param undPrice
     */
    @Override
    public void tickOptionComputation(int tickerId, int field, double impliedVol, double delta, double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice) {
        EWrapper handler = this.getHandler(tickerId);
        if (null != handler) {
            handler.tickOptionComputation(tickerId, field, impliedVol, delta, optPrice, pvDividend, gamma, vega, theta, undPrice);
        } else {
            this.throwIllegalArgumentException(tickerId);
        }
    }

    /**
     * For documentation see page 267 of stockbroker API documentation
     * @param tickerId
     * @param tickType
     * @param value
     */
    @Override
    public void tickGeneric(int tickerId, int tickType, double value) {
        EWrapper handler = this.getHandler(tickerId);
        if (null != handler) {
            handler.tickGeneric(tickerId, tickType, value);
        } else {
            this.throwIllegalArgumentException(tickerId);
        }
    }

    /**
     * For documentation see page 268 of stockbroker API documentation
     * @param tickerId
     * @param tickType
     * @param value
     */
    @Override
    public void tickString(int tickerId, int tickType, String value) {
        EWrapper handler = this.getHandler(tickerId);
        if (null != handler) {
            handler.tickString(tickerId, tickType, value);
        } else {
            this.throwIllegalArgumentException(tickerId);
        }
    }

    /**
     * For documentation see page 268 of stockbroker API documentation
     * @param tickerId
     * @param tickType
     * @param basisPoints
     * @param formattedBasisPoints
     * @param impliedFuture
     * @param holdDays
     * @param futureExpiry
     * @param dividendImpact
     * @param dividendsToExpiry
     */
    @Override
    public void tickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints, double impliedFuture, int holdDays, String futureExpiry, double dividendImpact, double dividendsToExpiry) {
        EWrapper handler = this.getHandler(tickerId);
        if (null != handler) {
            handler.tickEFP(tickerId, tickType, basisPoints, formattedBasisPoints, impliedFuture, holdDays, futureExpiry, dividendImpact, dividendsToExpiry);
        } else {
            this.throwIllegalArgumentException(tickerId);
        }
    }

    /**
     * For documentation see page 269 - 270 of stockbroker API documentation
     * @param orderId
     * @param status
     * @param filled
     * @param remaining
     * @param avgFillPrice
     * @param permId
     * @param parentId
     * @param lastFillPrice
     * @param clientId
     * @param whyHeld
     */
    @Override
    public void orderStatus(int orderId, String status, int filled, int remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
        EWrapper handler = this.getHandler(orderId);
        if (null != handler) {
            handler.orderStatus(orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld);
        } else {
            this.throwIllegalArgumentException(orderId);
        }
    }

    /**
     * For documentation see page 271 of stockbroker API documentation
     * @param orderId
     * @param contract
     * @param order
     * @param orderState
     */
    @Override
    public void openOrder(int orderId, Contract contract, Order order, OrderState orderState) {
        EWrapper handler = this.getHandler(orderId);
        if (null != handler) {
            handler.openOrder(orderId, contract, order, orderState);
        } else {
            this.throwIllegalArgumentException(orderId);
        }
    }

    /**
     * NO DOCUMENTATION AVAILABLE IN STOCKBROKERS PDF
     */
    @Override
    public void openOrderEnd() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 271 - 272 of stockbroker API documentation
     * @param key
     * @param value
     * @param currency
     * @param accountName
     */
    @Override
    public void updateAccountValue(String key, String value, String currency, String accountName) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 272 of stockbroker API documentation
     * @param contract
     * @param position
     * @param marketPrice
     * @param marketValue
     * @param averageCost
     * @param unrealizedPNL
     * @param realizedPNL
     * @param accountName
     */
    @Override
    public void updatePortfolio(Contract contract, int position, double marketPrice, double marketValue, double averageCost, double unrealizedPNL, double realizedPNL, String accountName) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 273 of stockbroker API documentation
     * @param timeStamp
     */
    @Override
    public void updateAccountTime(String timeStamp) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * NO DOCUMENTATION AVAILABLE IN STOCKBROKERS PDF
     * @param accountName
     */
    @Override
    public void accountDownloadEnd(String accountName) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 271 of stockbroker API documentation
     * @param orderId
     */
    @Override
    public void nextValidId(int orderId) {
        //This is irrelevent for requesting data, ignored
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 273 of stockbroker API documentation
     * @param reqId
     * @param contractDetails
     */
    @Override
    public void contractDetails(int reqId, ContractDetails contractDetails) {
        EWrapper handler = this.getHandler(reqId);
        if (null != handler) {
            handler.contractDetails(reqId, contractDetails);
        } else {
            this.throwIllegalArgumentException(reqId);
        }
    }

    /**
     * For documentation see page 273 of stockbroker API documentation
     * @param reqId
     * @param contractDetails
     */
    @Override
    public void bondContractDetails(int reqId, ContractDetails contractDetails) {
        EWrapper handler = this.getHandler(reqId);
        if (null != handler) {
            handler.bondContractDetails(reqId, contractDetails);
        } else {
            this.throwIllegalArgumentException(reqId);
        }
    }

    /**
     * For documentation see page 273 of stockbroker API documentation
     * @param reqId
     */
    @Override
    public void contractDetailsEnd(int reqId) {
        EWrapper handler = this.getHandler(reqId);
        if (null != handler) {
            handler.contractDetailsEnd(reqId);
        } else {
            this.throwIllegalArgumentException(reqId);
        }
    }

    /**
     * For documentation see page 273 - 274 of stockbroker API documentation
     * @param reqId
     * @param contract
     * @param execution
     */
    @Override
    public void execDetails(int reqId, Contract contract, Execution execution) {
        EWrapper handler = this.getHandler(reqId);
        if (null != handler) {
            handler.execDetails(reqId, contract, execution);
        } else {
            this.throwIllegalArgumentException(reqId);
        }
    }

    /**
     * For documentation see page 274 of stockbroker API documentation
     * @param reqId
     */
    @Override
    public void execDetailsEnd(int reqId) {
        EWrapper handler = this.getHandler(reqId);
        if (null != handler) {
            handler.execDetailsEnd(reqId);
        } else {
            this.throwIllegalArgumentException(reqId);
        }
    }

    /**
     * For documentation see page 274 - 275 of stockbroker API documentation
     * @param tickerId
     * @param position
     * @param operation
     * @param side
     * @param price
     * @param size
     */
    @Override
    public void updateMktDepth(int tickerId, int position, int operation, int side, double price, int size) {
        EWrapper handler = this.getHandler(tickerId);
        if (null != handler) {
            handler.updateMktDepth(tickerId, position, operation, side, price, size);
        } else {
            this.throwIllegalArgumentException(tickerId);
        }
    }

    /**
     * For documentation see page 275 of stockbroker API documentation
     * @param tickerId
     * @param position
     * @param marketMaker
     * @param operation
     * @param side
     * @param price
     * @param size
     */
    @Override
    public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation, int side, double price, int size) {
        EWrapper handler = this.getHandler(tickerId);
        if (null != handler) {
            handler.updateMktDepthL2(tickerId, position, marketMaker, operation, side, price, size);
        } else {
            this.throwIllegalArgumentException(tickerId);
        }
    }

    /**
     * For documentation see page 275 - 276 of stockbroker API documentation
     * @param msgId
     * @param msgType
     * @param message
     * @param origExchange
     */
    @Override
    public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange) {
        EWrapper handler = this.getHandler(msgId);
        if (null != handler) {
            handler.updateNewsBulletin(msgId, msgType, message, origExchange);
        } else {
            this.throwIllegalArgumentException(msgId);
        }
    }

    /**
     * For documentation see page 276 of stockbroker API documentation
     * @param accountsList
     */
    @Override
    public void managedAccounts(String accountsList) {
        //Not used to request data ignore
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 276 of stockbroker API documentation
     * @param faDataType
     * @param xml
     */
    @Override
    public void receiveFA(int faDataType, String xml) {
        //Not used to request data ignore
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 280 of stockbroker API documentation
     * @param reqId
     * @param date
     * @param open
     * @param high
     * @param low
     * @param close
     * @param volume
     * @param count
     * @param WAP
     * @param hasGaps
     */
    @Override
    public void historicalData(int reqId, String date, double open, double high, double low, double close, int volume, int count, double WAP, boolean hasGaps) {
        EWrapper handler = this.getHandler(reqId);
        if (null != handler) {
            handler.historicalData(reqId, date, open, high, low, close, volume, count, WAP, hasGaps);
        } else {
            this.throwIllegalArgumentException(reqId);
        }
    }

    /**
     * For documentation see page 280 of stockbroker API documentation
     * @param xml
     */
    @Override
    public void scannerParameters(String xml) {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 280 - 281 of stockbroker API documentation
     * @param reqId
     * @param rank
     * @param contractDetails
     * @param distance
     * @param benchmark
     * @param projection
     * @param legsStr
     */
    @Override
    public void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance, String benchmark, String projection, String legsStr) {
        EWrapper handler = this.getHandler(reqId);
        if (null != handler) {
            handler.scannerData(reqId, rank, contractDetails, distance, benchmark, projection, legsStr);
        } else {
            this.throwIllegalArgumentException(reqId);
        }
    }

    /**
     * For documentation see page 281 of stockbroker API documentation
     * @param reqId
     */
    @Override
    public void scannerDataEnd(int reqId) {
        EWrapper handler = this.getHandler(reqId);
        if (null != handler) {
            handler.scannerDataEnd(reqId);
        } else {
            this.throwIllegalArgumentException(reqId);
        }
    }

    /**
     * For documentation see page 281 - 282 of stockbroker API documentation
     * @param reqId
     * @param time
     * @param open
     * @param high
     * @param low
     * @param close
     * @param volume
     * @param wap
     * @param count
     */
    @Override
    public void realtimeBar(int reqId, long time, double open, double high, double low, double close, long volume, double wap, int count) {
        EWrapper handler = this.getHandler(reqId);
        if (null != handler) {
            handler.realtimeBar(reqId, time, open, high, low, close, volume, wap, count);
        } else {
            this.throwIllegalArgumentException(reqId);
        }
    }

    /**
     * For documentation see page 264 - 265 of stockbroker API documentation
     * @param time
     */
    @Override
    public void currentTime(long time) {
        Set<Integer> keySet = this.requestTable.keySet();
        for(Integer key : keySet){
            EWrapper target = this.requestTable.get(key);
            target.currentTime(time);
        }
    }

    /**
     * For documentation see page 282 of stockbroker API documentation
     * @param reqId
     * @param data
     */
    @Override
    public void fundamentalData(int reqId, String data) {
        EWrapper handler = this.getHandler(reqId);
        if (null != handler) {
            handler.fundamentalData(reqId, data);
        } else {
            this.throwIllegalArgumentException(reqId);
        }
    }

    /**
     * See p 437 of stock broker's documentation
     * @param reqId
     * @param underComp
     */
    @Override
    public void deltaNeutralValidation(int reqId, UnderComp underComp) {
        EWrapper handler = this.getHandler(reqId);
        if (null != handler) {
            handler.deltaNeutralValidation(reqId, underComp);
        } else {
            this.throwIllegalArgumentException(reqId);
        }
    }

    /**
     * For documentation see page 269 of stockbroker API documentation
     * @param reqId
     */
    @Override
    public void tickSnapshotEnd(int reqId) {
        EWrapper handler = this.getHandler(reqId);
        if (null != handler) {
            handler.tickSnapshotEnd(reqId);
        } else {
            this.throwIllegalArgumentException(reqId);
        }
    }

    /**
     * For documentation see page 269 of stockbroker API documentation
     * @param reqId
     * @param marketDataType
     */
    @Override
    public void marketDataType(int reqId, int marketDataType) {
        EWrapper handler = this.getHandler(reqId);
        if (null != handler) {
            handler.marketDataType(reqId, marketDataType);
        } else {
            this.throwIllegalArgumentException(reqId);
        }
    }

    /**
     * For documentation see page 274 of stockbroker API documentation
     * @param commissionReport
     */
    @Override
    public void commissionReport(CommissionReport commissionReport) {
        Set<Integer> keySet = this.requestTable.keySet();
        for(Integer key : keySet){
            EWrapper target = this.requestTable.get(key);
            target.commissionReport(commissionReport);
        }
    }

    /**
     * For documentation see page 265 of stockbroker API documentation
     * @param e
     */
    @Override
    public void error(Exception e) {
        Set<Integer> keySet = this.requestTable.keySet();
        for(Integer key : keySet){
            EWrapper target = this.requestTable.get(key);
            target.error(e);
        }
    }

    /**
     * For documentation see page 265 of stockbroker API documentation
     * @param str
     */
    @Override
    public void error(String str) {
        Set<Integer> keySet = this.requestTable.keySet();
        for(Integer key : keySet){
            EWrapper target = this.requestTable.get(key);
            target.error(str);
        }
    }

    /**
     * For documentation see page 265 of stockbroker API documentation and error codes
     * on pages 374 to 386
     * @param id
     * @param errorCode
     * @param errorMsg
     */
    @Override
    public void error(int id, int errorCode, String errorMsg) {
        EWrapper handler = this.getHandler(id);
        if (null != handler) {
            handler.error(id, errorCode, errorMsg);
        } else {
            this.throwIllegalArgumentException(id);
        }
    }

    /**
     * For documentation see page 265 of stockbroker API documentation
     */
    @Override
    public void connectionClosed() {
        Set<Integer> keySet = this.requestTable.keySet();
        for(Integer key : keySet){
            EWrapper target = this.requestTable.get(key);
            target.connectionClosed();
        }
    }
    //Stockbroker EWrapper interface - END

    @Override
    public void position(String account, Contract contract, int pos, double avgCost) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void positionEnd() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void accountSummary(int reqId, String account, String tag, String value, String currency) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void accountSummaryEnd(int reqId) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
