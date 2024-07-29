package com.igot.cios.service.impl;

import com.bazaarvoice.jolt.Chainr;
import com.bazaarvoice.jolt.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cios.constant.CiosConstants;
import com.igot.cios.constant.ContentSource;
import com.igot.cios.entity.CornellContentEntity;
import com.igot.cios.entity.UpgradContentEntity;
import com.igot.cios.exception.CiosContentException;
import com.igot.cios.kafka.KafkaProducer;
import com.igot.cios.repository.CornellContentRepository;
import com.igot.cios.repository.UpgradContentRepository;
import com.igot.cios.service.CiosContentService;
import com.igot.cios.util.PayloadValidation;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;



@Service
@Slf4j
public class CiosContentServiceImpl implements CiosContentService {
    @Autowired
    private CornellContentRepository contentRepository;
    @Autowired
    private UpgradContentRepository upgradContentRepository;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    KafkaProducer kafkaProducer;
    @Autowired
    PayloadValidation payloadValidation;
    @Value("${spring.kafka.cornell.topic.name}")
    private String topic;
    @Value("${cornell.progress.transformation.source-to-target.spec.path}")
    private String progressPathOfTragetFile;

    @Override
    public void loadContentFromExcel(MultipartFile file,String providerName) {
        log.info("CiosContentServiceImpl::loadJobsFromExcel");
        List<Map<String, String>> processedData = processExcelFile(file);
        log.info("No.of processedData from excel: " + processedData.size());
        JsonNode jsonData = objectMapper.valueToTree(processedData);
        ContentSource contentSource = ContentSource.fromProviderName(providerName);
        if (contentSource == null) {
            log.warn("Unknown provider name: " + providerName);
            return; // Exit if the provider name is not recognized
        }
        jsonData.forEach(
                eachContentData -> {
                    saveOrUpdateContentFromProvider(eachContentData,contentSource);
                });
    }

    @Override
    public List<CornellContentEntity> fetchAllContentFromDb() {
        log.info("CiosContentServiceImpl::fetchAllContentFromDb");
        try {
            return contentRepository.findAll();
        } catch (DataAccessException dae) {
            log.error("Database access error while fetching content", dae.getMessage());
            throw new CiosContentException(CiosConstants.ERROR, "Database access error: " + dae.getMessage());
        } catch (Exception e) {
            throw new CiosContentException(CiosConstants.ERROR, e.getMessage());
        }
    }

    @Override
    public void loadContentProgressFromExcel(MultipartFile file) {
        List<Map<String, String>> processedData = processExcelFile(file);
        log.info("No.of processedData from excel: " + processedData.size());
        JsonNode jsonData = objectMapper.valueToTree(processedData);
        jsonData.forEach(
                eachContentData -> {
                    callCornellEnrollmentAPI(eachContentData);
                });
    }

    private void saveOrUpdateContentFromProvider(JsonNode rawContentData,ContentSource source) {
        log.info("CiosContentServiceImpl::saveOrUpdateContentFromProvider");
        JsonNode transformData = transformData(rawContentData, source.getFilePath());
        payloadValidation.validatePayload(CiosConstants.CORNELL_DATA_PAYLOAD_VALIDATION_FILE, transformData);
        String externalId = transformData.path("content").path("externalId").asText();
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        switch (source) {
            case CORNELL:
                saveOrUpdateCornellContent(externalId, transformData, rawContentData, currentTime);
                break;
            case UPGRAD:
                saveOrUpdateUpgradContent(externalId, transformData, rawContentData, currentTime);
                break;
        }

    }

    private void saveOrUpdateUpgradContent(String externalId, JsonNode transformData, JsonNode rawContentData, Timestamp currentTime) {
        Optional<UpgradContentEntity> optExternalContent = upgradContentRepository.findByExternalId(externalId);
        if (optExternalContent.isPresent()) {
            UpgradContentEntity externalContent = optExternalContent.get();
            externalContent.setExternalId(externalId);
            externalContent.setCiosData(transformData);
            externalContent.setIsActive(externalContent.getIsActive());
            externalContent.setCreatedDate(externalContent.getCreatedDate());
            externalContent.setUpdatedDate(currentTime);
            externalContent.setSourceData(rawContentData);
            upgradContentRepository.save(externalContent);
        } else {
            UpgradContentEntity externalContent = new UpgradContentEntity();
            externalContent.setExternalId(externalId);
            externalContent.setCiosData(transformData);
            externalContent.setIsActive(false);
            externalContent.setCreatedDate(currentTime);
            externalContent.setUpdatedDate(currentTime);
            externalContent.setSourceData(rawContentData);
            upgradContentRepository.save(externalContent);
        }
    }

    private void saveOrUpdateCornellContent(String externalId, JsonNode transformData, JsonNode rawContentData, Timestamp currentTime) {
        Optional<CornellContentEntity> optExternalContent = contentRepository.findByExternalId(externalId);
        if (optExternalContent.isPresent()) {
            CornellContentEntity externalContent = optExternalContent.get();
            externalContent.setExternalId(externalId);
            externalContent.setCiosData(transformData);
            externalContent.setIsActive(externalContent.getIsActive());
            externalContent.setCreatedDate(externalContent.getCreatedDate());
            externalContent.setUpdatedDate(currentTime);
            externalContent.setSourceData(rawContentData);
            contentRepository.save(externalContent);
        } else {
            CornellContentEntity externalContent = new CornellContentEntity();
            externalContent.setExternalId(externalId);
            externalContent.setCiosData(transformData);
            externalContent.setIsActive(false);
            externalContent.setCreatedDate(currentTime);
            externalContent.setUpdatedDate(currentTime);
            externalContent.setSourceData(rawContentData);
            contentRepository.save(externalContent);
        }
    }
    private void callCornellEnrollmentAPI(JsonNode rawContentData) {
        log.info("CiosContentServiceImpl::saveOrUpdateContentFromProvider");
        JsonNode transformData = transformData(rawContentData, progressPathOfTragetFile);
        payloadValidation.validatePayload(CiosConstants.CORNELL_PROGRESS_DATA_VALIDATION_FILE, transformData);
        kafkaProducer.push(topic,transformData);
        log.info("callCornellEnrollmentAPI {} ",transformData.asText());
    }

    private JsonNode transformData(Object sourceObject, String destinationPath) {
        log.info("CiosContentServiceImpl::transformData");
        String inputJson;
        try {
            inputJson = objectMapper.writeValueAsString(sourceObject);
        } catch (JsonProcessingException e) {
            return null;
        }
        List<Object> specJson = JsonUtils.classpathToList(destinationPath);
        Chainr chainr = Chainr.fromSpec(specJson);
        Object transformedOutput = chainr.transform(JsonUtils.jsonToObject(inputJson));
        return objectMapper.convertValue(transformedOutput, JsonNode.class);
    }
    private List<Map<String, String>> processExcelFile(MultipartFile incomingFile) {
        log.info("CiosContentServiceImpl::processExcelFile");
        try {
            return validateFileAndProcessRows(incomingFile);
        } catch (Exception e) {
            log.error("Error occurred during file processing: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    private List<Map<String, String>> validateFileAndProcessRows(MultipartFile file) {
        log.info("CiosContentServiceImpl::validateFileAndProcessRows");
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            return processSheetAndSendMessage(sheet);
        } catch (IOException e) {
            log.error("Error while processing Excel file: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    private List<Map<String, String>> processSheetAndSendMessage(Sheet sheet) {
        log.info("CiosContentServiceImpl::processSheetAndSendMessage");
        DataFormatter formatter = new DataFormatter();
        Row headerRow = sheet.getRow(0);
        List<Map<String, String>> dataRows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row dataRow = sheet.getRow(rowIndex);

            if (dataRow == null) {
                break; // No more data rows, exit the loop
            }

            boolean allBlank = true;
            Map<String, String> rowData = new HashMap<>();

            for (int colIndex = 0; colIndex < headerRow.getLastCellNum(); colIndex++) {
                Cell headerCell = headerRow.getCell(colIndex);
                Cell valueCell = dataRow.getCell(colIndex);

                if (headerCell != null && headerCell.getCellType() != CellType.BLANK) {
                    String excelHeader =
                            formatter.formatCellValue(headerCell).replaceAll("[\\n*]", "").trim();
                    String cellValue = "";

                    if (valueCell != null && valueCell.getCellType() != CellType.BLANK) {
                        if (valueCell.getCellType() == CellType.NUMERIC
                                && DateUtil.isCellDateFormatted(valueCell)) {
                            // Handle date format
                            Date date = valueCell.getDateCellValue();
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                            cellValue = dateFormat.format(date);
                        } else {
                            cellValue = formatter.formatCellValue(valueCell).replace("\n", ",").trim();
                        }
                        allBlank = false;
                    }

                    rowData.put(excelHeader, cellValue);
                }
            }
            if (allBlank) {
                break; // If all cells are blank in the current row, stop processing
            }

            dataRows.add(rowData);
        }
        log.info("Number of Data Rows Processed: " + dataRows.size());
        return dataRows;
    }
}
