/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nps.autopsypycaffe;

/**
 *
 * @author tomb
 */


import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModule.ProcessResult;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.ingest.IngestMonitor;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskException;

/**
 * File-level ingest module that runs a py-faster-rcnn image detection
 * on the file input.
 
 */
public class NPSPyCaffeFileIngestModule implements FileIngestModule {

    private static final Logger logger = Logger.getLogger(NPSPyCaffeFileIngestModule.class.getName());
    private IngestServices services = IngestServices.getInstance();
    private FileManager fileManager;
    private IngestJobContext context;
    private Properties props;
    private NPSPyCaffeIngestJobSettings localSettings;
    private static final HashMap<Long, Long> artifactCountsForIngestJobs = new HashMap<>();
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private PyDetect detector;
    
    



    NPSPyCaffeFileIngestModule(IngestModuleIngestJobSettings settings, Properties props) {
        localSettings = (NPSPyCaffeIngestJobSettings)settings;
        this.props = props;
        
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
        fileManager = Case.getCurrentCase().getServices().getFileManager();
        
    }
    
    public void setDetector(PyDetect detect){
        detector = detect;
    }
    
    public FileManager getFileManager() {
        return fileManager;
    }
    
    public IngestJobContext getContext(){
        return context;
    } 
    
    public void displayErrorMessage(String mess1, String mess2, String mess3){
        IngestMessage msg = IngestMessage.createErrorMessage(NPSPyCaffeFactory.getModuleName(), mess1,
                    mess2 + " " + mess3);
                          
        services.postMessage(msg);
    }
    

    @Override
    public ProcessResult process(AbstractFile abstractFile) {


        // skip known
        if (abstractFile.getKnown().equals(TskData.FileKnown.KNOWN)) {
            return ProcessResult.OK;
        }

        //skip unalloc
        if (abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)) {
            return ProcessResult.OK;
        }
        if (abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)) {
            return ProcessResult.OK;
        }
        if ((abstractFile.isFile() == false)) {
            return ProcessResult.OK;
        }
        if (!ImageFileParser.isSupportedImageFile(abstractFile)){
            return ProcessResult.OK;
        }
       
        String detectorScript = props.getProperty("main.pyscript");
        if (detectorScript == null){
            logger.log(Level.SEVERE, "Could not find property 'main.pyscript'");
            displayErrorMessage("Property Error", "Could not find property 'main.pyscript'", "");
            return ProcessResult.ERROR;
        }
        detectorScript = NPSPyCaffeFactory.getBasePath() + detectorScript;
        if (new File(detectorScript).exists() == false){
            logger.log(Level.SEVERE, "Detector script " + detectorScript + " does not exist!!!");
            displayErrorMessage("Property Error", "Detector script does not exist", detectorScript);
            return ProcessResult.ERROR;
        }
        String pythonExe = props.getProperty("main.python");
        if (pythonExe == null || new File(pythonExe).exists() == false){
            logger.log(Level.SEVERE, "Not a valid python executeable in properties: " + pythonExe + " does not exist!!!");
            displayErrorMessage("Property Error", "Could not find python executable!", 
                                pythonExe);
            return ProcessResult.ERROR;
        }
        
        String detectorName = localSettings.getDetector();
        if (detectorName == null || detectorName.equals("")){
            logger.log(Level.SEVERE, "No detector was selected!");
            displayErrorMessage("Detector error", "No Detector was selected", "");
            return ProcessResult.ERROR;
        }
        String modelfile = props.getProperty(detectorName + ".model");
        if (modelfile == null){
            logger.log(Level.SEVERE, "Could not find property " + detectorName + ".model");
            displayErrorMessage("Property error", "Could not find property", detectorName + ".model");
            return ProcessResult.ERROR;
        }
        modelfile = NPSPyCaffeFactory.getBasePath() + modelfile;
        if (new File(modelfile).exists() == false){
            logger.log(Level.SEVERE, "model file " + modelfile + " does not exist!!!");
            displayErrorMessage("Property error", "Model file does not exist", modelfile);
            return ProcessResult.ERROR;
        }
        String prototxt = props.getProperty(detectorName + ".prototxt");
        if (prototxt == null){
            logger.log(Level.SEVERE, "Could not find property " + detectorName + ".prototxt");
            displayErrorMessage("Property error", "Could not find property", detectorName + ".prototxt");
            return ProcessResult.ERROR;
        }
        prototxt = NPSPyCaffeFactory.getBasePath() + prototxt;
        if (new File(prototxt).exists() == false){
            logger.log(Level.SEVERE, "prototxt file " + prototxt + " does not exist!!!");
            displayErrorMessage("Property error", "Prototxt file does not exist", prototxt);
            return ProcessResult.ERROR;
        }
        String cfg = props.getProperty(detectorName + ".config");
        if (cfg == null){
            logger.log(Level.SEVERE, "Could not find property " + detectorName + ".config");
            return ProcessResult.ERROR;
        }
        cfg = NPSPyCaffeFactory.getBasePath() + cfg;
        if (new File(cfg).exists() == false){
            logger.log(Level.SEVERE, "config file " + cfg + " does not exist!!!");
            displayErrorMessage("Property error", "Config file does not exist", cfg);
            return ProcessResult.ERROR;
        }
        String classes = props.getProperty(detectorName + ".classes");
        if (classes == null){
            logger.log(Level.SEVERE, "Could not find property " + detectorName + ".classes");
            displayErrorMessage("Property error", "Could not find property", detectorName + ".classes");
            return ProcessResult.ERROR;
        }
        // Optional confidence
        String confidence = props.getProperty(detectorName + ".confidence");
        
        
        String imageFileName = getTempPath() + File.separator + abstractFile.getName()
                + "-" + String.valueOf(abstractFile.getId());
        File file = new File(imageFileName);
        long freeSpace = services.getFreeDiskSpace();
        if ((freeSpace != IngestMonitor.DISK_FREE_SPACE_UNKNOWN) && (abstractFile.getSize() >= freeSpace)) {
            logger.log(Level.WARNING, "Not enough disk space to write file to disk."); //NON-NLS
            displayErrorMessage("NPSPYCaffe saving image file", "NPSPyCaffeFileIngestModule.errMsg.outOfDiskSpace",
                                abstractFile.getName());
            return ProcessResult.OK;
        }
        try {
            ContentUtils.writeToFile(abstractFile, file);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed writing image file to disk.", ex); //NON-NLS
            return ProcessResult.OK;
        }
 
        ArrayList<PyDetect.DetectionResult> detRes;
        PyDetect detect = detector;
        detector.putAbsMapAFile(imageFileName, abstractFile);
        try {
            if (NPSPyCaffeFactory.hasGPU()){
                // We only going to have one detect object in GPU mode
                // so lets not step on each other processing detects!
                synchronized(detect){
                    detRes = detect.getDetections(detectorScript, modelfile, prototxt, 
                         cfg, classes, confidence, imageFileName, abstractFile);
                    blackboardDetection(context, detRes);
                }
            } else {
                detRes = detect.getDetections(detectorScript, modelfile, prototxt, 
                         cfg, classes, confidence, imageFileName, abstractFile);
                blackboardDetection(context, detRes);
            }
        } catch (Exception ex){
            logger.log(Level.SEVERE, "Exception from getDetections", ex);
            return ProcessResult.ERROR;
        }
        
       
        
        /* Can't delete the file until close
        if (file.delete() == false) {
            logger.log(Level.INFO, "Failed to delete temp file: {0}", file.getName()); //NON-NLS
        }
        */
        return ProcessResult.OK;
    }
    
    
    public void blackboardDetection(IngestJobContext context, ArrayList<PyDetect.DetectionResult> detRes) {
        
        boolean foundDetection = false;
        Blackboard blackboard = Case.getCurrentCase().getServices().getBlackboard();
        try {
            for (PyDetect.DetectionResult det : detRes){
                AbstractFile abstractFile = detector.getAbsMapAFile(det.fileName);
                if (abstractFile == null){
                    logger.log(Level.SEVERE, "Could not find Abstract file from " + det.fileName);
                    continue;
                }
                ArrayList<BlackboardArtifact> artifacts = abstractFile.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
                // See if this file has already added this obj detection to the Blackboard
                BlackboardArtifact prevInteresting = null;
                for(BlackboardArtifact artifact: artifacts) {
                    for(BlackboardAttribute attribute: artifact.getAttributes(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME)) {
                        if(attribute.getModuleName().equals(NPSPyCaffeFactory.getModuleName())) {
                            String name = attribute.getValueString();
                            if (name.equals(det.objName)){
                                prevInteresting = artifact;
                                break;
                            }
                        }
                    }
                    if (prevInteresting != null){
                        break;
                    }
                }

                String dstring = String.format("%.2f",det.score) + " " + String.valueOf(Math.round(det.pix.getX())) + " " + 
                         String.valueOf(Math.round(det.pix.getY())) + " " + String.valueOf(Math.round(det.width)) +
                        " " + String.valueOf(Math.round(det.height));

                try {
                    BlackboardArtifact art = null;
                    if (prevInteresting == null){
                        art = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT);
                        art.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD, 
                            NPSPyCaffeFactory.getModuleName(), det.objName));
                    }
                    foundDetection = true;
                    BlackboardArtifact art2;
                    if (prevInteresting == null){
                        art2 = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
                        art2.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, 
                            NPSPyCaffeFactory.getModuleName(), det.objName));
                        art2.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE, 
                            NPSPyCaffeFactory.getModuleName(), dstring));
                    }else {
                        art2 = prevInteresting;
                        art2.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VALUE, 
                            NPSPyCaffeFactory.getModuleName(), dstring));
                    }
                    
                    try {
                        // index the artifact for keyword search
                        if (art != null){
                            blackboard.indexArtifact(art);
                        }
                        blackboard.indexArtifact(art2);
                    } catch (Blackboard.BlackboardException ex) {
                        MessageNotifyUtil.Notify.error("Failed to index artiface", 
                                art.getDisplayName());
                        logger.log(Level.SEVERE, "Unable to index blackboard artifact ", ex); //NON-NLS
                    }
                    addToBlackboardPostCount(context.getJobId(), 1L);
                }catch (Exception e){
                    logger.log(Level.SEVERE, "Creating artifact", e);    
                }
            }
            
        }catch (Exception e){
            logger.log(Level.SEVERE, "Creating artifact", e);    
        }
         // Must wait until we have put results on blackboard before removing entry in absMap
        for (PyDetect.DetectionResult d : detRes){
            detector.removeAbsMapFile(d.fileName);
        }
        
        if (foundDetection){
           
            services.fireModuleDataEvent(new ModuleDataEvent(NPSPyCaffeFactory.getModuleName(),
                        BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT));
            services.fireModuleDataEvent(new ModuleDataEvent(NPSPyCaffeFactory.getModuleName(),
                        BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT));
        }
    }
   

    
    @Override
    public void shutDown() {
        logger.log(Level.INFO, "Shutdown called");
        ArrayList<PyDetect.DetectionResult> res;
        if (NPSPyCaffeFactory.hasGPU()){
            synchronized(detector){
                res = detector.close();
                blackboardDetection(context, res);
                detector.clearAbsMap();
            }
        }else {
            res = detector.close();
            blackboardDetection(context, res);
            detector.clearAbsMap();
        }
        
        // This method is thread-safe with per ingest job reference counted
        // management of shared data.
        reportBlackboardPostCount(context.getJobId());
    }
 
    synchronized static void addToBlackboardPostCount(long ingestJobId, long countToAdd) {
        Long fileCount = artifactCountsForIngestJobs.get(ingestJobId);
 
        // Ensures that this job has an entry
        if (fileCount == null) {
            fileCount = 0L;
            artifactCountsForIngestJobs.put(ingestJobId, fileCount);
        }
 
        fileCount += countToAdd;
        artifactCountsForIngestJobs.put(ingestJobId, fileCount);
    }
 
    synchronized static void reportBlackboardPostCount(long ingestJobId) {
        Long refCount = refCounter.decrementAndGet(ingestJobId);
        if (refCount == 0) {
            Long filesCount = artifactCountsForIngestJobs.remove(ingestJobId);
            String msgText = String.format("Posted %d times to the blackboard", filesCount);
            IngestMessage message = IngestMessage.createMessage(
                    IngestMessage.MessageType.INFO,
                    NPSPyCaffeFactory.getModuleName(),
                    msgText);
            IngestServices.getInstance().postMessage(message);
        }
    }

    /**
     * Get a path to a temporary folder.
     *
     * @return
     */
    public static String getTempPath() {
        String tmpDir = Case.getCurrentCase().getTempDirectory() + File.separator
                + "EmailParser"; //NON-NLS
        File dir = new File(tmpDir);
        if (dir.exists() == false) {
            dir.mkdirs();
        }
        return tmpDir;
    }

    public static String getModuleOutputPath() {
        String outDir = Case.getCurrentCase().getModuleDirectory() + File.separator
                + NPSPyCaffeFactory.getModuleName();
        File dir = new File(outDir);
        if (dir.exists() == false) {
            dir.mkdirs();
        }
        return outDir;
    }

    public static String getRelModuleOutputPath() {
        return Case.getCurrentCase().getModuleOutputDirectoryRelativePath() + File.separator
                + NPSPyCaffeFactory.getModuleName();
    }

  
    IngestServices getServices() {
        return services;
    }

    
}
