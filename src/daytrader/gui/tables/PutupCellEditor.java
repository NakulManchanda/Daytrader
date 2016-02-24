/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.gui.tables;

import daytrader.datamodel.Putup;
import daytrader.gui.PutupEntryDisplay;
import java.awt.Component;
import javax.swing.AbstractCellEditor;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;

/**
 *
 * @author Roy
 */
public class PutupCellEditor extends AbstractCellEditor implements TableCellEditor {
    
    private PutupEntryDisplay editor;
    
    public PutupCellEditor(){
        this.editor = new PutupEntryDisplay();
    }

    @Override
    public Object getCellEditorValue() {
        Object result = null;
        if(null != this.editor){
            result = this.editor.getMyPutup();
        }
        return result;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        Component result = null;
        if(value instanceof Putup){
            Putup newData = (Putup)value;
            this.editor.setMyPutup(newData);
            result = this.editor;
        }
        if(null == result){
            result = new JLabel("ERROR");
        }
        return result;
    }
    
}
