package edu.mcw.rgd.OMIAPipeline;

/**
 * Created by cdursun on 3/15/2017.
 */
import edu.mcw.rgd.process.Utils;

import java.io.BufferedReader;
import java.util.*;

public class TabDelimetedTextParser {
    private String causalMutationsFileName;
    private String oldNewNcbiGeneIdMappingFileName;
    private String textForNullNcbiGeneId;
    private byte columnNoForGeneSymbol;
    private byte columnNoForNcbiGeneId;
    private byte columnNoForOmiaId;
    private byte columnNoForTaxonomyId;
    private byte columnNoForPheneName;
    private byte columnNoForOldNcbiGeneId;
    private byte columnNoForNewNcbiGeneId;
    private Set<Integer> taxonIds;

    public void init(Set<Integer> taxonIds, String causalMutationsFileName){
        this.causalMutationsFileName = causalMutationsFileName;
        this.taxonIds = taxonIds;
    }

    public Map<String, OmiaRecord> getMutationsMap() throws Exception{
        BufferedReader buf = Utils.openReader(causalMutationsFileName);
        Map<String, OmiaRecord> genePheneMap = new TreeMap<String, OmiaRecord>();

        String[] wordsArray;
        String omiaID = "", ncbiGeneID = "", geneSymbol = "", pheneName = "";
        int taxonId = 0, i;

        String line = buf.readLine(); //skip the header row

        while( (line=buf.readLine())!=null ) {
            wordsArray = line.split("\t");
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
                        taxonId = Integer.parseInt(each);
                    }else if ( i == getColumnNoForPheneName()){
                        pheneName = each;
                    }
                }

                i++;
            }

            if( taxonIds.contains(taxonId) ){
                if (ncbiGeneID.equals(getTextForNullNcbiGeneId()))
                    ncbiGeneID = null;
                genePheneMap.put(omiaID, new OmiaRecord(geneSymbol, ncbiGeneID, omiaID, pheneName, taxonId));
            }
        }
        buf.close();
        return genePheneMap;
    }

    public Map<String, String> getOldNewNcbiIdPairMap() throws Exception{
        BufferedReader buf = Utils.openReader(OmiaFileDownloader.DATA_DIRECTORY + oldNewNcbiGeneIdMappingFileName);

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
        public OmiaRecord(String geneSymbol, String ncbiGeneId, String omiaId, String pheneName, int taxonId) {
            this.geneSymbol = geneSymbol;
            this.ncbiGeneId = ncbiGeneId;
            this.omiaId = Integer.valueOf(omiaId);
            this.pheneName = pheneName;
            this.taxonId = taxonId;
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
            return geneSymbol + "\t" + ncbiGeneId +  "\t" + omiaId + "\t"+ taxonId + "\t" + pheneName;
        }

        private String geneSymbol;
        private String ncbiGeneId;
        private Integer omiaId;
        private String pheneName;
        int taxonId;
    }
}
