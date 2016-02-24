/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

/**
 * The standard historic data price time point encapsulates five different types of price that
 * are available for a security. This enumeration reflects these five types. Note each price /
 * time point represents at a minimum at least 1 second but possibly a longer time period.
 * @author Roy
 */
public enum DTPriceEnum {

    /**
     * The securities trading price at the start of the time period.
     */
    OPEN,
    /**
     * The highest price at which the security traded in the time period
     */
    HIGH,
    /**
     * The lowest price at which the security traded in the time period
     */
    LOW,
    /**
     * The securities trading price at the end of the time period.
     */
    CLOSE,
    /**
     * The securities Weighted Average Price (WAP) applies throughout the time period
     * and is the basic value used in the applications calculations
     */
    WAP;

    /**
     * Given any base graph this method will extract the AbstractGraphPoint with
     * the 'best' price for a given type of price point. 'Best' is defines as follows
     * OPEN and CLOSE = First or Last price / time point in the graph
     * HIGH and LOW = The point with the HIGHEST HIGH or LOWEST LOW respectively
     * WAP = The point with the HIGHEST WAP price.
     * @param graph - A BaseGraph of AbstractGraphPoints to examine
     * @param priceReq - The type of price to search for
     * @return - An AbstractGraphPoint encapsulating the appropriate price / time point
     * or NULL if none is found.
     */
    public static AbstractGraphPoint getBestPrice(BaseGraph<AbstractGraphPoint> graph, DTPriceEnum priceReq) {
        AbstractGraphPoint result = null;
        if (null != graph && null != priceReq) {
            switch (priceReq) {
                case OPEN:
                    result = graph.first();
                    break;
                case HIGH:
                    for(AbstractGraphPoint currPoint : graph){
                        if(null == result){
                            result = currPoint;
                        }else{
                            if(currPoint.getHigh()> result.getHigh()){
                                result = currPoint;
                            }
                        }
                    }
                    break;
                case LOW:
                    for(AbstractGraphPoint currPoint : graph){
                        if(null == result){
                            result = currPoint;
                        }else{
                            if(currPoint.getLow()> result.getLow()){
                                result = currPoint;
                            }
                        }
                    }
                    break;
                case CLOSE:
                    result = graph.last();
                    break;
                case WAP:
                    for(AbstractGraphPoint currPoint : graph){
                        if(null == result){
                            result = currPoint;
                        }else{
                            if(currPoint.getWAP() > result.getWAP()){
                                result = currPoint;
                            }
                        }
                    }
                    break;
            }
        }
        return result;
    }
}
