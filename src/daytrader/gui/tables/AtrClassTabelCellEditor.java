/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.gui.tables;

import daytrader.datamodel.AtrClassValue;
import daytrader.gui.AtrClassValueEntryPanel;
import java.awt.Component;
import javax.swing.AbstractCellEditor;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;

/**
 *
 * @author Roy
 */
public class AtrClassTabelCellEditor extends AbstractCellEditor implements TableCellEditor {
    
    private AtrClassValue data;
    private int row;
    private int col;

    @Override
    public Object getCellEditorValue() {
        return this;
    }

    /**
     * @return the data
     */
    public AtrClassValue getData() {
        return data;
    }

    /**
     * @return the row
     */
    public int getRow() {
        return row;
    }

    /**
     * @return the col
     */
    public int getCol() {
        return col;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        Component result = new JLabel();
        if(value instanceof AtrClassValue){
            this.data = (AtrClassValue)value;
            this.row = row;
            this.col = column;
            AtrClassValueEntryPanel panel = new AtrClassValueEntryPanel();
            panel.setAtrValue(data);
            result = panel;
        }
        return result;
    }
    
}
