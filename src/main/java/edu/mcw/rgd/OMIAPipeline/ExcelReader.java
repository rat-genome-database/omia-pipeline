package edu.mcw.rgd.OMIAPipeline;

import edu.mcw.rgd.process.Utils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by cdursun on 3/20/2017.
 */
public class ExcelReader {
    private final String LAST_PROCESSED_MATCHING_FILE_RECORD_FILE_NAME = "last_processed_matching_file";
    //name of RGD-OMIA matching file
    private String rgdOmiaMatchingFileName;
    // file name read from LAST_PROCESSED_MATCHING_FILE_RECORD_FILE_NAME
    private String lastProcessedRgdOmiaMatchingFileName;
    private String matchingFileNameTobeProcessed;
    private int columnNoForRgdAccId;
    private int columnNoForPheneId;
    private int columnNoForOmiaId;

    public boolean isRgdOmiaMatchingFileNew() {
        return isRgdOmiaMatchingFileNew;
    }

    private boolean isRgdOmiaMatchingFileNew;

    public void init() throws Exception{
        String newestFileName = OmiaFileDownloader.getTheNewestLocalFileName(OmiaFileDownloader.DATA_DIRECTORY, rgdOmiaMatchingFileName);

        if (newestFileName == null){
            System.out.println(rgdOmiaMatchingFileName + " is required under  " + new File (OmiaFileDownloader.DATA_DIRECTORY).getAbsolutePath() + " directory to run the pipeline!");
            System.exit(-1);
        }

        newestFileName = newestFileName.substring(newestFileName.indexOf(System.getProperty("file.separator")) + 1);
        String lastProcessedFileName = "";
        try{
            lastProcessedFileName = Utils.readFileAsString(OmiaFileDownloader.DATA_DIRECTORY + LAST_PROCESSED_MATCHING_FILE_RECORD_FILE_NAME);
        }catch (FileNotFoundException e){
            // FileNotFoundException is expected in the first run of the pipeline,
            // it will be created in Manager class by calling updateLastProcessedMatchingFileRecord() method
        }

        if (newestFileName.equals(lastProcessedFileName)) {
            setMatchingFileNameTobeProcessed(newestFileName);
            isRgdOmiaMatchingFileNew = false;
        }
        else {
            setMatchingFileNameTobeProcessed(newestFileName);
            setLastProcessedRgdOmiaMatchingFileName(newestFileName);
            isRgdOmiaMatchingFileNew = true;
        }
    }

    public String getLastProcessedRgdOmiaMatchingFileName() {
        return lastProcessedRgdOmiaMatchingFileName;
    }

    public void setLastProcessedRgdOmiaMatchingFileName(String lastProcessedRgdOmiaMatchingFileName) {
        this.lastProcessedRgdOmiaMatchingFileName = lastProcessedRgdOmiaMatchingFileName;
    }

    public void updateLastProcessedMatchingFileRecord() throws Exception{
        if (getLastProcessedRgdOmiaMatchingFileName() != null)
            Utils.writeStringToFile(getLastProcessedRgdOmiaMatchingFileName(), OmiaFileDownloader.DATA_DIRECTORY + LAST_PROCESSED_MATCHING_FILE_RECORD_FILE_NAME);
    }

    public Map<Integer, String> getGenePheneTermList(Map<Integer, String> omiaIdToRgdTermAccMap) throws Exception {

        Map<Integer, String> pairMap = new TreeMap<>();
        String termAcc;
        Integer pheneId, omiaId;

        FileInputStream excelFile = new FileInputStream(new File(OmiaFileDownloader.DATA_DIRECTORY + matchingFileNameTobeProcessed ));
        Workbook workbook = new XSSFWorkbook(excelFile);
        Sheet dataTypeSheet = workbook.getSheetAt(0);
        Iterator<Row> iterator = dataTypeSheet.iterator();

        // skip header row
        iterator.next();
        while (iterator.hasNext()) {

            Row currentRow = iterator.next();
            Iterator<Cell> cellIterator = currentRow.iterator();
            pheneId = null;
            omiaId = null;
            termAcc = null;
            while (cellIterator.hasNext()) {

                Cell currentCell = cellIterator.next();
                CellType cellType = currentCell.getCellType();
                int colIndex = currentCell.getColumnIndex();

                if (colIndex == getColumnNoForPheneId() && cellType == CellType.NUMERIC ) {
                    pheneId = (int) currentCell.getNumericCellValue();
                } else if (colIndex == getColumnNoForOmiaId() && cellType == CellType.NUMERIC) {
                    omiaId = (int)currentCell.getNumericCellValue();
                } else if (colIndex == getColumnNoForRgdAccId()) {
                    termAcc = currentCell.getStringCellValue();
                    if (termAcc != null){
                        if (termAcc.equals("")) {
                            termAcc = null;
                        }else{
                            termAcc = termAcc.replace(String.valueOf((char) 160), " ").trim();;
                        }
                    }
                }
            }

            if( pheneId!=null ) {
                pairMap.put(pheneId, termAcc);
            }
            if( omiaId!=null ) {
                omiaIdToRgdTermAccMap.put(omiaId, termAcc);
            }
        }

        excelFile.close();

        return pairMap;
    }

    public String getMatchingFileNameTobeProcessed() {
        return matchingFileNameTobeProcessed;
    }

    public void setMatchingFileNameTobeProcessed(String matchingFileNameTobeProcessed) {
        this.matchingFileNameTobeProcessed = matchingFileNameTobeProcessed;
    }

    public void setColumnNoForRgdAccId(int columnNoForRgdAccId) {
        this.columnNoForRgdAccId = columnNoForRgdAccId;
    }

    public int getColumnNoForRgdAccId() {
        return columnNoForRgdAccId;
    }

    public void setColumnNoForPheneId(int columnNoForPheneId) {
        this.columnNoForPheneId = columnNoForPheneId;
    }

    public int getColumnNoForPheneId() {
        return columnNoForPheneId;
    }
    public String getRgdOmiaMatchingFileName() {
        return rgdOmiaMatchingFileName;
    }

    public void setRgdOmiaMatchingFileName(String rgdOmiaMatchingFileName) {
        this.rgdOmiaMatchingFileName = rgdOmiaMatchingFileName;
    }

    public void setColumnNoForOmiaId(int columnNoForOmiaId) {
        this.columnNoForOmiaId = columnNoForOmiaId;
    }

    public int getColumnNoForOmiaId() {
        return columnNoForOmiaId;
    }
}
