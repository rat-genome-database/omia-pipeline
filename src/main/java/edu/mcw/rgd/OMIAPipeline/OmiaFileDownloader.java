package edu.mcw.rgd.OMIAPipeline;

import edu.mcw.rgd.process.FileDownloader;
import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Created by IntelliJ IDEA.
 * User: cdursun
 * Date: March 20, 2017
 */
public class OmiaFileDownloader extends FileDownloader {
    Logger logger = Logger.getLogger("summary");
    private static final String CAUSAL_MUTATION_FILE_NAME = "causal_mutations.txt";
    private static final String XML_FILE_NAME = "omia_xml.gz";
    public static final String DATA_DIRECTORY = "data" + System.getProperty("file.separator");

    private String localCausalMutationsFile  = DATA_DIRECTORY + CAUSAL_MUTATION_FILE_NAME;
    private String localXmlFile = DATA_DIRECTORY + XML_FILE_NAME;

    private boolean isCausalMutationFileNew = true;
    private boolean isXmlFileNew = true;

    private String externalCausalMutationFile;
    private String externalXmlFile;


    public void downloadAllIfNew() throws Exception {
        setDoNotUseHttpClient(true);//recently couldn't download using HttpClient method
        setCausalMutationFileNew(downloadIfNew(CAUSAL_MUTATION_FILE_NAME, localCausalMutationsFile, externalCausalMutationFile));
        setLocalCausalMutationsFile(getLocalFile());
        setXmlFileNew(downloadIfNew(XML_FILE_NAME, localXmlFile, externalXmlFile));
        setLocalXmlFile(getLocalFile());
    }


    public boolean downloadIfNew(String fileName, String localFile, String externalFile) throws Exception{

        downloadFile(localFile, externalFile);

        String newestFileName = getTheNewestLocalFileName(this.DATA_DIRECTORY, fileName);

        if (newestFileName != null) { // if there are files in the folder
            String newestFileContent = Utils.readFileAsString(newestFileName);

            if (newestFileName.equals(getLocalFile())) {
                return false; // then FileDownloader didn't download a file
            }

            String downloadedFileContent = Utils.readFileAsString(getLocalFile());

            if (Utils.generateMD5(newestFileContent).equals(Utils.generateMD5(downloadedFileContent))) {
                new File(getLocalFile()).delete();
                logger.info("File is not new " + getLocalFile() + " deleted...");
                //since it deletes the downloaded one, newest file name in the folder should be set as local file
                setLocalFile(newestFileName);
                return false;
            }
        }

        logger.info("Downloaded to file " + getLocalFile() );

        return true;
    }



    public void downloadFile(String localFile, String externalFile) throws Exception{
        setExternalFile(externalFile);
        setLocalFile(localFile);
        setLocalFile(downloadNew());
    }

    public int checkNumberOfLocalFiles(final String fileName){
        return listLocalFiles(this.DATA_DIRECTORY, fileName).length;
    }

    public static File[] listLocalFiles(String fileDirectory, final String fileName){
        return new File(fileDirectory).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.matches(fileName.substring(0, fileName.indexOf('.')) + "(.*)");
            }
        });
    }

    public static String getTheNewestLocalFileName(String fileDirectory, String fileName) throws Exception{
        File[] localFileList = listLocalFiles(fileDirectory, fileName);

        if (localFileList == null || localFileList.length == 0)
            return null;

        List<String> filePaths = new ArrayList<>(localFileList.length);
        for( File f: localFileList ) {
            filePaths.add(f.getPath());
        }
        Collections.sort(filePaths);

        // the newest file will be at the end of the list
        return filePaths.get(filePaths.size()-1);
    }

    public String getLocalXmlFile() {
        return localXmlFile;
    }

    public void setLocalXmlFile(String localXmlFile) {
        this.localXmlFile = localXmlFile;
    }

    public String getLocalCausalMutationsFile() {
        return localCausalMutationsFile;
    }

    public void setLocalCausalMutationsFile(String localCausalMutationsFile) {
        this.localCausalMutationsFile = localCausalMutationsFile;
    }

    public void setExternalCausalMutationFile(String externalCausalMutationFile) {
        this.externalCausalMutationFile = externalCausalMutationFile;
    }

    public String getExternalCausalMutationFile() {
        return externalCausalMutationFile;
    }

    public void setExternalXmlFile(String externalXmlFile) {
        this.externalXmlFile = externalXmlFile;
    }

    public String getExternalXmlFile() {
        return externalXmlFile;
    }

    public boolean isCausalMutationFileNew() {
        return isCausalMutationFileNew;
    }

    public void setCausalMutationFileNew(boolean causalMutationFileNew) {
        isCausalMutationFileNew = causalMutationFileNew;
    }

    public boolean isXmlFileNew() {
        return isXmlFileNew;
    }

    public void setXmlFileNew(boolean xmlFileNew) {
        isXmlFileNew = xmlFileNew;
    }

}
