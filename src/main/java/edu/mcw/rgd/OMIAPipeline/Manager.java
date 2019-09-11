package edu.mcw.rgd.OMIAPipeline;

import com.google.common.collect.Multimap;
import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.util.*;

/**
 * Created by cdursun on 3/14/2017.
 */
public class Manager {
    private String version;

    Logger loggerSummary = Logger.getLogger("summary");
    Logger loggerNotFoundNcbiGenes = Logger.getLogger("not_found_omia_genes_in_rgd");
    Logger loggerMismatchedPheneNames =  Logger.getLogger("mismatched_phenes");
    Logger loggerExcessPubmeds = Logger.getLogger("excess_pubmeds");

    private OmiaFileDownloader omiaFileDownloader;
    private TabDelimetedTextParser tabDelimetedTextParser;
    private ExcelReader excelReader;
    private XmlParser xmlParser;
    private Dao dao;

    private int maxNumberOfPubmedIds;
    private boolean stopProcessingIfNoNewFiles;
    private List<String> speciesProcessed;

    public static void main(String[] args) throws Exception {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));

        Manager manager= (Manager) bf.getBean("main");
        manager.init(bf);

        Date time0 = Calendar.getInstance().getTime();

        try {
            manager.run(time0);
        } catch (Exception e) {
            Utils.printStackTrace(e, manager.loggerSummary);
            throw e;
        }

        manager.loggerSummary.info("========== Elapsed time " + Utils.formatElapsedTime(time0.getTime(), System.currentTimeMillis()) + ". ==========");
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
        if( getStopProcessingIfNoNewFiles() && !omiaFileDownloader.isCausalMutationFileNew() && !omiaFileDownloader.isXmlFileNew() && !excelReader.isRgdOmiaMatchingFileNew()){
            loggerSummary.info("No new files, stopped processing!");
            System.exit(0);
        }

        loggerSummary.info("Started processing...");

        Map<Integer, Integer> taxonIds = new HashMap<>(); // taxon-to-speciesTypeKey map
        for( String speciesName: speciesProcessed ) {
            int speciesTypeKey = SpeciesType.parse(speciesName);
            taxonIds.put(SpeciesType.getTaxonomicId(speciesTypeKey), speciesTypeKey);

            loggerSummary.info("  OMIA annotations for "+speciesName+": "+dao.getCountOfAnnotationsForSpecies(speciesTypeKey));
        }

        Map <Phenotype, String> pheneRgdTermAccMap = excelReader.getGenePheneTermList();

        //read causal_mutations file
        tabDelimetedTextParser.init(taxonIds.keySet(), omiaFileDownloader.getLocalCausalMutationsFile());
        Map <String, TabDelimetedTextParser.OmiaRecord> genePheneMap = tabDelimetedTextParser.getMutationsMap();
        loggerSummary.info("Total number of Causal Mutations from OMIA : " + genePheneMap.size());

        //read the old-new ncbi_gene_ids from the mapping file
        //if this file doesn't exist then just skips it
        Map <String, String> oldNewNcbiIdPairMap = tabDelimetedTextParser.getOldNewNcbiIdPairMap();

        //read the OMIA xml file
        xmlParser.init(omiaFileDownloader, taxonIds.keySet());
        Map<Integer, Object> omiaPheneMap = xmlParser.readTable(xmlParser.getPheneTableName(),
                xmlParser.getOmiaIdFieldName(), xmlParser.getPheneIdFieldName(), false);

        Multimap<Integer, Object> articlePheneMap = xmlParser.readTableMultiKey(xmlParser.getArticlePheneTableName(),
                xmlParser.getSpeciesSpecificPheneIdFieldName(), xmlParser.getArticleIdFieldName(), false);

        Map<Integer, Object> articlesMap = xmlParser.readTable(xmlParser.getArticlesTableName(),
                xmlParser.getArticleIdFieldName(), xmlParser.getPubmedIdFieldName(), false);

        dao.init(runDate);

        List<Annotation> incomingAnnnotations = new ArrayList<Annotation>();

        Integer pheneId;
        String pubmedStr;

        int numberOfGenesHaveExcessPubmed = 0,
                numberOfMissingGeneIdsInCausalMutationsFile = 0,
                numberOfMismatchedPheneNames = 0,
                numberOfNotFoundNCBIGenesInRGD = 0;

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
                loggerSummary.warn("WARNING: Omia record with id " + omiaId + " from causal_mutations_file can't be found in XML file!");
                continue;
            }
            Collection<Object> articleIdList = articlePheneMap.get(pheneId);

            Object[] result = createPubmedString(articleIdList, articlesMap);
            pubmedStr = (String) result[0];
            Integer numberOfPubmed = (Integer) result[1];

            String termAcc = pheneRgdTermAccMap.get(new Phenotype(pheneId));

            if (termAcc != null) {
                try {
                    Annotation annotation = dao.createNewAnnotation(termAcc, omiaRecord, pubmedStr, taxonIds.get(omiaRecord.taxonId));
                    if( annotation!=null ) {
                        incomingAnnnotations.add(annotation);

                        if (numberOfPubmed > getMaxNumberOfPubmedIds()) {
                            numberOfGenesHaveExcessPubmed++;
                            loggerExcessPubmeds.info("Gene : " + omiaRecord.getNcbiGeneId() + " - Phene : " + " has " + numberOfPubmed + " Pubmed Ids");
                        }
                    }
                } catch (RgdIdNotFoundException re) {
                    loggerNotFoundNcbiGenes.info(re);
                    numberOfNotFoundNCBIGenesInRGD++;
                }
            }
            // if the phene hasn't been mapped yet
            else {
                numberOfMismatchedPheneNames++;
                loggerMismatchedPheneNames.info(pheneId + "\t" + omiaRecord.getPheneName() + "\t" + omiaId);
            }

        }
        loggerSummary.info("Total number of not found OMIA Phene Terms at RGD DB : " + numberOfMismatchedPheneNames);
        loggerSummary.info("Total number of not found OMIA NCBI Gene Ids at RGD DB : " + numberOfNotFoundNCBIGenesInRGD);
        loggerSummary.info("Total number of Missing NCBI Gene Ids in Causal Mutations File: " + numberOfMissingGeneIdsInCausalMutationsFile);

        processAnnotations(incomingAnnnotations);


        loggerSummary.info("Deleted obsolete OMIA annotations : " + dao.deleteUnmodifiedAnnotations());
        loggerSummary.info("Number of OMIA Phene terms having more than max \"" + getMaxNumberOfPubmedIds() + "\" Pubmed Ids : " + numberOfGenesHaveExcessPubmed);

        excelReader.updateLastProcessedMatchingFileRecord();


        for( int speciesTypeKey: taxonIds.values() ) {
            String speciesName = SpeciesType.getCommonName(speciesTypeKey);
            loggerSummary.info("  OMIA annotations for "+speciesName+": "+dao.getCountOfAnnotationsForSpecies(speciesTypeKey));
        }
    }

    void processAnnotations(List<Annotation> annotations) throws Exception {

        int insertedAnnotations = 0;
        int updatedAnnotations = 0;

        for( Annotation a: annotations ) {
            boolean annotInserted = dao.upsertAnnotation(a);
            if( annotInserted ) {
                insertedAnnotations++;
            } else {
                updatedAnnotations++;
            }
        }

        loggerSummary.info("Inserted OMIA annotations : " + insertedAnnotations);
        loggerSummary.info("Updated OMIA annotations : " + updatedAnnotations);
    }

    public Object[] createPubmedString(Collection<Object> articleIdList, Map<Integer, Object> articlesMap){

        String pubmedStr = "";
        int numberOfPubmed = 0;
        for (Object articleId: articleIdList) {
            Integer pubmedId = (Integer) articlesMap.get(articleId);
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

    public void setSpeciesProcessed(List<String> list) {
        speciesProcessed = list;
    }

    public List<String> getSpeciesProcessed() {
        return speciesProcessed;
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
