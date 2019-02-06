package edu.mcw.rgd.OMIAPipeline;

import edu.mcw.rgd.dao.impl.AnnotationDAO;
import edu.mcw.rgd.dao.impl.GeneDAO;
import edu.mcw.rgd.dao.impl.OntologyXDAO;
import edu.mcw.rgd.dao.impl.XdbIdDAO;
import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.RgdId;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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

    Log warningLogger = LogFactory.getLog("warning");
    Log deletedLogger = LogFactory.getLog("deleted");
    Log insertedLogger = LogFactory.getLog("inserted");
    Log updatedLogger = LogFactory.getLog("updated");


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
            updatedLogger.info(a.dump("-"));
            return false;
        }
        annotationDao.insertAnnotation(a);
        insertedLogger.info(a.dump("-"));
        return true;
    }

    public int deleteUnmodifiedAnnotations() throws Exception{
        List<Annotation> unmodifiedAnnotations = getUnmodifiedAnnotationsSince(getTimeCriteriaForObsoloteAnnotationDeletion());
        int numberOfDeletedAnnotations = unmodifiedAnnotations.size();

        for (Annotation a : unmodifiedAnnotations) {
            annotationDao.deleteAnnotation(a.getKey());
            deletedLogger.info(a.dump("-"));
        }

        return numberOfDeletedAnnotations;
    }

    public List<Annotation> getUnmodifiedAnnotationsSince(int min ) throws Exception {
        // calls getAnnotationsModifiedBeforeTimestamp method by subtracting @param min minutes from runDate
        List<Annotation> unmodifiedAnnots = annotationDao.getAnnotationsModifiedBeforeTimestamp(getOmiaUserKey(), new Date(this.runDate.getTime() - (min * 1000 * 60) ));;
        // also to avoid removing annotations created by DoAnnotator pipeline, we filter them out
        Iterator<Annotation> it = unmodifiedAnnots.iterator();
        while( it.hasNext() ) {
            Annotation annot = it.next();
            if( annot.getAspect().equals("I") ) { // 'I' - aspect for DO ontology; terms created by DoAnnot pipeline
                it.remove();
            }
        }
        return unmodifiedAnnots;
    }

    public Annotation createNewAnnotation(String termAcc, TabDelimetedTextParser.OmiaRecord omiaRecord, String pubmedStr, Collection<Integer> speciesTypeKeys) throws Exception{
        Annotation annotation = new Annotation();
        Gene gene = getGeneByNcbiGeneIdOrGeneSymbol(omiaRecord.getNcbiGeneId(), omiaRecord.getGeneSymbol(), speciesTypeKeys);
        annotation.setTerm(ontologyXdao.getTermByAccId(termAcc).getTerm());
        annotation.setAnnotatedObjectRgdId(gene.getRgdId());
        annotation.setRgdObjectKey(RgdId.OBJECT_KEY_GENES);
        annotation.setDataSrc(getOmiaDataSourceName());
        annotation.setObjectSymbol(omiaRecord.getGeneSymbol());
        annotation.setRefRgdId(getRefRgdId());
        annotation.setEvidence(getOmiaEvidenceCode());
        annotation.setAspect(ontologyXdao.getOntologyFromAccId(termAcc).getAspect());
        annotation.setObjectName(gene.getName());
        annotation.setTermAcc(termAcc);
        annotation.setCreatedBy(getOmiaUserKey());
        annotation.setLastModifiedBy(getOmiaUserKey());
        annotation.setXrefSource(pubmedStr);

        return annotation;
    }

    /**
     * get count of annotations given reference rgd id and species
     * @return count of annotations
     * @throws Exception on spring framework dao failure
     */
    public int getCountOfAnnotationsForSpecies(int speciesTypeKey) throws Exception {

        String query = "SELECT COUNT(*) FROM full_annot a,rgd_ids r "+
                "WHERE ref_rgd_id=? AND annotated_object_rgd_id=rgd_id AND r.object_status='ACTIVE' AND species_type_key=?";
        return ontologyXdao.getCount(query, getRefRgdId(), speciesTypeKey);
    }

    /**
     * Return Gene from RGD by ncbiGeneId if ncbiGeneId is not null
     * if ncbiGeneId is null or it can not get Gene by ncbiGeneId and if it is configured to use GeneSymbol
     *  then it returns Gene using geneSymbol
     * if not found at the end throws RgdIdNotFoundException
     * @param ncbiGeneId
     * @return
     * @throws Exception
     */
    public Gene getGeneByNcbiGeneIdOrGeneSymbol(String ncbiGeneId, String geneSymbol, Collection<Integer> speciesTypeKeys) throws Exception{
        List<Gene> geneList = null;
        boolean isGenePulledBySymbol = false;

        if (ncbiGeneId != null) {
            geneList = xdbIdDao.getGenesByXdbId(XdbId.XDB_KEY_ENTREZGENE, ncbiGeneId);
        }
        if ((ncbiGeneId == null || geneList == null || geneList.size() == 0) && useGeneSymbolForAnnotation) {
            geneList = new ArrayList<>();
            for( int speciesTypeKey: speciesTypeKeys ) {
                geneList.addAll(geneDao.getActiveGenes(speciesTypeKey, geneSymbol));
            }
            isGenePulledBySymbol = true;
        }

        if (geneList.size() > 1){
            warningLogger.info("Found " + geneList.size() + " RGD_IDs for NCBI Gene Id: " + ncbiGeneId + " - Gene Symbol:" + geneSymbol );
        }
        for( Gene gene: geneList) {
            if( speciesTypeKeys.contains(gene.getSpeciesTypeKey()) && !gene.isVariant()) {
                if( isGenePulledBySymbol ){
                    warningLogger.info(geneSymbol + " annoted by using gene symbol!");
                }
                return gene;
            }
        }

        // if not found using ncbiGeneId or geneSymbol then, try to get it with geneSymbol
        throw new RgdIdNotFoundException(ncbiGeneId, geneSymbol);
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
