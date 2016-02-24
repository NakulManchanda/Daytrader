/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.gui.tables;

import daytrader.datamodel.AtrClassEnum;
import daytrader.datamodel.AtrClassGrid;
import daytrader.datamodel.AtrClassValue;
import daytrader.gui.DaytraderMainForm;
import javax.swing.table.AbstractTableModel;

/**
 * This table model works with the revised AtrClassValues
 * @author Roy
 */
public class AtrParamTableModelTwo extends AbstractTableModel {
    
    private AtrClassGrid data;
    
    public AtrParamTableModelTwo(){
        this.data = DaytraderMainForm.atrData;
    }

    @Override
    public int getRowCount() {
        return this.data.getHeight();
    }

    @Override
    public int getColumnCount() {
        return this.data.getWidth();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return this.data.getItemAt(rowIndex, columnIndex);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        boolean result = false;
        if(!this.data.isLockedIn()){
            result = true;
        }
        return result;
    }

    @Override
    public String getColumnName(int column) {
        String result = "";
        AtrClassEnum val = AtrClassEnum.values()[column];
        result = val.toString();
        return result;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return AtrClassValue.class;
    }
    
}
