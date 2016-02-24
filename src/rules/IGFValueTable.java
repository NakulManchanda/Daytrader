/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.datamodel.AtrClassEnum;
import java.util.EnumMap;

/**
 * This class holds a hash map that defines the values to be used in the IG function
 * based on class
 * 
 * Currently uses hard coded test values but a display should be constructed that allows
 * the user to modify the table, view the entries and persist the data to an XML File 
 * @author Roy
 */
public class IGFValueTable {
    
    private EnumMap<AtrClassEnum, Double> data;
    
    /**
     * Default constructor uses hard coded test data but this should be replaced
     * to load the data persisted in an XML file.
     */
    public IGFValueTable(){
        data = new EnumMap<AtrClassEnum, Double>(AtrClassEnum.class);
        Double startVal = 0.005;
        for(AtrClassEnum currClass : AtrClassEnum.values()){
            data.put(currClass, startVal);
            startVal += 0.001;
        }
    }
    
    /**
     * Accessor method to retrieve the IG percentage to be used in calculations
     * of the IG rule
     * @param reqClass - The AtrClassEnum for which the IG Percentage is required
     * @return A Double being the percentage to use in the IG rule.
     */
    public Double getIGFraction(AtrClassEnum reqClass){
        Double result = 0.0d;
        if(null != reqClass){
            result = data.get(reqClass);
        }
        return result;
    }
}
