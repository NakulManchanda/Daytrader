/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

/**
 * This represents one entry in the IGLimitEntry Table, It associates a score
 * and price value together
 * @author Roy
 */
public class IGLimitEntry implements Comparable<IGLimitEntry>{
    
    private Double score;
    private Double price;
    
    /**
     * Constructor providing the score and price valus to store
     * @param dblScore Double being the score value to store
     * @param dblPrice Double being the price value to store
     */
    public IGLimitEntry(double dblScore, double dblPrice){
        this.score = dblScore;
        this.price = dblPrice;
    }

    /**
     * @return the score
     */
    public Double getScore() {
        return score;
    }

    /**
     * @return the price
     */
    public Double getPrice() {
        return price;
    }

    @Override
    public int compareTo(IGLimitEntry o) {
        int result = 0;
        //We need to order these by the price value
        if(null != o){
            Double oPrice = o.getPrice();
            Double diff = this.price - oPrice;
            if(diff < 0){
                result = -1;
            }else if(diff > 0){
                result = 1;
            }
        }
        return result;
    }

    /**
     * Accessor method to retrieve the score value.
     * @param score Double being the score for this entry
     */
    public void setScore(Double score) {
        this.score = score;
    }

    /**
     * Accessor method to retrieve the price value.
     * @param price Double being the price for this entry
     */
    public void setPrice(Double price) {
        this.price = price;
    }
    
}
