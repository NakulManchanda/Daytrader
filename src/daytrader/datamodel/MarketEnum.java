/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

/**
 * This represents the market on which the stock is traded
 * @author Roy
 */
public enum MarketEnum {
    
    /**
     * Enumeration value representing the New York stock exchange
     */
    NYSE,
    /**
     * Enumeration value representing the National Association of 
     * Securities Dealers Automated Quotations stock exchange
     */
    NASDAQ;

    @Override
    public String toString() {
        String result = "";
        switch(this){
            case NYSE:
                result = "NYSE";
                break;
            case NASDAQ:
                result = "NASDAQ";
                break;
        }
        return result;
    }
    
    /**
     * Retrieves the full stock exchange name as a string
     * @return String being the full name of the stock market.
     */
    public String toLongName(){
        String result = "";
        switch(this){
            case NYSE:
                result = "New York Stock Exchange (NYSE)";
                break;
            case NASDAQ:
                result = "National Association of Securities Dealers Automated Quotations (NASDAQ)";
                break;
        }
        return result;
    }
    
    /**
     * Function that parses the provided string looking for the code that
     * identifies the stock market and returns the MarketEnum value for 
     * that stock exchange if one is found
     * @param strCode - String to parse for stock market code
     * @return A MarketEnum representing the stock market identified in the 
     * parsed string or NULL is none could be found.
     */
    public static MarketEnum getMarketFromString(String strCode){
        MarketEnum result = null;
        if(null != strCode && 0 < strCode.length()){
            if(strCode.equalsIgnoreCase("NYSE")){
                result = NYSE;
            }
            if(strCode.equalsIgnoreCase("NASDAQ")){
                result = NASDAQ;
            }
        }
        return result;
    }
    
}
