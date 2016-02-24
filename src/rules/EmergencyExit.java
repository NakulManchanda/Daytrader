/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.EntryDetails;

/**
 * Given a graph and entry details this class decides whether an emergency exit is required.
 * VERY INCOMPLETE ONLY THE MOST GENERAL SKELETON. Use or discard as you see fit when
 * you come to code security exit rules (ie when a security should be sold).
 * @author Roy
 */
public class EmergencyExit {
    
    public boolean shouldEmerExit(EntryDetails entry){
        boolean result = false;
        if(null != entry){
            BaseGraph<AbstractGraphPoint> graph = entry.getRunRecord().getLoader().getGraph();
            AbstractGraphPoint last = graph.last();
            double lastPrice = last.getLastPrice();
            //Determine the emergency exit point
        }
        return result;
    }
    
}
