package edu.mcw.rgd.OMIAPipeline;

/**
 * Created by cdursun on 3/14/2017.
 */

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

public class XmlParser {
    private Document xmlDoc;
    private OmiaFileDownloader fileDownloader;
    private Integer speciesKeyForDog;
    private String tableElementNameInXML;
    private String nameAttributeNameInXML;
    private String rowElementNameInXML;
    private String fieldElementNameInXML;
    private String omiaIdFieldNameInXML;
    private String speciesIdFieldNameInXML;
    private String pheneTableNameInXML;
    private String pheneIdFieldNameInXML;
    private String omiaGroupTableNameInXML;
    private String omiaGroupFieldNameInXML;
    private String pheneGeneTableNameInXml;
    private String geneIdNameInXml;
    private String genesTableNameInXml;
    private String geneSymbolNameInXML;
    private String articlePheneTableNameInXml;
    private String articlesTableNameInXml;
    private String articleIdNameInXml;
    private String pubmedIdNameInXml;


    public void init(OmiaFileDownloader fileDownloader, Integer speciesKeyForDog) throws Exception{
        this.fileDownloader = fileDownloader;
        this.speciesKeyForDog = speciesKeyForDog;

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

        xmlDoc = dBuilder.parse(new ByteArrayInputStream(stripNonValidXMLCharacters(IOUtils.toString(openXmlFile())).getBytes()));
        xmlDoc.getDocumentElement().normalize();
        //System.out.println("xmlDoc loaded, Root element :" + xmlDoc.getDocumentElement().getNodeName());
    }

    public static void printMap(Map map){

        Iterator it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            System.out.println("Key = " + pair.getKey() + "  Values = " + pair.getValue());
            it.remove(); // avoids a ConcurrentModificationException
        }
    }

    public static void printMultimap(Multimap<Integer, Object> map){
        // get all the set of keys
        Set keys = map.keySet();
        // iterate through the key set and display key and values
        for (Object key : keys) {
            System.out.print("Key = " + key);
            System.out.println(" Values = " + map.get((Integer)key) + "");
        }
    }

    public Multimap<Integer, Object> readTableMultiKey(String tableName, String keyField, String valueField, boolean isValueString) {
        Multimap<Integer, Object> pairMap = ArrayListMultimap.create();
        NodeList tableDataList = xmlDoc.getElementsByTagName(getTableElementNameInXML());

        for (int i = 0; i < tableDataList.getLength(); i++) {
            Element tableNode = (Element) tableDataList.item(i);
            if (tableNode.getAttribute(getNameAttributeNameInXML()).equals(tableName)) {
                NodeList rows = tableNode.getElementsByTagName(getRowElementNameInXML());
                for (int j = 0; j < rows.getLength(); j++) {
                    Element row = (Element) rows.item(j);
                    NodeList fields = row.getElementsByTagName(getFieldElementNameInXML());
                    Integer key = null;
                    Object value = null;
                    Integer speciesIdName = null;
                    for (int k = 0; k < fields.getLength(); k++) {
                        Element field = (Element) fields.item(k);
                        if (field.getAttribute(getNameAttributeNameInXML()).equals(keyField)) {
                            if (field.getFirstChild() != null)
                                key = Integer.valueOf(field.getTextContent());
                        } else if (field.getAttribute(getNameAttributeNameInXML()).equals(valueField)) {
                            if (field.getFirstChild() != null) {
                                if (isValueString)
                                    value = field.getTextContent();
                                else
                                    value = Integer.valueOf(field.getTextContent());
                            }
                        }
                    }

                    if (key !=null && value != null)
                        pairMap.put(key, value);
                }
            }
        }

        return pairMap;
    }
    public Map<Integer, Object> readTable(String tableName, String keyField, String valueField, boolean isValueString) {
        Map<Integer, Object> pairMap = new TreeMap<>();
        NodeList tableDataList = xmlDoc.getElementsByTagName(getTableElementNameInXML());

        for (int i = 0; i < tableDataList.getLength(); i++) {
            Element tableNode = (Element) tableDataList.item(i);
            if (tableNode.getAttribute(getNameAttributeNameInXML()).equals(tableName)) {
                NodeList rows = tableNode.getElementsByTagName(getRowElementNameInXML());
                for (int j = 0; j < rows.getLength(); j++) {
                    Element row = (Element) rows.item(j);
                    NodeList fields = row.getElementsByTagName(getFieldElementNameInXML());
                    Integer key = null;
                    Object value = null;
                    String characterised = null;
                    Integer speciesId = null;
                    for (int k = 0; k < fields.getLength(); k++) {
                        Element field = (Element) fields.item(k);
                        if (tableName.equals(getPheneTableNameInXML()) && keyField.equals(getOmiaIdFieldNameInXML()) && field.getAttribute(getNameAttributeNameInXML()).equals(getSpeciesIdFieldNameInXML()))
                            speciesId = Integer.valueOf(field.getTextContent());

                        if (field.getAttribute(getNameAttributeNameInXML()).equals(keyField)) {
                            if (field.getFirstChild() != null)
                                key = Integer.valueOf(field.getTextContent());
                        } else if (field.getAttribute(getNameAttributeNameInXML()).equals(valueField)) {
                            if (field.getFirstChild() != null) {
                                if (isValueString)
                                    value = field.getTextContent();
                                else
                                    value = Integer.valueOf(field.getTextContent());
                            }
                        }
                    }

                    if (tableName.equals(getPheneGeneTableNameInXml())){
                        if (value != null){
                            if (pairMap.get(key) == null){
                                pairMap.put(key, value);
                            }
                        }
                    }
                    else if (speciesId != null && speciesId.intValue() == getSpeciesKeyForDog() && key !=null && value != null )
                        pairMap.put(key, value);
                    else if (speciesId == null && key !=null && value != null)
                        pairMap.put(key, value);
                }
            }
        }

        return pairMap;
    }


    BufferedReader openXmlFile() throws IOException {
        //check if the file exist
        String localFileName = this.fileDownloader.getLocalXmlFile();
        File file=new File(localFileName);
        if (!file.exists()) {
            //  processLog.error("The file "+ localFileName + " doesn't exist");
            return null;
        }

        //processLog.info("Parsing file: " + localFileName);

        return new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(localFileName))));
    }

    /**
     * This method ensures that the output String has only
     * valid XML unicode characters as specified by the
     * XML 1.0 standard. For reference, please see
     * <a href="http://www.w3.org/TR/2000/REC-xml-20001006#NT-Char">the
     * standard</a>. This method will return an empty
     * String if the input is null or empty.
     *
     * @param in The String whose non-valid characters we want to remove.
     * @return The in String, stripped of non-valid characters.
     */
    public String stripNonValidXMLCharacters(String in) {
        StringBuffer out = new StringBuffer(); // Used to hold the output.
        char current; // Used to reference the current character.

        if (in == null || ("".equals(in))) return ""; // vacancy test.
        for (int i = 0; i < in.length(); i++) {
            current = in.charAt(i); // NOTE: No IndexOutOfBoundsException caught here; it should not happen.
            if ((current == 0x9) ||
                    (current == 0xA) ||
                    (current == 0xD) ||
                    ((current >= 0x20) && (current <= 0xD7FF)) ||
                    ((current >= 0xE000) && (current <= 0xFFFD)) ||
                    ((current >= 0x10000) && (current <= 0x10FFFF)))
                out.append(current);
        }
        return out.toString();
    }
    public void setTableElementNameInXML(String tableElementNameInXML) {
        this.tableElementNameInXML = tableElementNameInXML;
    }

    public String getTableElementNameInXML() {
        return tableElementNameInXML;
    }

    public void setNameAttributeNameInXML(String nameAttributeNameInXML) {
        this.nameAttributeNameInXML = nameAttributeNameInXML;
    }

    public String getNameAttributeNameInXML() {
        return nameAttributeNameInXML;
    }

    public void setRowElementNameInXML(String rowElementNameInXML) {
        this.rowElementNameInXML = rowElementNameInXML;
    }

    public String getRowElementNameInXML() {
        return rowElementNameInXML;
    }

    public void setFieldElementNameInXML(String fieldElementNameInXML) {
        this.fieldElementNameInXML = fieldElementNameInXML;
    }

    public String getFieldElementNameInXML() {
        return fieldElementNameInXML;
    }

    public void setOmiaIdFieldNameInXML(String omiaIdFieldNameInXML) {
        this.omiaIdFieldNameInXML = omiaIdFieldNameInXML;
    }

    public String getOmiaIdFieldNameInXML() {
        return omiaIdFieldNameInXML;
    }

    public void setSpeciesIdFieldNameInXML(String speciesIdFieldNameInXML) {
        this.speciesIdFieldNameInXML = speciesIdFieldNameInXML;
    }

    public String getSpeciesIdFieldNameInXML() {
        return speciesIdFieldNameInXML;
    }

    public void setPheneTableNameInXML(String pheneTableNameInXML) {
        this.pheneTableNameInXML = pheneTableNameInXML;
    }

    public String getPheneTableNameInXML() {
        return pheneTableNameInXML;
    }
    public Integer getSpeciesKeyForDog() {
        return speciesKeyForDog;
    }

    public void setSpeciesKeyForDog(Integer speciesKeyForDog) {
        this.speciesKeyForDog = speciesKeyForDog;
    }
    public void setPheneIdFieldNameInXML(String pheneIdFieldNameInXML) {
        this.pheneIdFieldNameInXML = pheneIdFieldNameInXML;
    }

    public String getPheneIdFieldNameInXML() {
        return pheneIdFieldNameInXML;
    }

    public void setOmiaGroupTableNameInXML(String omiaGroupTableNameInXML) {
        this.omiaGroupTableNameInXML = omiaGroupTableNameInXML;
    }

    public String getOmiaGroupTableNameInXML() {
        return omiaGroupTableNameInXML;
    }

    public void setOmiaGroupFieldNameInXML(String omiaGroupFieldNameInXML) {
        this.omiaGroupFieldNameInXML = omiaGroupFieldNameInXML;
    }

    public String getOmiaGroupFieldNameInXML() {
        return omiaGroupFieldNameInXML;
    }

    public void setPheneGeneTableNameInXml(String pheneGeneTableNameInXml) {
        this.pheneGeneTableNameInXml = pheneGeneTableNameInXml;
    }

    public String getPheneGeneTableNameInXml() {
        return pheneGeneTableNameInXml;
    }

    public void setGeneIdFieldNameInXML(String geneIdFieldNameInXML) {
        this.geneIdNameInXml = geneIdFieldNameInXML;
    }

    public String getGeneIdFieldNameInXML() {
        return geneIdNameInXml;
    }

    public void setGenesTableNameInXml(String genesTableNameInXml) {
        this.genesTableNameInXml = genesTableNameInXml;
    }

    public String getGenesTableNameInXml() {
        return genesTableNameInXml;
    }

    public void setGeneSymbolFieldNameInXML(String geneSymbolFieldNameInXML) {
        this.geneSymbolNameInXML = geneSymbolFieldNameInXML;
    }

    public String getGeneSymbolFieldNameInXML() {
        return geneSymbolNameInXML;
    }

    public void setArticlePheneTableNameInXml(String articlePheneTableNameInXml) {
        this.articlePheneTableNameInXml = articlePheneTableNameInXml;
    }

    public String getArticlePheneTableNameInXml() {
        return articlePheneTableNameInXml;
    }

    public void setArticlesTableNameInXml(String articlesTableNameInXml) {
        this.articlesTableNameInXml = articlesTableNameInXml;
    }

    public String getArticlesTableNameInXml() {
        return articlesTableNameInXml;
    }

    public void setArticleIdFieldNameInXML(String articleIdFieldNameInXML) {
        this.articleIdNameInXml = articleIdFieldNameInXML;
    }

    public String getArticleIdFieldNameInXML() {
        return articleIdNameInXml;
    }

    public void setPubmedIdFieldNameInXML(String pubmedIdFieldNameInXML) {
        this.pubmedIdNameInXml = pubmedIdFieldNameInXML;
    }

    public String getPubmedIdFieldNameInXML() {
        return pubmedIdNameInXml;
    }

}
