/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.datamodel.DTConstants;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * This is the data table to use for checking IG scores. Uses hard coded test data 
 * values but this should be converted to load and persist data to / from an
 * XML File.
 * @author Roy
 */
public class IGLimitTable {
    
    private TreeSet<IGLimitEntry> data;
    private TreeSet<IGLimitEntry> data933;
    
    /**
     * Default Constructor that loads the hard coded test data.
     */
    public IGLimitTable(){
        this.data = new TreeSet<IGLimitEntry>();
        double dblPrice = 15;
        for(double i = 0.04; i < 0.22; i += 0.01){
            double currScore = i * DTConstants.SCALE;
            double currPrice = dblPrice * DTConstants.SCALE;
            IGLimitEntry item = new IGLimitEntry(currScore, currPrice);
            this.data.add(item);
            dblPrice += 5;
        }
        
        //We need to use a second version of this table for 09:33 - 09:36 time period
        //This code builds that table
        this.data933 = new TreeSet<IGLimitEntry>();
        dblPrice = 5;
        for(double i = 0.04; i < 0.24; i += 0.01){
            double currScore = i * DTConstants.SCALE;
            double currPrice = dblPrice * DTConstants.SCALE;
            IGLimitEntry item = new IGLimitEntry(currScore, currPrice);
            this.data933.add(item);
            dblPrice += 5;
        }
    }
    
    /**
     * Accessor method to retrieve the IGLimitEntry that should be used for the provided
     * price
     * @param price - the price used in identifying the correct IGLimitEntry
     * @return An IGLimitEntry object to use for this price point or NULL is none exist
     */
    public IGLimitEntry getLimitFromPrice(double price){
        IGLimitEntry result = null;
        if(null != this.data && 0 < this.data.size()){
            IGLimitEntry first = this.data.first();
            IGLimitEntry tempVal = new IGLimitEntry(0, price);
            NavigableSet<IGLimitEntry> subSet = this.data.subSet(first, true, tempVal, true);
            result = subSet.last();
        }
        return result;
    }
    
    /**
     * Accessor method to retrieve the IGLimitEntry that should be used for the provided
     * price when that price occurs in the first 3 minutes of the day
     * @param price - the price used in identifying the correct IGLimitEntry
     * @return An IGLimitEntry object to use for this price point or NULL is none exist
     */
    public IGLimitEntry getAlternateLimitFromPrice(double price){
        IGLimitEntry result = null;
        if(null != this.data933 && 0 < this.data933.size()){
            IGLimitEntry first = this.data933.first();
            IGLimitEntry tempVal = new IGLimitEntry(0, price);
            NavigableSet<IGLimitEntry> subSet = this.data933.subSet(first, true, tempVal, true);
            result = subSet.last();
        }
        return result;
    }
    
}
