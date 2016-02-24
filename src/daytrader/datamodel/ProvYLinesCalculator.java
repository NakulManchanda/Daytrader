/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytrader.interfaces.IGraphLine;
import java.util.ArrayList;
import java.util.concurrent.Callable;

/**
 * This Callable task takes a put up in the YLineLoadStatus.YLINECALCULATING state and
 * calculates the provisional Y-Lines moving it into the YLineLoadStatus.YLINESLOADED state
 * @author Roy
 */
public class ProvYLinesCalculator implements Callable<Void> {
    
    private Putup target;
    
    /**
     * Constructor accepts a Putup that is in the YLineLoadStatus.YLINECALCULATING
     * state, performs the Y-Line calculations and stores the generated Y-Lines.
     * Finally moves the putup into the YLineLoadStatus.YLINESLOADED state.
     * 
     * NB: that to reach the YLineLoadStatus.YLINECALCULATING the needed Y-Lines 
     * data must have already been loaded.
     * @param newTarget
     */
    public ProvYLinesCalculator(Putup newTarget){
        this.target = newTarget;
    }

    @Override
    public Void call() throws Exception {
        if(this.target.getYLineStatus() == YLineLoadStatus.YLINECALCULATING){
            BaseGraph<AbstractGraphPoint> preLoadedYLineGraph = this.target.getPreLoadedYLineGraph();
            ArrayList<IGraphLine> provisionalYLines = this.target.generateProvisionalYLines(preLoadedYLineGraph);
            ArrayList<IGraphLine> checkYLinesForStandIns = this.target.checkYLinesForStandIns(provisionalYLines, preLoadedYLineGraph);
            this.target.setInitialYLines(checkYLinesForStandIns);
            this.target.setYLineStatus(YLineLoadStatus.YLINESLOADED);
        }
        return null;
    }
    
}
