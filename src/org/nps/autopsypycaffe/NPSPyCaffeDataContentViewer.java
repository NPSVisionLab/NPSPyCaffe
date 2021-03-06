/*
 * Derived from https://github.com/LoWang123/CopyMoveModulePackage
 *
 * @author Tobi
 */

package org.nps.autopsypycaffe;

import java.awt.BasicStroke;
import java.awt.Color;
import java.io.File;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingConstants;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

@ServiceProvider(service = DataContentViewer.class)
public class NPSPyCaffeDataContentViewer extends javax.swing.JPanel implements DataContentViewer, ItemListener{
    
    private String currentName = "";
    private String currentFileExtension = "";
    private BufferedImage currentImage = null;
    private final JFileChooser fileChooser = new JFileChooser();
    private AbstractFile lastFile = null;
    private Logger logger = IngestServices.getInstance().getLogger("NPSPyCaffeDataContentViewer");
    
    /**
     * Creates new form CopyMoveForgeryDetectionDataContentViewer
     */
    public NPSPyCaffeDataContentViewer() {
        initComponents();
    }

    
    @Override
    public void setNode(Node selectedNode) {
        AbstractFile abstractFile = selectedNode.getLookup().lookup(AbstractFile.class);
        lastFile = abstractFile;
        try {
            initializeResultImage(abstractFile);
            panelImage.addComponentListener(new resizeListener());
            this.addComponentListener(new resizeListener());
        }
        catch (IOException ex) {
            logger.log(Level.SEVERE, "Error when trying to read Image from Blackboard (setNode)", ex);
        }
        catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error when trying to access Blackboard (setNode)", ex);
        }
        catch (Exception ex) {
            logger.log(Level.SEVERE, "Unknown exception (setNode)", ex);
        }
        
    }
    
    
    private void drawRectangle(java.awt.Graphics2D g, int fheight, String objName, String dstring){
        StringTokenizer tok = new StringTokenizer(dstring);
        if (tok.hasMoreElements()){
            String next = tok.nextToken();
            Double score = Double.valueOf(next);
            next = tok.nextToken();
            Double x = Double.valueOf(next);
            next = tok.nextToken();
            Double y = Double.valueOf(next);
            next = tok.nextToken();
            Double width = Double.valueOf(next);
            next = tok.nextToken();
            Double height = Double.valueOf(next);
            int ix = (int)Math.round(x);
            int iy = (int)Math.round(y);
            if (width > 0 && height > 0){
                g.drawLine(ix, iy, 
                           (int)Math.round(x+ width - 1.0) , iy);
                //Hoz bottom           
                g.drawLine(ix, (int)Math.round(y+  height - 1.0), 
                           (int)Math.round(x+  width) , (int)Math.round(y+ height)); 
                //Virt left
                g.drawLine(ix, iy, 
                           ix , (int)Math.round(y+ height)); 
                //Virt right
                g.drawLine((int)Math.round(x+  width), (int)Math.round(y), 
                           (int)Math.round(x+  width) , (int)Math.round(y+ height)); 
            }
            //Draw Label                     
            g.drawString( objName + " : " + String.format("%.2f", score), ix + 2, iy + fheight);
        }
    }
    
    private void redrawImage(ArrayList<String> names){
        
        try {
            ArrayList<BlackboardArtifact> artifacts = 
                    lastFile.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
        
            BufferedImage image = ImageFileParser.parseAbstractFile(lastFile);
             // draw the image and then we will draw each rectangle
            java.awt.Graphics2D g = image.createGraphics();
            g.setColor(Color.yellow);
            g.setStroke(new BasicStroke(3));
            FontMetrics fm = g.getFontMetrics();
            int fheight = fm.getHeight();
            for(BlackboardArtifact artifact: artifacts) {
                boolean found = false;
                String objName = null;
                for(BlackboardAttribute attribute: artifact.getAttributes(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME)) {
                    if(attribute.getModuleName().equals(NPSPyCaffeFactory.getModuleName())) {
                        objName = attribute.getValueString();
                        for (String next : names){
                            if (objName.equals(next)){
                                found = true;
                                break;
                            }
                        }
                    }
                }
                if (found){
                    for(BlackboardAttribute attribute: artifact.getAttributes(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE)) {
                        if(attribute.getModuleName().equals(NPSPyCaffeFactory.getModuleName())) {
                            String dstring = attribute.getValueString();
                            drawRectangle(g, fheight, objName, dstring);
                        }
                    }
                }
    
            }    
            setImage(image);
        } catch (Exception ex){
            logger.log(Level.WARNING, "Image type not supported ", ex);
        }
    }

    private void initializeResultImage(AbstractFile abstractFile) throws TskCoreException, IOException{
        
        
        ArrayList<BlackboardArtifact> artifacts = abstractFile.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
  
        try {
            BufferedImage image = ImageFileParser.parseAbstractFile(abstractFile);
             // draw the image and then we will draw each rectangle
            java.awt.Graphics2D g = image.createGraphics();
            g.setColor(Color.yellow);
            g.setStroke(new BasicStroke(3));
            FontMetrics fm = g.getFontMetrics();
            int fheight = fm.getHeight();
            buttonPanel.removeAll();
            BoxLayout layoutButtons = new BoxLayout(buttonPanel, BoxLayout.Y_AXIS);
            buttonPanel.setLayout(layoutButtons);
            for(BlackboardArtifact artifact: artifacts) {
                String objName = "";
                for(BlackboardAttribute attribute: artifact.getAttributes(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME)) {
                    if(attribute.getModuleName().equals(NPSPyCaffeFactory.getModuleName())) {
                        objName = attribute.getValueString();
                        break;
                    }
                }
                for(BlackboardAttribute attribute: artifact.getAttributes(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE)) {
                    if(attribute.getModuleName().equals(NPSPyCaffeFactory.getModuleName())) {
                        String dstring = attribute.getValueString();
                        drawRectangle(g, fheight, objName, dstring);
                    }
                }
                if (objName.equals("") == false){
                    // Create a button for each objName in the image
                    
                    JCheckBox button = new JCheckBox(objName);
                    button.setBackground(Color.white);
                    button.setSelected(true);
                    button.setVerticalAlignment(SwingConstants.CENTER);
                    button.setActionCommand(objName);
                    button.addItemListener(this);
                    buttonPanel.setAlignmentY(0.0f);
                    buttonPanel.add(button);
                }
                buttonPanel.revalidate();
                buttonPanel.repaint();
            }
            setImage(image);
        }catch (IOException ex){
            logger.log(Level.WARNING, "Image type not supported ", ex);
        }
       
        //currentName = abstractFile.getName().replace("." + abstractFile.getNameExtension(), "") + "_Result";       
        //currentFileExtension = abstractFile.getNameExtension();
        
       
        
    }

    private void setImage(BufferedImage image) {
        int pwidth = panelImage.getWidth();
        int pheight = panelImage.getHeight();
        int bwidth = buttonPanel.getWidth();
 
        if (pwidth == 0){
            pwidth = this.getWidth();
            pheight = this.getHeight(); 
        }
        pwidth -= bwidth + 20;
        double differenceWidth = (double)image.getWidth() / (double)pwidth;
        double differenceHeight = (double)image.getHeight() / (double)pheight;

        Image scaledImage = image;

        if(differenceWidth > differenceHeight) {
            if(differenceWidth > 1) {
                int height = (int)((double)image.getHeight() / differenceWidth);
                scaledImage = image.getScaledInstance(panelImage.getWidth(), height, Image.SCALE_FAST);
            }
        }
        else {
            if(differenceHeight > 1) {
                int width = (int)((double)image.getWidth() / differenceHeight);
                scaledImage = image.getScaledInstance(width, panelImage.getHeight(), Image.SCALE_FAST);
            }
        }
        
        
        currentImage = image;
        ImageIcon icon = new ImageIcon(scaledImage);
        imageLabel.setIcon(icon);
    } 
    
    
    @Override
    public String getTitle() {
        return "Image Detection";
    }

    @Override
    public String getToolTip() {
        return "Displays the results from the NPSPyCaffeFileIngestModule.";
    }

    @Override
    public DataContentViewer createInstance() {
        return new NPSPyCaffeDataContentViewer();
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void resetComponent() {
    }

    @Override
    public boolean isSupported(Node node) {
        AbstractFile abstractFile = node.getLookup().lookup(AbstractFile.class);
        return isSupported(abstractFile);
    }

    private boolean isSupported(AbstractFile abstractFile) {
        
        if (abstractFile == null) {
            return false;
        }
        try {
            boolean cmfdResultFound = false;
            ArrayList<BlackboardArtifact> artifacts = abstractFile.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
            
            for(BlackboardArtifact artifact: artifacts) {
                for(BlackboardAttribute attribute: artifact.getAttributes(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE)) {
                    if(attribute.getModuleName().equals(NPSPyCaffeFactory.getModuleName())) {
                        cmfdResultFound = true;
                    }
                }
                for(BlackboardAttribute attribute: artifact.getAttributes(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT)) {
                    if(attribute.getModuleName().equals(NPSPyCaffeFactory.getModuleName())) {
                        cmfdResultFound = true;
                    }
                }
            }
            
            if(cmfdResultFound) {
                return true;
            }
        }
        catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error when trying to read BlackboardAttributes (isSupported)", ex);
            return false;
        }
        return false;
    }
    
    @Override
    public int isPreferred(Node node) {
        AbstractFile abstractFile = node.getLookup().lookup(AbstractFile.class);
        if(isSupported(abstractFile)) {
            return 9;
        }
        return 0;
    }
    
    class resizeListener extends ComponentAdapter {
        @Override
        public void componentResized(ComponentEvent e) {            
            setImage(currentImage);
        }
    }
    
    @Override
    public void itemStateChanged(ItemEvent e){
        ArrayList<String> displayObjects = new ArrayList<String>();
        for (Component c : buttonPanel.getComponents()){
            if (c instanceof JCheckBox) {
                JCheckBox cbox = (JCheckBox)c;
                if (cbox.isSelected()){
                    String name = ((JCheckBox)c).getActionCommand();
                    displayObjects.add(name);
                }
            }
        }
        redrawImage(displayObjects);
    }
    
    
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        panelImage = new javax.swing.JPanel();
        imageLabel = new javax.swing.JLabel();
        buttonPanel = new javax.swing.JPanel();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));

        setBackground(new java.awt.Color(255, 255, 255));
        setLayout(new java.awt.GridLayout(1, 0));

        panelImage.setBackground(new java.awt.Color(255, 255, 255));

        imageLabel.setBackground(new java.awt.Color(0, 153, 255));
        imageLabel.setText(org.openide.util.NbBundle.getMessage(NPSPyCaffeDataContentViewer.class, "NPSPyCaffeDataContentViewer.imageLabel.text")); // NOI18N
        imageLabel.setToolTipText(org.openide.util.NbBundle.getMessage(NPSPyCaffeDataContentViewer.class, "NPSPyCaffeDataContentViewer.imageLabel.toolTipText")); // NOI18N

        buttonPanel.setBackground(new java.awt.Color(255, 255, 255));

        javax.swing.GroupLayout buttonPanelLayout = new javax.swing.GroupLayout(buttonPanel);
        buttonPanel.setLayout(buttonPanelLayout);
        buttonPanelLayout.setHorizontalGroup(
            buttonPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 81, Short.MAX_VALUE)
        );
        buttonPanelLayout.setVerticalGroup(
            buttonPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout panelImageLayout = new javax.swing.GroupLayout(panelImage);
        panelImage.setLayout(panelImageLayout);
        panelImageLayout.setHorizontalGroup(
            panelImageLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelImageLayout.createSequentialGroup()
                .addComponent(imageLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 636, Short.MAX_VALUE)
                .addGap(18, 18, 18)
                .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(buttonPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        panelImageLayout.setVerticalGroup(
            panelImageLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(imageLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 468, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panelImageLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(buttonPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(panelImageLayout.createSequentialGroup()
                .addGap(227, 227, 227)
                .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        add(panelImage);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel buttonPanel;
    private javax.swing.Box.Filler filler1;
    private javax.swing.JLabel imageLabel;
    private javax.swing.JPanel panelImage;
    // End of variables declaration//GEN-END:variables
}
