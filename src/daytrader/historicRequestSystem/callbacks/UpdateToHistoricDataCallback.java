/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.historicRequestSystem.callbacks;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.CallbackType;
import daytrader.interfaces.ICallback;
import daytradertasks.LoadHistoricDataPointBatchResult;
import javax.swing.JOptionPane;

/**
 * Give a base graph this callback takes a set of loaded historic data points
 * and adds them to the graph. Any data points between the start and end times
 * of the point list will be removed.
 *
 * @author Roy
 */
public class UpdateToHistoricDataCallback implements ICallback {

    private BaseGraph<AbstractGraphPoint> graph;
    private boolean alert;

    /**
     * Constructor accepts the BaseGraph that should be updated when the callback is made
     * @param targetGraph - The graph of Price / Time data to update with the data 
     * received from the call back
     */
    public UpdateToHistoricDataCallback(BaseGraph<AbstractGraphPoint> targetGraph) {
        this.graph = targetGraph;
        this.alert = false;
    }

    /**
     * Constructor accepts the BaseGraph that should be updated when the callback is made and
     * a flag indicating if a visual alert should be shown
     * @param targetGraph - The graph of Price / Time data to update with the data 
     * received from the call back
     * @param newAlertFlag - boolean True if an alert box advising the update has happened should be shown
     * useful when debugging.
     */
    public UpdateToHistoricDataCallback(BaseGraph<AbstractGraphPoint> targetGraph, boolean newAlertFlag) {
        this.graph = targetGraph;
        this.alert = newAlertFlag;
    }

    @Override
    public void callback(CallbackType type, Object data) {
        if (null != this.graph && null != data && data instanceof LoadHistoricDataPointBatchResult) {
            LoadHistoricDataPointBatchResult aResult = (LoadHistoricDataPointBatchResult) data;
            if (null != aResult.loadedPoints && 0 < aResult.loadedPoints.size()) {
                this.graph.storeHistoricData(aResult.loadedPoints);
                if (alert) {
                    java.awt.EventQueue.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            JOptionPane.showMessageDialog(null, "Update Complete: " + graph.getStockTicker(), "Callback completed...", JOptionPane.INFORMATION_MESSAGE);
                        }
                    });
                }
            }
        }
    }
}
