/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

/**
 * This enumeration defines the type of Putup
 * @author Roy
 */
public enum PutupTypeEnum {
    
    /**
     * A Putup type where if the market is entered by the application it will take a 'Long' 
     * position on the security as defined at http://en.wikipedia.org/wiki/Long_%28finance%29
     */
    LONGS,
    /**
     * A Putup type where if the market is entered by the application it will take a 'Short' 
     * position on the security as defined at http://en.wikipedia.org/wiki/Short_%28finance%29
     */
    SHORTS;

    @Override
    public String toString() {
        String result = "";
        switch(this){
            case LONGS:
                result = "LONGS";
                break;
            case SHORTS:
                result = "SHORTS";
                break;
        }
        return result;
    }
    
    /**
     * This method parses the provided string and identifies the Putup Type contained
     * in the string.
     * @param data - A String containing the putup type
     * @return A PutupTypeEnum parsed from the string parameter or NULL if no type
     * could be found.
     */
    public static PutupTypeEnum getTypeFromString(String data){
        PutupTypeEnum result = null;
        if(data.equalsIgnoreCase("LONGS")){
            result = PutupTypeEnum.LONGS;
        }
        if(data.equalsIgnoreCase("SHORTS")){
            result = PutupTypeEnum.SHORTS;
        }
        return result;
    }
    
}
