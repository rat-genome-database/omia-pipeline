package edu.mcw.rgd.OMIAPipeline;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import edu.mcw.rgd.process.Utils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * StAX-based XML parser. Streams the XML file without loading the entire DOM into memory.
 * Each readTable/readTableMultiKey call streams through the cleaned XML file independently.
 */
public class XmlParser {
    private OmiaFileDownloader fileDownloader;
    private String cleanedXmlFile;

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

    public void init(OmiaFileDownloader fileDownloader, Set<Integer> taxonIds) throws Exception {
        this.fileDownloader = fileDownloader;
        this.taxonIds = taxonIds;

        // pre-process: strip invalid XML characters and write a cleaned file
        cleanedXmlFile = "/tmp/omia2.xml";
        try (BufferedReader br = Utils.openReaderUtf8(this.fileDownloader.getLocalXmlFile());
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cleanedXmlFile), "UTF-8"))) {

            String line;
            while ((line = br.readLine()) != null) {
                writer.write(stripNonValidXMLCharacters(line));
                writer.write("\n");
            }
        }
    }

    public Multimap<Integer, Object> readTableMultiKey(String tableName, String keyField, String valueField, boolean isValueString) {
        Multimap<Integer, Object> pairMap = ArrayListMultimap.create();

        try {
            streamTable(tableName, keyField, valueField, isValueString, (key, value, speciesId) -> {
                if (key != null && value != null) {
                    pairMap.put(key, value);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Error reading table " + tableName, e);
        }

        return pairMap;
    }

    public Map<Integer, Object> readTable(String tableName, String keyField, String valueField, boolean isValueString) {
        Map<Integer, Object> pairMap = new TreeMap<>();

        try {
            streamTable(tableName, keyField, valueField, isValueString, (key, value, speciesId) -> {
                if (tableName.equals(getPheneGeneTableName())) {
                    if (value != null && !pairMap.containsKey(key)) {
                        pairMap.put(key, value);
                    }
                } else if (speciesId != null && taxonIds.contains(speciesId) && key != null && value != null) {
                    pairMap.put(key, value);
                } else if (speciesId == null && key != null && value != null) {
                    pairMap.put(key, value);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Error reading table " + tableName, e);
        }

        return pairMap;
    }

    @FunctionalInterface
    interface RowHandler {
        void handle(Integer key, Object value, Integer speciesId);
    }

    /**
     * Streams through the XML file using StAX, processing only the specified table.
     * For each row in the target table, extracts the key, value, and optional speciesId fields,
     * then delegates to the handler.
     */
    private void streamTable(String tableName, String keyField, String valueField, boolean isValueString, RowHandler handler) throws Exception {

        boolean trackSpeciesId = tableName.equals(getPheneTableName()) && keyField.equals(getOmiaIdFieldName());

        XMLInputFactory factory = XMLInputFactory.newInstance();
        try (InputStream is = new FileInputStream(cleanedXmlFile)) {
            XMLStreamReader reader = factory.createXMLStreamReader(is);

            boolean inTargetTable = false;
            boolean inRow = false;
            String currentFieldName = null;
            StringBuilder textBuf = new StringBuilder();

            Integer key = null;
            Object value = null;
            Integer speciesId = null;

            while (reader.hasNext()) {
                int event = reader.next();

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        String localName = reader.getLocalName();

                        if (localName.equals(tableElementName)) {
                            String nameAttr = reader.getAttributeValue(null, nameAttributeName);
                            inTargetTable = tableName.equals(nameAttr);
                        } else if (inTargetTable && localName.equals(rowElementName)) {
                            inRow = true;
                            key = null;
                            value = null;
                            speciesId = null;
                        } else if (inTargetTable && inRow && localName.equals(fieldElementName)) {
                            currentFieldName = reader.getAttributeValue(null, nameAttributeName);
                            textBuf.setLength(0);
                        }
                        break;

                    case XMLStreamConstants.CHARACTERS:
                        if (inTargetTable && inRow && currentFieldName != null) {
                            textBuf.append(reader.getText());
                        }
                        break;

                    case XMLStreamConstants.END_ELEMENT:
                        String endName = reader.getLocalName();

                        if (endName.equals(fieldElementName)) {
                            if (inTargetTable && inRow && currentFieldName != null) {
                                String text = textBuf.toString().trim();
                                if (!text.isEmpty()) {
                                    if (currentFieldName.equals(keyField)) {
                                        key = Integer.valueOf(text);
                                    } else if (currentFieldName.equals(valueField)) {
                                        if (isValueString) {
                                            value = text;
                                        } else {
                                            value = Integer.valueOf(text);
                                        }
                                    }
                                    if (trackSpeciesId && currentFieldName.equals(speciesIdFieldName)) {
                                        speciesId = Integer.valueOf(text);
                                    }
                                }
                            }
                            currentFieldName = null;
                        } else if (inTargetTable && endName.equals(rowElementName)) {
                            handler.handle(key, value, speciesId);
                            inRow = false;
                        } else if (endName.equals(tableElementName)) {
                            if (inTargetTable) {
                                reader.close();
                                return;
                            }
                        }
                        break;
                }
            }
            reader.close();
        }
    }

    public String stripNonValidXMLCharacters(String in) {
        if (in == null || in.isEmpty()) return "";
        StringBuilder out = new StringBuilder(in.length());
        for (int i = 0; i < in.length(); i++) {
            char current = in.charAt(i);
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
