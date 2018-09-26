package edu.mcw.rgd.OMIAPipeline;

/**
 * Created by cdursun on 3/15/2017.
 */
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Map;
import java.util.TreeMap;

public class TabDelimetedTextParser {
    private String causalMutationsFileName;
    private String oldNewNcbiGeneIdMappingFileName;
    private String textForNullNcbiGeneId;
    private Integer speciesKeyForDog;
    private byte columnNoForGeneSymbol;
    private byte columnNoForNcbiGeneId;
    private byte columnNoForOmiaId;
    private byte columnNoForTaxonomyId;
    private byte columnNoForPheneName;
    private byte columnNoForOldNcbiGeneId;
    private byte columnNoForNewNcbiGeneId;


    public void init(Integer speciesKeyForDog, String causalMutationsFileName){
        this.speciesKeyForDog = speciesKeyForDog;
        this.causalMutationsFileName = causalMutationsFileName;
    }

    public Map<String, OmiaRecord> getMutationsMap() throws Exception{
        BufferedReader buf = new BufferedReader(new FileReader(causalMutationsFileName));
        Map<String, OmiaRecord> genePheneMap = new TreeMap<String, OmiaRecord>();

        String lineJustFetched = null;
        String[] wordsArray;
        String taxonomyID = "", omiaID = "", ncbiGeneID = "", geneSymbol = "", pheneName = "";
        int i;

        buf.readLine(); //skip the header row
        while(true){
            lineJustFetched = buf.readLine();
            if(lineJustFetched == null){
                break;
            }else{
                wordsArray = lineJustFetched.split("\t");
                i = 0;
                for(String each : wordsArray){
                    if(!"".equals(each)){
                        if ( i == getColumnNoForGeneSymbol()){
                            geneSymbol = each;
                        }else if ( i == getColumnNoForNcbiGeneId()){
                            ncbiGeneID = each;
                        }else if ( i == getColumnNoForOmiaId()){
                            omiaID = each;
                        }else if ( i == getColumnNoForTaxonomyId()){
                            taxonomyID = each;
                        }else if ( i == getColumnNoForPheneName()){
                            pheneName = each;
                        }
                    }

                    i++;
                }
                if (Integer.parseInt(taxonomyID) == getSpeciesKeyForDog()){
                    if (ncbiGeneID.equals(getTextForNullNcbiGeneId()))
                        ncbiGeneID = null;
                    genePheneMap.put(omiaID, new OmiaRecord(geneSymbol, ncbiGeneID, omiaID, pheneName));
                }
            }
        }
        buf.close();
        return genePheneMap;
    }
    public Map<String, String> getOldNewNcbiIdPairMap() throws Exception{
        BufferedReader buf;
        try {
            buf = new BufferedReader(new FileReader(OmiaFileDownloader.DATA_DIRECTORY + oldNewNcbiGeneIdMappingFileName));
        }
        catch (FileNotFoundException e){
            //if there is no oldNewNcbiIdMappingFile then do nothing
            return null;
        }

        Map<String, String> oldNewNcbiIdPairMap = new TreeMap<String, String>();
        String lineJustFetched = null;
        String[] wordsArray;
        String oldNcbiId = "", newNcbiId = "";
        int i;

        buf.readLine(); //skip the header row
        while(true){
            lineJustFetched = buf.readLine();
            if(lineJustFetched == null){
                break;
            }else{
                wordsArray = lineJustFetched.split("\t");
                i = 0;
                for(String each : wordsArray){
                    if(!"".equals(each)){
                        if ( i == getColumnNoForOldNcbiGeneId()){
                            oldNcbiId = each;
                        }else if ( i == getColumnNoForNewNcbiGeneId()){
                            newNcbiId = each;
                        }
                    }

                    i++;
                }
                oldNewNcbiIdPairMap.put(oldNcbiId, newNcbiId);
            }
        }
        buf.close();
        return oldNewNcbiIdPairMap;
    }

    public void setTextForNullNcbiGeneId(String textForNullNcbiGeneId) {
        this.textForNullNcbiGeneId = textForNullNcbiGeneId;
    }

    public String getTextForNullNcbiGeneId() {
        return textForNullNcbiGeneId;
    }

    public Integer getSpeciesKeyForDog() {
        return speciesKeyForDog;
    }


    public void setColumnNoForGeneSymbol(byte columnNoForGeneSymbol) {
        this.columnNoForGeneSymbol = columnNoForGeneSymbol;
    }

    public byte getColumnNoForGeneSymbol() {
        return columnNoForGeneSymbol;
    }


    public void setColumnNoForOmiaId(byte columnNoForOmiaId) {
        this.columnNoForOmiaId = columnNoForOmiaId;
    }

    public byte getColumnNoForOmiaId() {
        return columnNoForOmiaId;
    }

    public void setColumnNoForTaxonomyId(byte columnNoForTaxonomyId) {
        this.columnNoForTaxonomyId = columnNoForTaxonomyId;
    }

    public byte getColumnNoForTaxonomyId() {
        return columnNoForTaxonomyId;
    }

    public void setColumnNoForPheneName(byte columnNoForPheneName) {
        this.columnNoForPheneName = columnNoForPheneName;
    }

    public byte getColumnNoForPheneName() {
        return columnNoForPheneName;
    }
    public byte getColumnNoForOldNcbiGeneId() {
        return columnNoForOldNcbiGeneId;
    }

    public void setColumnNoForOldNcbiGeneId(byte columnNoForOldNcbiGeneId) {
        this.columnNoForOldNcbiGeneId = columnNoForOldNcbiGeneId;
    }



    public byte getColumnNoForNewNcbiGeneId() {
        return columnNoForNewNcbiGeneId;
    }

    public void setColumnNoForNewNcbiGeneId(byte columnNoForNewNcbiGeneId) {
        this.columnNoForNewNcbiGeneId = columnNoForNewNcbiGeneId;
    }

    public String getOldNewNcbiGeneIdMappingFileName() {
        return oldNewNcbiGeneIdMappingFileName;
    }

    public void setOldNewNcbiGeneIdMappingFileName(String oldNewNcbiGeneIdMappingFileName) {
        this.oldNewNcbiGeneIdMappingFileName = oldNewNcbiGeneIdMappingFileName;
    }
    public byte getColumnNoForNcbiGeneId() {
        return columnNoForNcbiGeneId;
    }

    public void setColumnNoForNcbiGeneId(byte columnNoForNcbiGeneId) {
        this.columnNoForNcbiGeneId = columnNoForNcbiGeneId;
    }


    public class OmiaRecord {
        public OmiaRecord(String geneSymbol, String ncbiGeneId, String omiaId, String pheneName) {
            this.geneSymbol = geneSymbol;
            this.ncbiGeneId = ncbiGeneId;
            this.omiaId = Integer.valueOf(omiaId);
            this.pheneName = pheneName;
            this.speciesKeyForDog = getSpeciesKeyForDog();
        }

        public String getGeneSymbol() {
            return geneSymbol;
        }

        public void setGeneSymbol(String geneSymbol) {
            this.geneSymbol = geneSymbol;
        }

        public String getNcbiGeneId() {
            return ncbiGeneId;
        }
        public String getNcbiGeneIdOrSymbol() {
            return (ncbiGeneId != null ) ? ncbiGeneId : geneSymbol;
        }

        public void setNcbiGeneId(String ncbiGeneId) {
            this.ncbiGeneId = ncbiGeneId;
        }

        public Integer getOmiaId() {
            return omiaId;
        }

        public void setOmiaId(String Integer) {
            this.omiaId = omiaId;
        }

        public String getPheneName() {
            return pheneName;
        }

        public void setPheneName(String pheneName) {
            this.pheneName = pheneName;
        }

        public String toString (){
            return geneSymbol + "\t" + ncbiGeneId +  "\t" + omiaId + "\t"+ speciesKeyForDog + "\t" + pheneName;
        }

        private String geneSymbol;
        private String ncbiGeneId;
        private Integer omiaId;
        private String pheneName;

        private Integer speciesKeyForDog;
    }
}
