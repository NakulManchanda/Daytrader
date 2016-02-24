/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.historicRequestSystem;

/**
 * This enumeration allows a priority level to be associated with requests.
 * @author Roy
 */
public enum PriorityEnum {
    
    /**
     * Highest Priority level indicating that the Historic Request Processing System
     * should execute this request at the earliest available time.
     */
    IMMEDIATE(1),
    /**
     * High priority request that should be processed ahead of any other request 
     * except on requesting immediate execution.
     */
    HIGH(2),
    /**
     * Default priority level assigned to requests
     */
    STANDARD(3),
    /**
     * Lowest priority level, such requests are only executed after all other 
     * requests.
     */
    LOW(4);
    
    private int type;
    
    /**
     * Constructor that allows this enumeration to assign specific integer values to
     * its enumerated values on creation.
     * @param i - integer being the integer number to use for this enumeration value;
     */
    PriorityEnum (int i)
    {
        this.type = i;
    }
    
    /**
     * Accessor to retrieve the integer number associated with this Priority Level
     * @return integer being the value used to denote this priority level
     */
    public int getNumericType()
    {
        return type;
    }
}
