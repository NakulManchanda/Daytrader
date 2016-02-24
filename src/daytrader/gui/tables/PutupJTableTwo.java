/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.gui.tables;

import daytrader.datamodel.Putup;
import javax.swing.JTable;

/**
 *
 * @author Roy
 */
public class PutupJTableTwo extends JTable {

    private PutupCellRenderer renderer;
    private PutupCellEditor editor;
    private PutupTableModelTwo myDataModel;

    public PutupJTableTwo() {
        this.renderer = new PutupCellRenderer();
        this.editor = new PutupCellEditor();
        this.myDataModel = new PutupTableModelTwo();
        this.setModel(this.myDataModel);
        this.setDefaultRenderer(Putup.class, this.renderer);
        this.setDefaultEditor(Putup.class, this.editor);
        this.setRowHeight(this.renderer.getPreferedSize().height);
    }

    /**
     * @return the myDataModel
     */
    public PutupTableModelTwo getMyDataModel() {
        return this.myDataModel;
    }

    @Override
    public Object getValueAt(int row, int column) {
        Object result = super.getValueAt(row, column);
        if (row < this.myDataModel.getData().getAllPutupsList().size()) {
            try {
                result = this.myDataModel.getData().getAllPutupsList().get(row);
            } catch (Exception ex) {
            }
        }
        return result;
    }
}
