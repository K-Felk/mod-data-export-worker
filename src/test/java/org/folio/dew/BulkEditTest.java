package org.folio.dew;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.folio.dew.domain.dto.EntityType.ITEM;
import static org.folio.dew.domain.dto.EntityType.USER;
import static org.folio.dew.domain.dto.ExportType.BULK_EDIT_IDENTIFIERS;
import static org.folio.dew.domain.dto.ExportType.BULK_EDIT_UPDATE;
import static org.folio.dew.domain.dto.IdentifierType.BARCODE;
import static org.folio.dew.domain.dto.IdentifierType.HOLDINGS_RECORD_ID;
import static org.folio.dew.domain.dto.JobParameterNames.OUTPUT_FILES_IN_STORAGE;
import static org.folio.dew.domain.dto.JobParameterNames.UPDATED_FILE_NAME;
import static org.folio.dew.utils.Constants.TOTAL_CSV_LINES;
import static org.folio.dew.utils.CsvHelper.countLines;
import static org.folio.dew.utils.Constants.ENTITY_TYPE;
import static org.folio.dew.utils.Constants.EXPORT_TYPE;
import static org.folio.dew.utils.Constants.FILE_NAME;
import static org.folio.dew.utils.Constants.IDENTIFIER_TYPE;
import static org.folio.dew.utils.Constants.ROLLBACK_FILE;
import static org.springframework.batch.test.AssertFile.assertFileEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.folio.dew.config.kafka.KafkaService;
import org.folio.dew.domain.dto.EntityType;
import org.folio.dew.domain.dto.ExportType;
import org.folio.dew.domain.dto.IdentifierType;
import org.folio.dew.domain.dto.JobParameterNames;
import org.folio.dew.service.BulkEditProcessingErrorsService;
import org.folio.dew.utils.Constants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.FileSystemResource;
import lombok.SneakyThrows;

@ExtendWith(MockitoExtension.class)
class BulkEditTest extends BaseBatchTest {

  private static final String BARCODES_CSV = "src/test/resources/upload/barcodes.csv";
  private static final String BARCODES_FOR_PROGRESS_CSV = "src/test/resources/upload/barcodes_for_progress.csv";
  private static final String ITEM_BARCODES_CSV = "src/test/resources/upload/item_barcodes.csv";
  private static final String ITEM_BARCODES_DOUBLE_QOUTES_CSV = "src/test/resources/upload/item_barcodes_double_qoutes.csv";
  private static final String ITEM_HOLDINGS_CSV = "src/test/resources/upload/item_holdings.csv";
  private static final String USER_RECORD_CSV = "src/test/resources/upload/bulk_edit_user_record.csv";
  private static final String ITEM_RECORD_CSV = "src/test/resources/upload/bulk_edit_item_record.csv";
  private static final String USER_RECORD_CSV_NOT_FOUND = "src/test/resources/upload/bulk_edit_user_record_not_found.csv";
  private static final String ITEM_RECORD_CSV_NOT_FOUND = "src/test/resources/upload/bulk_edit_item_record_not_found.csv";
  private static final String USER_RECORD_CSV_BAD_CONTENT = "src/test/resources/upload/bulk_edit_user_record_bad_content.csv";
  private static final String USER_RECORD_CSV_BAD_CUSTOM_FIELD = "src/test/resources/upload/bulk_edit_user_record_bad_custom_field.csv";
  private static final String USER_RECORD_CSV_EMPTY_PATRON_GROUP = "src/test/resources/upload/bulk_edit_user_record_empty_patron_group.csv";
  private static final String ITEM_RECORD_CSV_BAD_CALL_NUMBER_TYPE = "src/test/resources/upload/bulk_edit_item_record_bad_call_number_type.csv";
  private static final String ITEM_RECORD_CSV_BAD_DAMAGED_STATUS = "src/test/resources/upload/bulk_edit_item_record_bad_damaged_status.csv";
  private static final String ITEM_RECORD_CSV_BAD_LOAN_TYPE = "src/test/resources/upload/bulk_edit_item_record_bad_loan_type.csv";
  private static final String ITEM_RECORD_CSV_BAD_LOCATION = "src/test/resources/upload/bulk_edit_item_record_bad_location.csv";
  private static final String ITEM_RECORD_CSV_BAD_NOTE_TYPE = "src/test/resources/upload/bulk_edit_item_record_bad_note_type.csv";
  private static final String ITEM_RECORD_CSV_BAD_RELATIONSHIP = "src/test/resources/upload/bulk_edit_item_record_bad_relationship.csv";
  private static final String ITEM_RECORD_CSV_BAD_SERVICE_POINT = "src/test/resources/upload/bulk_edit_item_record_bad_service_point.csv";
  private static final String ITEM_RECORD_CSV_INVALID_NOTES = "src/test/resources/upload/bulk_edit_item_record_invalid_notes.csv";
  private static final String ITEM_RECORD_CSV_INVALID_CIRCULATION_NOTES = "src/test/resources/upload/bulk_edit_item_record_invalid_circulation_notes.csv";
  private static final String ITEM_RECORD_IN_APP_UPDATED = "src/test/resources/upload/bulk_edit_item_record_in_app_updated.csv";
  private static final String ITEM_RECORD_IN_APP_UPDATED_COPY = "storage/bulk_edit_item_record_in_app_updated.csv";
  private static final String USER_RECORD_ROLLBACK_CSV = "test-directory/bulk_edit_rollback.csv";
  private static final String BARCODES_SOME_NOT_FOUND = "src/test/resources/upload/barcodesSomeNotFound.csv";
  private static final String ITEM_BARCODES_SOME_NOT_FOUND = "src/test/resources/upload/item_barcodes_some_not_found.csv";
  private static final String USERS_QUERY_FILE_PATH = "src/test/resources/upload/users_by_group.cql";
  private static final String ITEMS_QUERY_FILE_PATH = "src/test/resources/upload/items_by_barcode.cql";
  private static final String QUERY_NO_GROUP_FILE_PATH = "src/test/resources/upload/active_no_group.cql";
  private static final String EXPECTED_BULK_EDIT_USER_OUTPUT = "src/test/resources/output/bulk_edit_user_identifiers_output.csv";
  private static final String EXPECTED_BULK_EDIT_ITEM_OUTPUT = "src/test/resources/output/bulk_edit_item_identifiers_output.csv";
  private static final String EXPECTED_BULK_EDIT_ITEM_OUTPUT_ESCAPED = "src/test/resources/output/bulk_edit_item_identifiers_output_escaped.csv";
  private static final String EXPECTED_NO_GROUP_OUTPUT = "src/test/resources/output/bulk_edit_no_group_output.csv";
  private static final String EXPECTED_ITEMS_QUERY_OUTPUT = "src/test/resources/output/bulk_edit_item_query_output.csv";
  private final static String EXPECTED_BULK_EDIT_OUTPUT_SOME_NOT_FOUND = "src/test/resources/output/bulk_edit_user_identifiers_output_some_not_found.csv";
  private final static String EXPECTED_BULK_EDIT_ITEM_OUTPUT_SOME_NOT_FOUND = "src/test/resources/output/bulk_edit_item_identifiers_output_some_not_found.csv";
  private final static String EXPECTED_BULK_EDIT_OUTPUT_ERRORS = "src/test/resources/output/bulk_edit_user_identifiers_errors_output.csv";
  private final static String EXPECTED_BULK_EDIT_ITEM_OUTPUT_ERRORS = "src/test/resources/output/bulk_edit_item_identifiers_errors_output.csv";
  private final static String EXPECTED_BULK_EDIT_ITEM_IDENTIFIERS_HOLDINGS_ERRORS_OUTPUT = "src/test/resources/output/bulk_edit_item_identifiers_holdings_errors_output.csv";

  @Autowired
  private Job bulkEditProcessUserIdentifiersJob;
  @Autowired
  private Job bulkEditProcessItemIdentifiersJob;
  @Autowired
  private Job bulkEditUserCqlJob;
  @Autowired
  private Job bulkEditItemCqlJob;
  @Autowired
  private Job bulkEditUpdateUserRecordsJob;
  @Autowired
  private Job bulkEditUpdateItemRecordsJob;
  @Autowired
  private Job bulkEditRollBackJob;
  @Autowired
  private BulkEditProcessingErrorsService bulkEditProcessingErrorsService;

  @MockBean
  private KafkaService kafkaService;

  @Test
  @DisplayName("Run bulk-edit (user identifiers) successfully")
  void uploadUserIdentifiersJobTest() throws Exception {

    JobLauncherTestUtils testLauncher = createTestLauncher(bulkEditProcessUserIdentifiersJob);

    final JobParameters jobParameters = prepareJobParameters(BULK_EDIT_IDENTIFIERS, USER, BARCODE, BARCODES_CSV, true);
    JobExecution jobExecution = testLauncher.launchJob(jobParameters);

    verifyFileOutput(jobExecution, EXPECTED_BULK_EDIT_USER_OUTPUT);

    assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

    // check if caching works
    wireMockServer.verify(1, getRequestedFor(urlEqualTo("/groups/3684a786-6671-4268-8ed0-9db82ebca60b")));
  }

  @Test
  @DisplayName("Update retrieval progress (user identifiers) successfully")
  void shouldUpdateProgressUponUserIdentifiersJob() throws Exception {

    JobLauncherTestUtils testLauncher = createTestLauncher(bulkEditProcessUserIdentifiersJob);

    final JobParameters jobParameters = prepareJobParameters(BULK_EDIT_IDENTIFIERS, USER, BARCODE, BARCODES_FOR_PROGRESS_CSV, true);
    testLauncher.launchJob(jobParameters);

    var jobCaptor = ArgumentCaptor.forClass(org.folio.de.entity.Job.class);

    // expected 4 events: 1st - job started, 2nd, 3rd - updates after each chunk (100 identifiers), 4th - job completed
    Mockito.verify(kafkaService, Mockito.times(4)).send(Mockito.any(), Mockito.any(), jobCaptor.capture());

    verifyJobProgressUpdates(jobCaptor);
  }

  @Test
  @DisplayName("Update retrieval progress (item identifiers) successfully")
  void shouldUpdateProgressUponItemIdentifiersJob() throws Exception {

    JobLauncherTestUtils testLauncher = createTestLauncher(bulkEditProcessItemIdentifiersJob);

    final JobParameters jobParameters = prepareJobParameters(BULK_EDIT_IDENTIFIERS, ITEM, BARCODE, BARCODES_FOR_PROGRESS_CSV, true);
    testLauncher.launchJob(jobParameters);

    var jobCaptor = ArgumentCaptor.forClass(org.folio.de.entity.Job.class);

    // expected 4 events: 1st - job started, 2nd, 3rd - updates after each chunk (100 identifiers), 4th - job completed
    Mockito.verify(kafkaService, Mockito.times(4)).send(Mockito.any(), Mockito.any(), jobCaptor.capture());

    verifyJobProgressUpdates(jobCaptor);
  }

  @ParameterizedTest
  @EnumSource(value = IdentifierType.class, names = {"USER_NAME", "EXTERNAL_SYSTEM_ID"}, mode = EnumSource.Mode.EXCLUDE)
  @DisplayName("Run bulk-edit (item identifiers) successfully")
  void uploadItemIdentifiersJobTest(IdentifierType identifierType) throws Exception {

    JobLauncherTestUtils testLauncher = createTestLauncher(bulkEditProcessItemIdentifiersJob);

    final JobParameters jobParameters = prepareJobParameters(BULK_EDIT_IDENTIFIERS, ITEM, identifierType, ITEM_BARCODES_CSV, true);
    JobExecution jobExecution = testLauncher.launchJob(jobParameters);

    verifyFileOutput(jobExecution, EXPECTED_BULK_EDIT_ITEM_OUTPUT);

    assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

    // check if caching works
    wireMockServer.verify(1, getRequestedFor(urlEqualTo("/item-note-types/8d0a5eca-25de-4391-81a9-236eeefdd20b")));
  }

  @Test
  @DisplayName("Upload item identifiers (holdingsRecordId) successfully")
  void shouldProcessMultipleItemsOnHoldingsRecordId() throws Exception {

    JobLauncherTestUtils testLauncher = createTestLauncher(bulkEditProcessItemIdentifiersJob);

    final JobParameters jobParameters = prepareJobParameters(BULK_EDIT_IDENTIFIERS, ITEM, HOLDINGS_RECORD_ID, ITEM_HOLDINGS_CSV, true);
    JobExecution jobExecution = testLauncher.launchJob(jobParameters);

    verifyFileOutput(jobExecution, EXPECTED_BULK_EDIT_ITEM_OUTPUT, EXPECTED_BULK_EDIT_ITEM_IDENTIFIERS_HOLDINGS_ERRORS_OUTPUT);

    assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
  }

  @Test
  @DisplayName("Run bulk-edit (user identifiers) with errors")
  void bulkEditUserJobTestWithErrors() throws Exception {
    JobLauncherTestUtils testLauncher = createTestLauncher(bulkEditProcessUserIdentifiersJob);

    final JobParameters jobParameters = prepareJobParameters(BULK_EDIT_IDENTIFIERS, USER, BARCODE, BARCODES_SOME_NOT_FOUND, true);

    JobExecution jobExecution = testLauncher.launchJob(jobParameters);

    verifyFileOutput(jobExecution, EXPECTED_BULK_EDIT_OUTPUT_SOME_NOT_FOUND);

    assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

    // check if caching works
    wireMockServer.verify(1, getRequestedFor(urlEqualTo("/groups/3684a786-6671-4268-8ed0-9db82ebca60b")));
  }

  @Test
  @DisplayName("Run bulk-edit (item identifiers) with errors")
  void bulkEditItemJobTestWithErrors() throws Exception {
    JobLauncherTestUtils testLauncher = createTestLauncher(bulkEditProcessItemIdentifiersJob);

    final JobParameters jobParameters = prepareJobParameters(BULK_EDIT_IDENTIFIERS, ITEM, BARCODE, ITEM_BARCODES_SOME_NOT_FOUND, true);

    JobExecution jobExecution = testLauncher.launchJob(jobParameters);

    verifyFileOutput(jobExecution, EXPECTED_BULK_EDIT_ITEM_OUTPUT_SOME_NOT_FOUND);

    assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
  }

  @Test
  @DisplayName("Run bulk-edit (user query) successfully")
  void bulkEditUserQueryJobTest() throws Exception {
    JobLauncherTestUtils testLauncher = createTestLauncher(bulkEditUserCqlJob);

    final JobParameters jobParameters = prepareJobParameters(ExportType.BULK_EDIT_QUERY, USER, BARCODE, USERS_QUERY_FILE_PATH, true);
    JobExecution jobExecution = testLauncher.launchJob(jobParameters);

    verifyFileOutput(jobExecution, EXPECTED_BULK_EDIT_USER_OUTPUT);

    assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
  }

  @Test
  @DisplayName("Process users without patron group id successfully")
  void shouldProcessUsersWithoutPatronGroupIdSuccessfully() throws Exception {
    JobLauncherTestUtils testLauncher = createTestLauncher(bulkEditUserCqlJob);

    final JobParameters jobParameters = prepareJobParameters(ExportType.BULK_EDIT_QUERY, USER, BARCODE, QUERY_NO_GROUP_FILE_PATH, true);
    JobExecution jobExecution = testLauncher.launchJob(jobParameters);

    verifyFileOutput(jobExecution, EXPECTED_NO_GROUP_OUTPUT);

    assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
  }

  @Test
  @DisplayName("Run bulk-edit (item query) successfully")
  void bulkEditItemQueryJobTest() throws Exception {
    JobLauncherTestUtils testLauncher = createTestLauncher(bulkEditItemCqlJob);

    final JobParameters jobParameters = prepareJobParameters(ExportType.BULK_EDIT_QUERY, ITEM, BARCODE, ITEMS_QUERY_FILE_PATH, true);
    JobExecution jobExecution = testLauncher.launchJob(jobParameters);

    verifyFileOutput(jobExecution, EXPECTED_ITEMS_QUERY_OUTPUT);

    assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
  }

  @ParameterizedTest
  @ValueSource(strings = {USER_RECORD_CSV, USER_RECORD_CSV_NOT_FOUND, USER_RECORD_CSV_BAD_CONTENT, USER_RECORD_CSV_BAD_CUSTOM_FIELD, USER_RECORD_CSV_EMPTY_PATRON_GROUP})
  @DisplayName("Run update user records w/ and w/o errors")
  void uploadUserRecordsJobTest(String csvFileName) throws Exception {
    JobLauncherTestUtils testLauncher = createTestLauncher(bulkEditUpdateUserRecordsJob);
    final JobParameters jobParameters = prepareJobParameters(BULK_EDIT_UPDATE, USER, BARCODE, csvFileName, true);
    JobExecution jobExecution = testLauncher.launchJob(jobParameters);

    assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

    var errors = bulkEditProcessingErrorsService.readErrorsFromCSV(jobExecution.getJobParameters().getString("jobId"), csvFileName, 10);

    if (!USER_RECORD_CSV.equals(csvFileName)) {
      if (USER_RECORD_CSV_BAD_CUSTOM_FIELD.equals(csvFileName)) {
        assertThat(errors.getErrors()).hasSize(2);
      } else {
        assertThat(errors.getErrors()).hasSize(1);
      }
      assertThat(jobExecution.getExecutionContext().getString(OUTPUT_FILES_IN_STORAGE)).isNotEmpty();
    } else {
      assertThat(jobExecution.getExecutionContext().getString(OUTPUT_FILES_IN_STORAGE)).isNotEmpty();
      assertThat(errors.getErrors()).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {ITEM_RECORD_CSV, ITEM_RECORD_CSV_NOT_FOUND, ITEM_RECORD_CSV_BAD_CALL_NUMBER_TYPE,
    ITEM_RECORD_CSV_BAD_DAMAGED_STATUS, ITEM_RECORD_CSV_BAD_LOAN_TYPE, ITEM_RECORD_CSV_BAD_LOCATION,
    ITEM_RECORD_CSV_BAD_NOTE_TYPE, ITEM_RECORD_CSV_BAD_RELATIONSHIP, ITEM_RECORD_CSV_BAD_SERVICE_POINT,
    ITEM_RECORD_CSV_INVALID_NOTES, ITEM_RECORD_CSV_INVALID_CIRCULATION_NOTES})
  @DisplayName("Run update item records w/ and w/o errors")
  void uploadItemRecordsJobTest(String csvFileName) throws Exception {
    JobLauncherTestUtils testLauncher = createTestLauncher(bulkEditUpdateItemRecordsJob);
    final JobParameters jobParameters = prepareJobParameters(BULK_EDIT_UPDATE, ITEM, BARCODE, csvFileName, true);
    JobExecution jobExecution = testLauncher.launchJob(jobParameters);

    assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

    var errors = bulkEditProcessingErrorsService.readErrorsFromCSV(jobExecution.getJobParameters().getString("jobId"), csvFileName, 10);
    if (!ITEM_RECORD_CSV.equals(csvFileName)) {
      assertThat(errors.getErrors()).hasSize(1);
      assertThat(jobExecution.getExecutionContext().getString(OUTPUT_FILES_IN_STORAGE)).isNotEmpty();
    } else {
      assertThat(errors.getErrors()).isEmpty();
      assertThat(jobExecution.getExecutionContext().getString(OUTPUT_FILES_IN_STORAGE)).isNotEmpty();
    }
  }

  @Test
  @DisplayName("Run item records update when in-app updates available - successful")
  @SneakyThrows
  void shouldUseInAppUpdatesFileIfPresent() {
    // create a copy of file since it will be deleted and consequent test runs will fail
    Files.copy(Path.of(ITEM_RECORD_IN_APP_UPDATED), Path.of(ITEM_RECORD_IN_APP_UPDATED_COPY), StandardCopyOption.REPLACE_EXISTING);

    JobLauncherTestUtils testLauncher = createTestLauncher(bulkEditUpdateItemRecordsJob);
    var builder = new JobParametersBuilder(prepareJobParameters(BULK_EDIT_UPDATE, ITEM, BARCODE, ITEM_RECORD_CSV, true));
    builder.addString(UPDATED_FILE_NAME, ITEM_RECORD_IN_APP_UPDATED_COPY);
    JobExecution jobExecution = testLauncher.launchJob(builder.toJobParameters());

    assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

    var request = wireMockServer.getAllServeEvents().get(0).getRequest();
    assertThat(request.getMethod().getName()).isEqualTo("PUT");
    assertThat(request.getUrl()).isEqualTo("/inventory/items/8a29baff-b703-4c3a-8b7b-ef476b2cd583");
  }

  @Test
  @DisplayName("Run rollback user records successfully")
  void rollBackUserRecordsJobTest() throws Exception {
    JobLauncherTestUtils testLauncher = createTestLauncher(bulkEditRollBackJob);
    File srcFile = new File(USER_RECORD_CSV);
    File destFile = new File(USER_RECORD_ROLLBACK_CSV);
    FileUtils.copyFile(srcFile, destFile);
    var parameters = new HashMap<String, JobParameter>();
    parameters.put(Constants.JOB_ID, new JobParameter("74914e57-3406-4757-938b-9a3f718d0ee6"));
    parameters.put(Constants.FILE_NAME, new JobParameter(USER_RECORD_ROLLBACK_CSV));
    JobExecution jobExecution = testLauncher.launchJob(new JobParameters(parameters));
    assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
  }

  @Test
  @DisplayName("Double quotes in data should be escaped")
  @SneakyThrows
  void shouldEscapeDoubleQuotes() {
    JobLauncherTestUtils testLauncher = createTestLauncher(bulkEditProcessItemIdentifiersJob);

    final JobParameters jobParameters = prepareJobParameters(BULK_EDIT_IDENTIFIERS, ITEM, BARCODE, ITEM_BARCODES_DOUBLE_QOUTES_CSV, true);
    JobExecution jobExecution = testLauncher.launchJob(jobParameters);

    verifyFileOutput(jobExecution, EXPECTED_BULK_EDIT_ITEM_OUTPUT_ESCAPED);

    assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
  }

  @SneakyThrows
  private void verifyFileOutput(JobExecution jobExecution, String output) {
    final ExecutionContext executionContext = jobExecution.getExecutionContext();
    String fileInStorage = (String) executionContext.get("outputFilesInStorage");
    if (fileInStorage.contains(";")) {
      String[] links = fileInStorage.split(";");
      fileInStorage = links[0];
      String errorInStorage = links[1];
      final FileSystemResource actualResultWithErrors = actualFileOutput(errorInStorage);
      final FileSystemResource expectedResultWithErrors = jobExecution.getJobInstance().getJobName().contains("-USER") ?
        new FileSystemResource(EXPECTED_BULK_EDIT_OUTPUT_ERRORS) :
        new FileSystemResource(EXPECTED_BULK_EDIT_ITEM_OUTPUT_ERRORS);
      assertFileEquals(expectedResultWithErrors, actualResultWithErrors);
    }
    final FileSystemResource actualResult = actualFileOutput(fileInStorage);
    FileSystemResource expectedCharges = new FileSystemResource(output);
    assertFileEquals(expectedCharges, actualResult);
  }

  @SneakyThrows
  private void verifyFileOutput(JobExecution jobExecution, String output, String expectedErrorOutput) {
    final ExecutionContext executionContext = jobExecution.getExecutionContext();
    String fileInStorage = (String) executionContext.get("outputFilesInStorage");
    String[] links = fileInStorage.split(";");
    fileInStorage = links[0];
    String errorInStorage = links[1];
    final FileSystemResource actualResultWithErrors = actualFileOutput(errorInStorage);
    final FileSystemResource expectedResultWithErrors = new FileSystemResource(expectedErrorOutput);
    assertFileEquals(expectedResultWithErrors, actualResultWithErrors);
    final FileSystemResource actualResult = actualFileOutput(fileInStorage);
    FileSystemResource expectedCharges = new FileSystemResource(output);
    assertFileEquals(expectedCharges, actualResult);
  }

  @SneakyThrows
  private JobParameters prepareJobParameters(ExportType exportType, EntityType entityType, IdentifierType identifierType, String path, boolean hasOutcomeFile) {
    Map<String, JobParameter> params = new HashMap<>();
    if (hasOutcomeFile) {
      String workDir =
        System.getProperty("java.io.tmpdir")
          + File.separator
          + springApplicationName
          + File.separator;
      params.put(JobParameterNames.TEMP_OUTPUT_FILE_PATH, new JobParameter(workDir + "out"));
      try {
        if (Files.notExists(Path.of(workDir + "out"))) {
          Files.createFile(Path.of(workDir + "out"));
          Files.createFile(Path.of(workDir + "out.csv"));
        }
      } catch (IOException e) {
        fail(e.getMessage());
      }
    }
    if (ExportType.BULK_EDIT_UPDATE == exportType) {
      params.put(ROLLBACK_FILE, new JobParameter("rollback/file/path"));
      params.put(FILE_NAME, new JobParameter(path));
    } else if (ExportType.BULK_EDIT_QUERY == exportType) {
      params.put("query", new JobParameter(readQueryString(path)));
    } else if (BULK_EDIT_IDENTIFIERS == exportType) {
      params.put(FILE_NAME, new JobParameter(path));
      params.put(TOTAL_CSV_LINES, new JobParameter(countLines(Path.of(path), false)));
    }

    String jobId = UUID.randomUUID().toString();
    params.put(JobParameterNames.JOB_ID, new JobParameter(jobId));
    params.put(EXPORT_TYPE, new JobParameter(exportType.getValue()));
    params.put(ENTITY_TYPE, new JobParameter(entityType.getValue()));
    params.put(IDENTIFIER_TYPE, new JobParameter(identifierType.getValue()));

    return new JobParameters(params);
  }

  @SneakyThrows
  private String readQueryString(String path) {
    try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
      return reader.readLine();
    }
  }

  private void verifyJobProgressUpdates(ArgumentCaptor<org.folio.de.entity.Job> jobCaptor) {
    var job = jobCaptor.getAllValues().get(1);
    assertThat(job.getProgress().getTotal()).isEqualTo(80);
    assertThat(job.getProgress().getProcessed()).isEqualTo(100);
    assertThat(job.getProgress().getProgress()).isEqualTo(45);
    assertThat(job.getProgress().getSuccess()).isEqualTo(80);
    assertThat(job.getProgress().getErrors()).isZero();

    job = jobCaptor.getAllValues().get(2);
    assertThat(job.getProgress().getTotal()).isEqualTo(144);
    assertThat(job.getProgress().getProcessed()).isEqualTo(179);
    assertThat(job.getProgress().getProgress()).isEqualTo(90);
    assertThat(job.getProgress().getSuccess()).isEqualTo(144);
    assertThat(job.getProgress().getErrors()).isZero();

    job = jobCaptor.getAllValues().get(3);
    assertThat(job.getProgress().getTotal()).isEqualTo(179);
    assertThat(job.getProgress().getProcessed()).isEqualTo(179);
    assertThat(job.getProgress().getProgress()).isEqualTo(100);
    assertThat(job.getProgress().getSuccess()).isEqualTo(144);
    assertThat(job.getProgress().getErrors()).isEqualTo(35);
  }

}
