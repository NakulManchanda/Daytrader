/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.interfaces;

/**
 * Interface to be implemented by any class that wishes to persist itself to an
 * Excel style CSV file
 * @author Roy
 */
public interface ICSVPersistable {
    
    /**
     * This method allows the implementing class to convert itself to a CSV String
     * suitable for export to an Excel Spreadsheet (ie to serialise itself).
     * @return The objects parameters encoded as a CSV String
     */
    String toCSVString();
    
    /**
     * Given a CSV String this method allows the object to parse a CSV string and intialise
     * itself based on the data encoded in the string.
     * @param strData - The CSV data string to be parsed
     * @return boolean True if the string could be parsed and the object successfully initialised
     * from its contents, False otherwise.
     */
    boolean fromCSVString(String strData);   //IMPLEMENT THIS LATER NOW!!!!
    
}
