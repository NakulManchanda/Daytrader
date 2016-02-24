/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

/**
 * This class contains and manages access to an array of HistoricDataResponse objects.
 * This allows the results of multiple requests to be grouped together into a single entity
 * @author Roy
 */
public class HistoricDataGraph extends BaseGraph<AbstractGraphPoint> {
    
    /**
     * Default Constructor calls default constructor for BaseGraph. This class 
     * effectively aliases the BaseGraph class to make code more readable and
     * describe the type of data being stored.
     */
    public HistoricDataGraph() {
        super();
    }
}
