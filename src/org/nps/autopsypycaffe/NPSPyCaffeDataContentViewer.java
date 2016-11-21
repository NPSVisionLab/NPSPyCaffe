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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
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
public class NPSPyCaffeDataContentViewer extends javax.swing.JPanel implements DataContentViewer{
    
    private String currentName = "";
    private String currentFileExtension = "";
    private BufferedImage currentImage = null;
    private final JFileChooser fileChooser = new JFileChooser();
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
                            //Draw Label                     
                            g.drawString( objName + " : " + String.format("%.2f", score), ix + 2, iy + fheight);
                            
                        }
                        
                    }
                }    
            }
            setImage(image);
        }catch (IOException ex){
            logger.log(Level.WARNING, "Image type not supported ", ex);
        }
       
        currentName = abstractFile.getName().replace("." + abstractFile.getNameExtension(), "") + "_Result";       
        currentFileExtension = abstractFile.getNameExtension();
        
       
        
    }

    private void setImage(BufferedImage image) {
        int pwidth = panelImage.getWidth();
        int pheight = panelImage.getHeight();
        if (pwidth == 0){
            pwidth = this.getWidth();
            pheight = this.getHeight();
           
        }
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
            return 2;
        }
        return 0;
    }
    
    class resizeListener extends ComponentAdapter {
        @Override
        public void componentResized(ComponentEvent e) {            
            setImage(currentImage);
        }
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

        setBackground(new java.awt.Color(255, 255, 255));
        setLayout(new java.awt.GridLayout());

        panelImage.setBackground(new java.awt.Color(255, 255, 255));

        imageLabel.setBackground(new java.awt.Color(0, 153, 255));
        imageLabel.setText(org.openide.util.NbBundle.getMessage(NPSPyCaffeDataContentViewer.class, "NPSPyCaffeDataContentViewer.imageLabel.text")); // NOI18N
        imageLabel.setToolTipText(org.openide.util.NbBundle.getMessage(NPSPyCaffeDataContentViewer.class, "NPSPyCaffeDataContentViewer.imageLabel.toolTipText")); // NOI18N

        javax.swing.GroupLayout panelImageLayout = new javax.swing.GroupLayout(panelImage);
        panelImage.setLayout(panelImageLayout);
        panelImageLayout.setHorizontalGroup(
            panelImageLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(imageLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
        );
        panelImageLayout.setVerticalGroup(
            panelImageLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(imageLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 468, Short.MAX_VALUE)
        );

        add(panelImage);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel imageLabel;
    private javax.swing.JPanel panelImage;
    // End of variables declaration//GEN-END:variables
}
