/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.gui.tables;

import daytrader.datamodel.AtrClassValue;
import daytrader.gui.AtrClassValueEntryPanel;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

/**
 *
 * @author Roy
 */
public class AtrParamJTableTwo extends JTable {
    
    private AtrClassValueEntryPanel panelToRender;
    
    public AtrParamJTableTwo(){
        panelToRender = new AtrClassValueEntryPanel();
        this.setModel(new AtrParamTableModelTwo());
        this.setRowHeight(panelToRender.getPreferredSize().height);
        this.setDefaultRenderer(AtrClassValue.class, panelToRender);
        this.setDefaultEditor(AtrClassValue.class, new AtrClassTabelCellEditor());
    }

    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
        TableModel model = this.getModel();
        TableCellRenderer renderer = null;
        if(model instanceof AtrParamTableModelTwo){
            AtrParamTableModelTwo myModel = (AtrParamTableModelTwo)model;
            Object value = myModel.getValueAt(row, column);
            if(value instanceof AtrClassValue){
                AtrClassValue item = (AtrClassValue)value;
                panelToRender.setAtrValue(item);
                renderer = panelToRender;
            }
        }
        return renderer;
    }

    @Override
    public TableCellEditor getCellEditor(int row, int column) {
        return super.getCellEditor(row, column); //To change body of generated methods, choose Tools | Templates.
    }
    
    
    
}
