package edu.mcw.rgd.OMIAPipeline;

/**
 * Created by cdursun on 4/5/2017.
 */
public class RgdIdNotFoundException extends Exception{
    public String getNcbiGeneId() {
        return ncbiGeneId;
    }

    private String ncbiGeneId;

    public String getGeneSymbol() {
        return geneSymbol;
    }

    private String geneSymbol;

    RgdIdNotFoundException(String ncbiGeneId, String geneSymbol) {
        this.ncbiGeneId = ncbiGeneId;
        this.geneSymbol = geneSymbol;
    }

    public String toString() {
        return "NCBI Gene Id : " + ncbiGeneId + "\t Gene Symbol : " + geneSymbol ;
    }
}
