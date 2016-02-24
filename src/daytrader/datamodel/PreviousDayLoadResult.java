/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

/**
 * DEPRCATED CLASS DO NOT USE
 * 
 * This class encapsulates a previous days loading result. It provides the day code and
 * associated base graph for caching into the main record. The graph SHOULD CONTAIN POINTS
 * FROM ONLY ONE DAY.
 * @author Roy
 */
public class PreviousDayLoadResult<T extends AbstractGraphPoint>{
    
    private Integer dayCode;
    private BaseGraph<T> graph;
    
    public PreviousDayLoadResult(BaseGraph<T> newGraph){
        this.graph = newGraph;
        this.dayCode = this.graph.last().getDateAsNumber();
    }

    /**
     * @return the dayCode
     */
    public Integer getDayCode() {
        return dayCode;
    }

    /**
     * @return the graph
     */
    public BaseGraph<T> getGraph() {
        return graph;
    }
    
}
