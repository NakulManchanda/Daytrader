/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

/**
 * This class encapsulates the details of an entry into the stock market.
 * Not at all complete (or used yet, feel free to replace or extend it).
 * @author Roy
 */
public class EntryDetails {
    
    private RealTimeRunRecord runRecord;
    private Integer I;
    
    /**
     * Constructor accepts the RealTimeRunManager for a Put up
     * @param newRec - The RealTimeRunManager of the put up for which the entry is to be made
     * @param newI - Integer being the 'I' value determined for this entry
     */
    public EntryDetails(RealTimeRunRecord newRec, Integer newI){
        this.runRecord = newRec;
        this.I = newI;
    }

    /**
     * Accessor method to retrieve the real time run manager involved in this entry
     * @return A RealTimeRunRecord object for the security that the entry relates to.
     */
    public RealTimeRunRecord getRunRecord() {
        return runRecord;
    }

    /**
     * Accessor method to retrieve the 'I' value determined by the entry rules
     * @return An Integer being this entries 'I' rule.
     */
    public Integer getI() {
        return I;
    }
    
}
