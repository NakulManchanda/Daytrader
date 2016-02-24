/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.gui.tables;

import daytrader.datamodel.Putup;
import daytrader.gui.PutupEntryDisplay;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

/**
 *
 * @author Roy
 */
public class PutupCellRenderer implements TableCellRenderer {
    
    private PutupEntryDisplay renderer;
    
    public PutupCellRenderer(){
        this.renderer = new PutupEntryDisplay();
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        Component result = null;
        if(value instanceof Putup){
            Putup newData = (Putup)value;
            this.renderer.setMyPutup(newData);
            result = this.renderer;
        }
        if(null == result){
            result = new JLabel("ERROR");
        }
        return result;
    }
    
    public Dimension getPreferedSize(){
        return this.renderer.getPreferredSize();
    }
}
