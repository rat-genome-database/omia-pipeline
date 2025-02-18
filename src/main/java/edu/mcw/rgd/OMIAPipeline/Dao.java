package edu.mcw.rgd.OMIAPipeline;

import edu.mcw.rgd.dao.impl.AnnotationDAO;
import edu.mcw.rgd.dao.impl.GeneDAO;
import edu.mcw.rgd.dao.impl.OntologyXDAO;
import edu.mcw.rgd.dao.impl.XdbIdDAO;
import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.RgdId;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.datamodel.ontologyx.TermSynonym;
import edu.mcw.rgd.process.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Created by cdursun on 3/29/2017.
 */
public class Dao {
    private AnnotationDAO annotationDao = new AnnotationDAO();
    private OntologyXDAO ontologyXdao = new OntologyXDAO();
    private GeneDAO geneDao = new GeneDAO();
    private XdbIdDAO xdbIdDao = new XdbIdDAO();

    private Date runDate;
    private String omiaDataSourceName;
    private String omiaEvidenceCode;
    private Integer refRgdId;
    private int timeCriteriaForObsoloteAnnotationDeletion;
    private Integer omiaUserKey;
    private boolean useGeneSymbolForAnnotation;

    Logger warningLogger = LogManager.getLogger("summary");
    Logger deletedLogger = LogManager.getLogger("deleted");
    Logger insertedLogger = LogManager.getLogger("inserted");
    Logger updatedLogger = LogManager.getLogger("updated");


    public void init(Date runDate){
        this.runDate = runDate;
    }

    /**
     * insert or update an annotation
     * @param a Annotation object
     * @return true if incoming annotation has been inserted; false if it has been updated
     */
    public boolean upsertAnnotation(Annotation a) throws Exception {
        int key = annotationDao.getAnnotationKey(a);
        if (key != 0) {
            a.setKey(key);
            a.setLastModifiedDate(runDate);
            annotationDao.updateAnnotation(a);
            updatedLogger.info(a.dump("|"));
            return false;
        }
        annotationDao.insertAnnotation(a);
        insertedLogger.info(a.dump("|"));
        return true;
    }

    public int deleteUnmodifiedAnnotations() throws Exception{
        List<Annotation> unmodifiedAnnotations = getUnmodifiedAnnotationsSince(getTimeCriteriaForObsoloteAnnotationDeletion());
        int numberOfDeletedAnnotations = unmodifiedAnnotations.size();

        for (Annotation a : unmodifiedAnnotations) {
            annotationDao.deleteAnnotation(a.getKey());
            deletedLogger.info(a.dump("|"));
        }

        return numberOfDeletedAnnotations;
    }

    public List<Annotation> getUnmodifiedAnnotationsSince(int min ) throws Exception {
        // calls getAnnotationsModifiedBeforeTimestamp method by subtracting @param min minutes from runDate
        return annotationDao.getAnnotationsModifiedBeforeTimestamp(getOmiaUserKey(), new Date(this.runDate.getTime() - (min * 1000 * 60) ));
    }

    public Annotation createNewAnnotation(String termAcc, TabDelimetedTextParser.OmiaRecord omiaRecord, String pubmedStr, int speciesTypeKey) throws Exception{

        Term term = ontologyXdao.getTermByAccId(termAcc);
        if( term==null ) {
            warningLogger.warn("WARNING: term with accession "+termAcc+" not found in database!");
            return null;
        }
        while( term.isObsolete() ) {
            List<TermSynonym> synonyms = ontologyXdao.getTermSynonyms(term.getAccId());
            for( TermSynonym tsyn: synonyms ) {
                if( Utils.stringsAreEqual(tsyn.getType(), "replaced_by") ) {
                    String newTermAcc = tsyn.getName();
                    term = ontologyXdao.getTermByAccId(newTermAcc);
                    if( term==null ) {
                        warningLogger.warn("WARNING: term with accession "+termAcc+" not found in database!");
                        return null;
                    }
                    warningLogger.warn("WARNING: term with accession "+termAcc+" replaced with "+newTermAcc);
                }
            }
        }

        Gene gene = getGeneByNcbiGeneIdOrGeneSymbol(omiaRecord.getNcbiGeneId(), omiaRecord.getGeneSymbol(), speciesTypeKey);
        if( gene==null ) {
            return null;
        }

        Annotation annotation = new Annotation();
        annotation.setTerm(term.getTerm());
        annotation.setAnnotatedObjectRgdId(gene.getRgdId());
        annotation.setRgdObjectKey(RgdId.OBJECT_KEY_GENES);
        annotation.setDataSrc(getOmiaDataSourceName());
        annotation.setObjectSymbol(omiaRecord.getGeneSymbol());
        annotation.setRefRgdId(getRefRgdId());
        annotation.setEvidence(getOmiaEvidenceCode());
        annotation.setAspect(ontologyXdao.getOntologyFromAccId(termAcc).getAspect());
        annotation.setObjectName(gene.getName());
        annotation.setTermAcc(term.getAccId());
        annotation.setCreatedBy(getOmiaUserKey());
        annotation.setLastModifiedBy(getOmiaUserKey());
        annotation.setXrefSource(pubmedStr);
        annotation.setNotes(omiaRecord.getPheneName());

        return annotation;
    }

    /**
     * get count of annotations given reference rgd id and species
     * @return count of annotations
     * @throws Exception on spring framework dao failure
     */
    public int getCountOfAnnotationsForSpecies(int speciesTypeKey) throws Exception {

        return annotationDao.getCountOfAnnotationsByReference(getRefRgdId(), speciesTypeKey);
    }

    /**
     * Return Gene from RGD by ncbiGeneId if ncbiGeneId is not null
     * if ncbiGeneId is null or if it cannot get Gene by ncbiGeneId and if it is configured to use GeneSymbol
     *  then it returns Gene using geneSymbol
     * @return Gene or null if not found
     * @throws Exception
     */
    public Gene getGeneByNcbiGeneIdOrGeneSymbol(String ncbiGeneId, String geneSymbol, int speciesTypeKey) throws Exception{
        List<Gene> geneList = null;
        boolean isGenePulledBySymbol = false;

        if (ncbiGeneId != null) {
            geneList = xdbIdDao.getGenesByXdbId(XdbId.XDB_KEY_ENTREZGENE, ncbiGeneId);
        }
        if ((ncbiGeneId == null || geneList == null || geneList.size() == 0) && useGeneSymbolForAnnotation) {
            geneList = geneDao.getActiveGenes(speciesTypeKey, geneSymbol);
            isGenePulledBySymbol = true;
        }

        if (geneList.size() > 1){
            warningLogger.info("Found " + geneList.size() + " RGD_IDs for NCBI Gene Id: " + ncbiGeneId + " - Gene Symbol:" + geneSymbol );
        }
        for( Gene gene: geneList) {
            if( (speciesTypeKey == gene.getSpeciesTypeKey()) && !gene.isVariant()) {
                if( isGenePulledBySymbol ){
                    warningLogger.info(geneSymbol + " annoted by using gene symbol!");
                }
                return gene;
            }
        }

        warningLogger.info("Not Found RGD_IDs for NCBI Gene Id: " + ncbiGeneId + " - Gene Symbol:" + geneSymbol );
        return null;
    }

    public String getOmiaDataSourceName() {
        return omiaDataSourceName;
    }

    public void setOmiaDataSourceName(String omiaDataSourceName) {
        this.omiaDataSourceName = omiaDataSourceName;
    }

    public String getOmiaEvidenceCode() {
        return omiaEvidenceCode;
    }

    public void setOmiaEvidenceCode(String omiaEvidenceCode) {
        this.omiaEvidenceCode = omiaEvidenceCode;
    }
    public Integer getOmiaUserKey() {
        return omiaUserKey;
    }

    public void setOmiaUserKey(Integer omiaUserKey) {
        this.omiaUserKey = omiaUserKey;
    }
    public Integer getRefRgdId() {
        return refRgdId;
    }

    public void setRefRgdId(Integer refRgdId) {
        this.refRgdId = refRgdId;
    }

    public int getTimeCriteriaForObsoloteAnnotationDeletion() {
        return timeCriteriaForObsoloteAnnotationDeletion;
    }

    public void setTimeCriteriaForObsoloteAnnotationDeletion(int timeCriteriaForObsoloteAnnotationDeletion) {
        this.timeCriteriaForObsoloteAnnotationDeletion = timeCriteriaForObsoloteAnnotationDeletion;
    }

    public void setUseGeneSymbolForAnnotation(boolean useGeneSymbolForAnnotation) {
        this.useGeneSymbolForAnnotation = useGeneSymbolForAnnotation;
    }

    public boolean getUseGeneSymbolForAnnotation() {
        return useGeneSymbolForAnnotation;
    }
}
