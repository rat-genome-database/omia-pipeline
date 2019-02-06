package edu.mcw.rgd.OMIAPipeline;

/**
 * Created by cdursun on 3/14/2017.
 */

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import edu.mcw.rgd.datamodel.SpeciesType;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class XmlParser {
    private Document xmlDoc;
    private OmiaFileDownloader fileDownloader;
    private String tableElementName;
    private String nameAttributeName;
    private String rowElementName;
    private String fieldElementName;
    private String omiaIdFieldName;
    private String speciesIdFieldName;
    private String pheneTableName;
    private String pheneIdFieldName;
    private String pheneGeneTableName;
    private String articlePheneTableName;
    private String speciesSpecificPheneIdFieldName;
    private String articlesTableName;
    private String articleIdFieldName;
    private String pubmedIdFieldName;
    private Set<Integer> taxonIds;

    public void init(OmiaFileDownloader fileDownloader, Set<Integer> taxonIds) throws Exception{
        this.fileDownloader = fileDownloader;
        this.taxonIds = taxonIds;

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
        NodeList tableDataList = xmlDoc.getElementsByTagName(getTableElementName());

        for (int i = 0; i < tableDataList.getLength(); i++) {
            Element tableNode = (Element) tableDataList.item(i);
            if (tableNode.getAttribute(getNameAttributeName()).equals(tableName)) {
                NodeList rows = tableNode.getElementsByTagName(getRowElementName());
                for (int j = 0; j < rows.getLength(); j++) {
                    Element row = (Element) rows.item(j);
                    NodeList fields = row.getElementsByTagName(getFieldElementName());
                    Integer key = null;
                    Object value = null;
                    for (int k = 0; k < fields.getLength(); k++) {
                        Element field = (Element) fields.item(k);
                        if (field.getAttribute(getNameAttributeName()).equals(keyField)) {
                            if (field.getFirstChild() != null)
                                key = Integer.valueOf(field.getTextContent());
                        } else if (field.getAttribute(getNameAttributeName()).equals(valueField)) {
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
        NodeList tableDataList = xmlDoc.getElementsByTagName(getTableElementName());

        for (int i = 0; i < tableDataList.getLength(); i++) {
            Element tableNode = (Element) tableDataList.item(i);
            if (tableNode.getAttribute(getNameAttributeName()).equals(tableName)) {
                NodeList rows = tableNode.getElementsByTagName(getRowElementName());
                for (int j = 0; j < rows.getLength(); j++) {
                    Element row = (Element) rows.item(j);
                    NodeList fields = row.getElementsByTagName(getFieldElementName());
                    Integer key = null;
                    Object value = null;
                    Integer speciesId = null;
                    for (int k = 0; k < fields.getLength(); k++) {
                        Element field = (Element) fields.item(k);
                        if (tableName.equals(getPheneTableName()) && keyField.equals(getOmiaIdFieldName()) && field.getAttribute(getNameAttributeName()).equals(getSpeciesIdFieldName()))
                            speciesId = Integer.valueOf(field.getTextContent());

                        if (field.getAttribute(getNameAttributeName()).equals(keyField)) {
                            if (field.getFirstChild() != null)
                                key = Integer.valueOf(field.getTextContent());
                        } else if (field.getAttribute(getNameAttributeName()).equals(valueField)) {
                            if (field.getFirstChild() != null) {
                                if (isValueString)
                                    value = field.getTextContent();
                                else
                                    value = Integer.valueOf(field.getTextContent());
                            }
                        }
                    }

                    if (tableName.equals(getPheneGeneTableName())){
                        if (value != null){
                            if (pairMap.get(key) == null){
                                pairMap.put(key, value);
                            }
                        }
                    }
                    else if (speciesId != null && taxonIds.contains(speciesId.intValue()) && key != null && value != null )
                        pairMap.put(key, value);
                    else if (speciesId == null && key != null && value != null)
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

    public String getTableElementName() {
        return tableElementName;
    }

    public void setTableElementName(String tableElementName) {
        this.tableElementName = tableElementName;
    }

    public String getNameAttributeName() {
        return nameAttributeName;
    }

    public void setNameAttributeName(String nameAttributeName) {
        this.nameAttributeName = nameAttributeName;
    }

    public String getRowElementName() {
        return rowElementName;
    }

    public void setRowElementName(String rowElementName) {
        this.rowElementName = rowElementName;
    }

    public String getFieldElementName() {
        return fieldElementName;
    }

    public void setFieldElementName(String fieldElementName) {
        this.fieldElementName = fieldElementName;
    }

    public String getOmiaIdFieldName() {
        return omiaIdFieldName;
    }

    public void setOmiaIdFieldName(String omiaIdFieldName) {
        this.omiaIdFieldName = omiaIdFieldName;
    }

    public String getSpeciesIdFieldName() {
        return speciesIdFieldName;
    }

    public void setSpeciesIdFieldName(String speciesIdFieldName) {
        this.speciesIdFieldName = speciesIdFieldName;
    }

    public String getPheneTableName() {
        return pheneTableName;
    }

    public void setPheneTableName(String pheneTableName) {
        this.pheneTableName = pheneTableName;
    }

    public String getPheneIdFieldName() {
        return pheneIdFieldName;
    }

    public void setPheneIdFieldName(String pheneIdFieldName) {
        this.pheneIdFieldName = pheneIdFieldName;
    }

    public String getPheneGeneTableName() {
        return pheneGeneTableName;
    }

    public void setPheneGeneTableName(String pheneGeneTableName) {
        this.pheneGeneTableName = pheneGeneTableName;
    }

    public String getArticlePheneTableName() {
        return articlePheneTableName;
    }

    public void setArticlePheneTableName(String articlePheneTableName) {
        this.articlePheneTableName = articlePheneTableName;
    }

    public String getSpeciesSpecificPheneIdFieldName() {
        return speciesSpecificPheneIdFieldName;
    }

    public void setSpeciesSpecificPheneIdFieldName(String speciesSpecificPheneIdFieldName) {
        this.speciesSpecificPheneIdFieldName = speciesSpecificPheneIdFieldName;
    }

    public String getArticlesTableName() {
        return articlesTableName;
    }

    public void setArticlesTableName(String articlesTableName) {
        this.articlesTableName = articlesTableName;
    }

    public String getArticleIdFieldName() {
        return articleIdFieldName;
    }

    public void setArticleIdFieldName(String articleIdFieldName) {
        this.articleIdFieldName = articleIdFieldName;
    }

    public String getPubmedIdFieldName() {
        return pubmedIdFieldName;
    }

    public void setPubmedIdFieldName(String pubmedIdFieldName) {
        this.pubmedIdFieldName = pubmedIdFieldName;
    }
}
