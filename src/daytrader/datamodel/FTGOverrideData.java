/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import java.util.EnumMap;

/**
 * This class holds the FTG values for each class used in the FTG Override check. 
 * Currently these values are test data held in a EnumMap 
 * however this class should be updated to support serialisation
 * to an XML file in the same manner as the AtrClassValue.
 * @author Roy
 */
public class FTGOverrideData {
    
    private EnumMap<AtrClassEnum, Double> data;
    
    /**
     * Default constructor that populates the enumeration map with test data.
     * This needs to be changed to load data from an XML File, Indeed the whole
     * class should implement the XMLPersistable interface and this constructor
     * should load data persisted in an XML File.
     */
    public FTGOverrideData(){
        data = new EnumMap<AtrClassEnum, Double>(AtrClassEnum.class);
        Double startVal = 0.005;
        for(AtrClassEnum currClass : AtrClassEnum.values()){
            data.put(currClass, startVal);
            startVal += 0.001;
        }
    }
    
    /**
     * Accessor to retrieve the FTG Fraction to be used in performing the FTG Override Test 
     * @param reqClass - A put up's ATRClassEnum for which the matching FTG fraction is required
     * @return A Double being the FTG fraction to use for this class.
     */
    public Double getOverrideFTGFraction(AtrClassEnum reqClass){
        Double result = 0.0d;
        if(null != reqClass){
            result = data.get(reqClass);
        }
        return result;
    }
}