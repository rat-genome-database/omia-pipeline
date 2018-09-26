package edu.mcw.rgd.OMIAPipeline;

import com.google.common.collect.Multimap;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.process.Utils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.util.*;

/**
 * Created by cdursun on 3/14/2017.
 */
public class Manager {
    private String version;
    private final static Log loggerSummary;
    private final static Log loggerWarning;
    private final static Log loggerNotFoundNcbiGenes;
    private final static Log loggerMismatchedPheneNames;
    private final static Log loggerExcessPubmeds;

    private OmiaFileDownloader omiaFileDownloader;
    private TabDelimetedTextParser tabDelimetedTextParser;
    private ExcelReader excelReader;
    private XmlParser xmlParser;
    private Dao dao;


    static {
        loggerSummary = LogFactory.getLog("summary");
        loggerWarning = LogFactory.getLog("warning");
        loggerNotFoundNcbiGenes = LogFactory.getLog("not_found_omia_genes_in_rgd");
        loggerMismatchedPheneNames =  LogFactory.getLog("mismatched_phenes");
        loggerExcessPubmeds =   LogFactory.getLog("excess_pubmeds");
    }

    private int maxNumberOfPubmedIds;
    private Integer speciesKeyForDog;
    private boolean stopProcessingIfNoNewFiles;


    public static void main(String[] args) throws Exception {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));

        Manager manager= (Manager) bf.getBean("main");
        manager.init(bf);

        Date time0 = Calendar.getInstance().getTime();

        try {
            manager.run(time0);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

        loggerSummary.info("========== Elapsed time " + Utils.formatElapsedTime(time0.getTime(), System.currentTimeMillis()) + ". ==========");

    }

    /** LOGIC:
     * <ol>
    * <li> Download causal_mutations.txt file
    * <li> Download OMIA XML file
    * <li> Check the RGD_OMIA_matching.xlsx filename with the last processed matching file name
    * <li> If they are not modified since the last update then delete the downloaded files,  make a note and stop the execution,
    * <li> If number of XML files or txt files exceed the maxNumberOfFiles then delete the oldest file
    * <li> if at least one of them has been changed then start processing
    * <li> read 3 files into Maps
    * <li> iterate on causal_mutations Map
    * <li> check if pheneName from RGD_OMIA_matching.xlsx file has corresponding RGD_ACC_ID
    * <li> if there is not then add to a unmatchedOMIAPheneRGDlist and skip this record
    * <li> if there is then
    * <li> check DB if there is an Annotation for causal_mutation record
    * <li> if there is not then any create an Annotation
    * <li> if there is then add to the to be updated Annotation list
    * <li> After finishing the records
    * <li> insert the new Annotation list
    * <li> update the Update list
    * <li> delete the unmodified Annotations
    * <li> if unmatchedOMIAPheneRGDlist is not empty then add these records to the RGD_OMIA_matching.xlsx and sends this to the curators
    * </ol>
    */
    public void run(Date runDate) throws Exception {

        omiaFileDownloader.downloadAllIfNew();
        excelReader.init();

        //Check whether the downloaded files and matching excel file have new content or not,
        // and if stopProcessingIfNoNewFiles at AppConfigure.xml is set to True or False
        if (stopProcessingIfNoNewFiles && !omiaFileDownloader.isCausalMutationFileNew() && !omiaFileDownloader.isXmlFileNew() && !excelReader.isRgdOmiaMatchingFileNew()){
            loggerSummary.info("No new files, stopped processing!");
            System.exit(0);
        }
        else {
            loggerSummary.info("Started processing...");

            Map <Phenotype, String> pheneRgdTermAccMap = excelReader.getGenePheneTermList();
            //Map <Integer, String> pheneIdPheneNameMap = extractPheneIdPheneNamePairs(pheneRgdTermAccMap);

            //read causal_mutations file
            tabDelimetedTextParser.init(getSpeciesKeyForDog(), omiaFileDownloader.getLocalCausalMutationsFile());
            Map <String, TabDelimetedTextParser.OmiaRecord> genePheneMap = tabDelimetedTextParser.getMutationsMap();
            loggerSummary.info("Total number of Causal Mutations from OMIA : " + genePheneMap.size());

            //read the old-new ncbi_gene_ids from the mapping file
            //if this file doesn't exist then just skips it
            Map <String, String> oldNewNcbiIdPairMap = tabDelimetedTextParser.getOldNewNcbiIdPairMap();

            //read the OMIA xml file
            xmlParser.init(omiaFileDownloader, getSpeciesKeyForDog());
            Map<Integer, Object> omiaPheneMap = xmlParser.readTable(xmlParser.getPheneTableNameInXML(),
                    xmlParser.getOmiaIdFieldNameInXML(), xmlParser.getPheneIdFieldNameInXML(), false);

            Multimap<Integer, Object> articlePheneMap = xmlParser.readTableMultiKey(xmlParser.getArticlePheneTableNameInXml(),
                    xmlParser.getPheneIdFieldNameInXML(), xmlParser.getArticleIdFieldNameInXML(), false);

            Map<Integer, Object> articlesMap = xmlParser.readTable(xmlParser.getArticlesTableNameInXml(),
                    xmlParser.getArticleIdFieldNameInXML(), xmlParser.getPubmedIdFieldNameInXML(), false);

            dao.init(runDate);

            List<Annotation> tobeInsertAnnnotationList = new ArrayList<Annotation>();
            List<Annotation> tobeUpdatedAnnnotationList = new ArrayList<Annotation>();

            Integer pheneId = null;

            String pubmedStr = "";

            int numberOfGenesHaveExcessPubmed = 0,
                    numberOfMissingGeneIdsInCausalMutationsFile = 0,
                    numberOfMismatchedPheneNames = 0,
                    numberOfNotFoundNCBIGenesInRGD = 0;

            Collection<Object> articleIdList;

            //for each phene-gene record in the causal_mutations.txt file
            for (String key: genePheneMap.keySet()) {
                Integer omiaId = Integer.valueOf(key);
                TabDelimetedTextParser.OmiaRecord omiaRecord = genePheneMap.get(key);


                //Some of NcbiGeneIds coming from OMIA is old
                //We are reading a mapping file and if there is a new NcbiGeneId for the ncbigeneid then we set the new value
                if (oldNewNcbiIdPairMap != null) {
                    try {
                        String newNcbiGeneId = oldNewNcbiIdPairMap.get(omiaRecord.getNcbiGeneIdOrSymbol());
                        if (newNcbiGeneId != null) {
                            omiaRecord.setNcbiGeneId(newNcbiGeneId);
                        }
                    } catch (NullPointerException e) {
                        // we have only a few gene_id mappings
                        // for others we will have nullpointerexception
                        // do nothing for those genes
                    }
                } else if (omiaRecord.getNcbiGeneId() == null) {
                    numberOfMissingGeneIdsInCausalMutationsFile++;
                }

                pheneId = (Integer) omiaPheneMap.get(omiaId);
                // sometimes XML file doesn't contain any record for omia_id in causal_mutations_file
                // in this case pheneId becomes null, skip those records
                if (pheneId == null) {
                    loggerWarning.info("Omia record with id " + omiaId + " from causal_mutations_file can't be found in XML file.");
                    continue;
                }
                articleIdList = articlePheneMap.get(pheneId);

                Object[] result = createPubmedString(articleIdList, articlesMap);
                pubmedStr = (String) result[0];
                Integer numberOfPubmed = (Integer) result[1];

                String termAcc = pheneRgdTermAccMap.get(new Phenotype(pheneId));

                if (termAcc != null) {
                    try {
                        Annotation annotation = dao.createNewAnnotation(termAcc, omiaRecord, pubmedStr);
                        if (annotation.getKey() == null)
                            tobeInsertAnnnotationList.add(annotation);
                        else
                            tobeUpdatedAnnnotationList.add(annotation);

                        if (numberOfPubmed > getMaxNumberOfPubmedIds()) {
                            numberOfGenesHaveExcessPubmed++;
                            loggerExcessPubmeds.info("Gene : " + omiaRecord.getNcbiGeneId() + " - Phene : " + " has " + numberOfPubmed + " Pubmed Ids");
                        }
                    } catch (RgdIdNotFoundException re) {
                        loggerNotFoundNcbiGenes.info(re);
                        numberOfNotFoundNCBIGenesInRGD++;
                    }
                }
                // if the phene hasn't been mapped yet or phene name is different from the mapping file
                else if (termAcc == null/* || !omiaRecord.getPheneName().equals(pheneIdPheneNameMap.get(pheneId) )*/) {
                    numberOfMismatchedPheneNames++;
                    loggerMismatchedPheneNames.info(pheneId + "\t" + omiaRecord.getPheneName() + "\t" + omiaId);
                }

            }
            loggerSummary.info("Total number of not found OMIA Phene Terms at RGD DB : " + numberOfMismatchedPheneNames);
            loggerSummary.info("Total number of not found OMIA NCBI Gene Ids at RGD DB : " + numberOfNotFoundNCBIGenesInRGD);
            loggerSummary.info("Total number of Missing NCBI Gene Ids in Causal Mutations File: " + numberOfMissingGeneIdsInCausalMutationsFile);

            dao.insertAnnotations(tobeInsertAnnnotationList);
            loggerSummary.info("Inserted OMIA annotations : " + tobeInsertAnnnotationList.size());

            dao.updateAnnotations(tobeUpdatedAnnnotationList);
            loggerSummary.info("Updated OMIA annotations : " + tobeUpdatedAnnnotationList.size());


            loggerSummary.info("Deleted obsolete OMIA annotations : " + dao.deleteUnmodifiedAnnotations());
            loggerSummary.info("Number of OMIA Phene terms having more than max \"" + getMaxNumberOfPubmedIds() + "\" Pubmed Ids : " + numberOfGenesHaveExcessPubmed);

            excelReader.updateLastProcessedMatchingFileRecord();
        }
    }
    public Map<Integer, String> extractPheneIdPheneNamePairs(Map <Phenotype, String> pheneRgdTermAccMap){
        Map <Integer, String> pheneIdPheneNameMap = new TreeMap<>();
        for (Map.Entry<Phenotype, String> e : pheneRgdTermAccMap.entrySet()) {
            Phenotype phene = e.getKey();
            pheneIdPheneNameMap.put(phene.getId(), phene.getName());
        }
        return pheneIdPheneNameMap;
    }

    public Object[] createPubmedString(Collection<Object> articleIdList, Map<Integer, Object> articlesMap){
        String pubmedStr = "";
        int numberOfPubmed = 0;
        for (Object anArticleIdList : articleIdList) {
            Integer pubmedId = (Integer) articlesMap.get(anArticleIdList);
            if (pubmedId != null) {
                if (numberOfPubmed < getMaxNumberOfPubmedIds()) {
                    if (!pubmedStr.equals(""))
                        pubmedStr += "|PMID:" + pubmedId;
                    else
                        pubmedStr += "PMID:" + pubmedId;
                }
                numberOfPubmed++;
            }
        }
        Object[] result = new Object[2];
        result[0] = pubmedStr;
        result[1] = numberOfPubmed;
        return result;
    }
    void init(DefaultListableBeanFactory bf) {
        loggerSummary.info(getVersion());
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public OmiaFileDownloader getOmiaFileDownloader() {
        return omiaFileDownloader;
    }

    public void setOmiaFileDownloader(OmiaFileDownloader omiaFileDownloader) {
        this.omiaFileDownloader = omiaFileDownloader;
    }

    public void setMaxNumberOfPubmedIds(int maxNumberOfPubmedIds) {
        this.maxNumberOfPubmedIds = maxNumberOfPubmedIds;
    }

    public int getMaxNumberOfPubmedIds() {
        return maxNumberOfPubmedIds;
    }
    public XmlParser getXmlParser() {
        return xmlParser;
    }

    public void setXmlParser(XmlParser xmlParser) {
        this.xmlParser = xmlParser;
    }
    public Dao getDao() {
        return dao;
    }

    public void setDao(Dao dao) {
        this.dao = dao;
    }

    public void setSpeciesKeyForDog(Integer speciesKeyForDog) {
        this.speciesKeyForDog = speciesKeyForDog;
    }

    public Integer getSpeciesKeyForDog() {
        return speciesKeyForDog;
    }

    public void setTabDelimetedTextParser(TabDelimetedTextParser tabDelimetedTextParser) {
        this.tabDelimetedTextParser = tabDelimetedTextParser;
    }

    public TabDelimetedTextParser getTabDelimetedTextParser() {
        return tabDelimetedTextParser;
    }

    public void setExcelReader(ExcelReader excelReader) {
        this.excelReader = excelReader;
    }

    public ExcelReader getExcelReader() {
        return excelReader;
    }

    public void setStopProcessingIfNoNewFiles(boolean stopProcessingIfNoNewFiles) {
        this.stopProcessingIfNoNewFiles = stopProcessingIfNoNewFiles;
    }

    public boolean getStopProcessingIfNoNewFiles() {
        return stopProcessingIfNoNewFiles;
    }
}
