/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

/**
 * This enumeration describes the various 'states' that can be used to select between
 * which rules the RulesStateManager should be running
 * @author Roy
 */
public enum RulesStateEnum {
    
    /**
     * Putups in this state are receiving real time 5 sec bar data from the 
     * stock broker but have not yet passed the FTG and 3M Range rules
     */
    FIVESECBARONLY,                                 //State 1 & 2
    /**
     * Putups in this state are receiving real time 5 sec bar data from the 
     * stock broker as well as 1 sec real time market data. They have passed 
     * the FTG rules check and the 3M Range check simultaneously at some
     * point in the day. They will be scanning the real time data graphs looking
     * for a SingleDouble pattern that defines how the stock price must behave 
     * before entry can be considered
     */
    FIVESECBARPLUSMARKETDATA,                       //State 3 looking for single double
    /**
     *Putups in this state are receiving real time 5 sec bar data from the 
     * stock broker as well as 1 sec real time market data. In addition they
     * have identified a 'clue' SingleDouble pattern which indicates that the 
     * stock is behaving in a manner likely to lead to entry. They will have loaded
     * historic data for the day up to the time the pattern was identified
     */
    TESTINGSINGLEDOUBLE;                            //State 4 Testing a found Single/Double Combination

    @Override
    public String toString() {
        String result = "";
        switch(this){
            case FIVESECBARONLY:
                result = "FIVESECBARONLY";
                break;
            case FIVESECBARPLUSMARKETDATA:
                result = "FIVESECBARPLUSMARKETDATA";
                break;
            case TESTINGSINGLEDOUBLE:
                result = "TESTINGSINGLEDOUBLE";
                break;
        }
        return result;
    }
    
    /**
     * This method parses a string to identify a rules state. 
     * @param data - A String that identifies a rules state enumeration value.
     * @throws IllegalArgumentException if a RulesStateEnum cannot be identified
     * from the provided string argument
     * @return A RulesStateEnum parsed from the provided string argument
     */
    public static RulesStateEnum parseFromString(String data){
        RulesStateEnum result = null;
        if(null != data){
            if(data.equalsIgnoreCase("FIVESECBARONLY")){
                result = FIVESECBARONLY;
            }
            if(data.equalsIgnoreCase("FIVESECBARPLUSMARKETDATA")){
                result = FIVESECBARPLUSMARKETDATA;
            }
            if(data.equalsIgnoreCase("TESTINGSINGLEDOUBLE")){
                result = TESTINGSINGLEDOUBLE;
            }
        }
        if(null == result){
            throw new IllegalArgumentException("Cannot identify a RulesStateEnum from the string: " + data);
        }
        return result;
    }
    
}
