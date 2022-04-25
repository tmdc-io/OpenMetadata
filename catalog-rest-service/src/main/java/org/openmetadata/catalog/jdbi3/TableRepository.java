/*
 *  Copyright 2021 Collate
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openmetadata.catalog.jdbi3;

import static org.openmetadata.catalog.Entity.DATABASE_SCHEMA;
import static org.openmetadata.catalog.Entity.FIELD_DESCRIPTION;
import static org.openmetadata.catalog.Entity.FIELD_FOLLOWERS;
import static org.openmetadata.catalog.Entity.FIELD_OWNER;
import static org.openmetadata.catalog.Entity.FIELD_TAGS;
import static org.openmetadata.catalog.Entity.LOCATION;
import static org.openmetadata.catalog.Entity.TABLE;
import static org.openmetadata.catalog.util.EntityUtil.getColumnField;
import static org.openmetadata.common.utils.CommonUtil.listOrEmpty;
import static org.openmetadata.common.utils.CommonUtil.nullOrEmpty;
import static org.openmetadata.common.utils.CommonUtil.parseDate;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.jdbi.v3.sqlobject.transaction.Transaction;
import org.openmetadata.catalog.Entity;
import org.openmetadata.catalog.entity.data.DatabaseSchema;
import org.openmetadata.catalog.entity.data.Table;
import org.openmetadata.catalog.exception.CatalogExceptionMessage;
import org.openmetadata.catalog.exception.EntityNotFoundException;
import org.openmetadata.catalog.jdbi3.DatabaseSchemaRepository.DatabaseSchemaEntityInterface;
import org.openmetadata.catalog.resources.databases.DatabaseUtil;
import org.openmetadata.catalog.resources.databases.TableResource;
import org.openmetadata.catalog.tests.ColumnTest;
import org.openmetadata.catalog.tests.CustomMetric;
import org.openmetadata.catalog.tests.TableTest;
import org.openmetadata.catalog.tests.type.TestCaseResult;
import org.openmetadata.catalog.type.ChangeDescription;
import org.openmetadata.catalog.type.Column;
import org.openmetadata.catalog.type.ColumnJoin;
import org.openmetadata.catalog.type.ColumnProfile;
import org.openmetadata.catalog.type.DailyCount;
import org.openmetadata.catalog.type.DataModel;
import org.openmetadata.catalog.type.EntityReference;
import org.openmetadata.catalog.type.Include;
import org.openmetadata.catalog.type.JoinedWith;
import org.openmetadata.catalog.type.Relationship;
import org.openmetadata.catalog.type.SQLQuery;
import org.openmetadata.catalog.type.TableConstraint;
import org.openmetadata.catalog.type.TableData;
import org.openmetadata.catalog.type.TableJoins;
import org.openmetadata.catalog.type.TableProfile;
import org.openmetadata.catalog.type.TagLabel;
import org.openmetadata.catalog.util.EntityInterface;
import org.openmetadata.catalog.util.EntityUtil;
import org.openmetadata.catalog.util.EntityUtil.Fields;
import org.openmetadata.catalog.util.FullyQualifiedName;
import org.openmetadata.catalog.util.JsonUtils;
import org.openmetadata.catalog.util.RestUtil;
import org.openmetadata.common.utils.CommonUtil;

@Slf4j
public class TableRepository extends EntityRepository<Table> {
  // Table fields that can be patched in a PATCH request
  static final String TABLE_PATCH_FIELDS = "owner,tags,tableConstraints,tablePartition";
  // Table fields that can be updated in a PUT request
  static final String TABLE_UPDATE_FIELDS = "owner,tags,tableConstraints,tablePartition,dataModel,profileSample";

  public TableRepository(CollectionDAO dao) {
    super(
        TableResource.COLLECTION_PATH,
        TABLE,
        Table.class,
        dao.tableDAO(),
        dao,
        TABLE_PATCH_FIELDS,
        TABLE_UPDATE_FIELDS);
  }

  @Override
  public Table setFields(Table table, Fields fields) throws IOException {
    setDefaultFields(table);
    table.setTableConstraints(fields.contains("tableConstraints") ? table.getTableConstraints() : null);
    table.setOwner(fields.contains(FIELD_OWNER) ? getOwner(table) : null);
    table.setFollowers(fields.contains(FIELD_FOLLOWERS) ? getFollowers(table) : null);
    table.setUsageSummary(
        fields.contains("usageSummary") ? EntityUtil.getLatestUsage(daoCollection.usageDAO(), table.getId()) : null);
    table.setTags(fields.contains(FIELD_TAGS) ? getTags(table.getFullyQualifiedName()) : null);
    getColumnTags(fields.contains(FIELD_TAGS), table.getColumns());
    table.setJoins(fields.contains("joins") ? getJoins(table) : null);
    table.setSampleData(fields.contains("sampleData") ? getSampleData(table) : null);
    table.setViewDefinition(fields.contains("viewDefinition") ? table.getViewDefinition() : null);
    table.setTableProfile(fields.contains("tableProfile") ? getTableProfile(table) : null);
    table.setLocation(fields.contains("location") ? getLocation(table) : null);
    table.setTableQueries(fields.contains("tableQueries") ? getQueries(table) : null);
    table.setProfileSample(fields.contains("profileSample") ? table.getProfileSample() : null);
    table.setTableTests(fields.contains("tests") ? getTableTests(table) : null);
    getColumnTests(fields.contains("tests"), table);
    getCustomMetrics(fields.contains("customMetrics"), table);
    return table;
  }

  private void setDefaultFields(Table table) throws IOException {
    EntityReference schemaRef = getContainer(table.getId(), TABLE);
    DatabaseSchema schema = Entity.getEntity(schemaRef, Fields.EMPTY_FIELDS, Include.ALL);
    table.withDatabaseSchema(schemaRef).withDatabase(schema.getDatabase()).withService(schema.getService());
  }

  @Override
  public void restorePatchAttributes(Table original, Table updated) {
    // Patch can't make changes to following fields. Ignore the changes.
    updated
        .withFullyQualifiedName(original.getFullyQualifiedName())
        .withName(original.getName())
        .withDatabase(original.getDatabase())
        .withService(original.getService())
        .withId(original.getId());
  }

  @Override
  public EntityInterface<Table> getEntityInterface(Table entity) {
    return new TableEntityInterface(entity);
  }

  public static String getFQN(Table table) {
    return (table != null && table.getDatabaseSchema() != null)
        ? FullyQualifiedName.add(table.getDatabaseSchema().getFullyQualifiedName(), table.getName())
        : null;
  }

  @Transaction
  public Table addJoins(UUID tableId, TableJoins joins) throws IOException {
    // Validate the request content
    Table table = daoCollection.tableDAO().findEntityById(tableId);
    if (!CommonUtil.dateInRange(RestUtil.DATE_FORMAT, joins.getStartDate(), 0, 30)) {
      throw new IllegalArgumentException("Date range can only include past 30 days starting today");
    }

    // Validate joined columns
    for (ColumnJoin join : joins.getColumnJoins()) {
      validateColumn(table, join.getColumnName());
      validateColumnFQNs(join.getJoinedWith());
    }

    // With all validation done, add new joins
    for (ColumnJoin join : joins.getColumnJoins()) {
      String columnFQN = FullyQualifiedName.add(table.getFullyQualifiedName(), join.getColumnName());
      addJoin(joins.getStartDate(), columnFQN, join.getJoinedWith());
    }
    return table.withJoins(getJoins(table));
  }

  @Transaction
  public Table addSampleData(UUID tableId, TableData tableData) throws IOException {
    // Validate the request content
    Table table = daoCollection.tableDAO().findEntityById(tableId);

    // Validate all the columns
    for (String columnName : tableData.getColumns()) {
      validateColumn(table, columnName);
    }
    // Make sure each row has number values for all the columns
    for (List<Object> row : tableData.getRows()) {
      if (row.size() != tableData.getColumns().size()) {
        throw new IllegalArgumentException(
            String.format(
                "Number of columns is %d but row has %d sample values", tableData.getColumns().size(), row.size()));
      }
    }

    daoCollection
        .entityExtensionDAO()
        .insert(tableId.toString(), "table.sampleData", "tableData", JsonUtils.pojoToJson(tableData));
    setFields(table, Fields.EMPTY_FIELDS);
    return table.withSampleData(tableData);
  }

  @Transaction
  public Table addTableProfileData(UUID tableId, TableProfile tableProfile) throws IOException {
    // Validate the request content
    Table table = daoCollection.tableDAO().findEntityById(tableId);

    List<TableProfile> storedTableProfiles = getTableProfile(table);
    Map<String, TableProfile> storedMapTableProfiles = new HashMap<>();
    if (storedTableProfiles != null) {
      for (TableProfile profile : storedTableProfiles) {
        storedMapTableProfiles.put(profile.getProfileDate(), profile);
      }
    }
    // validate all the columns
    for (ColumnProfile columnProfile : tableProfile.getColumnProfile()) {
      validateColumn(table, columnProfile.getName());
    }
    storedMapTableProfiles.put(tableProfile.getProfileDate(), tableProfile);
    List<TableProfile> updatedProfiles = new ArrayList<>(storedMapTableProfiles.values());

    daoCollection
        .entityExtensionDAO()
        .insert(tableId.toString(), "table.tableProfile", "tableProfile", JsonUtils.pojoToJson(updatedProfiles));
    setFields(table, Fields.EMPTY_FIELDS);
    return table.withTableProfile(getTableProfile(table));
  }

  @Transaction
  public Table addLocation(UUID tableId, UUID locationId) throws IOException {
    Table table = daoCollection.tableDAO().findEntityById(tableId);
    EntityReference location = daoCollection.locationDAO().findEntityReferenceById(locationId);
    // A table has only one location.
    deleteFrom(tableId, TABLE, Relationship.HAS, LOCATION);
    addRelationship(tableId, locationId, TABLE, LOCATION, Relationship.HAS);
    setFields(table, Fields.EMPTY_FIELDS);
    return table.withLocation(location);
  }

  @Transaction
  public Table addQuery(UUID tableId, SQLQuery query) throws IOException {
    // Validate the request content
    try {
      byte[] checksum = MessageDigest.getInstance("MD5").digest(query.getQuery().getBytes());
      query.setChecksum(Hex.encodeHexString(checksum));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    Table table = daoCollection.tableDAO().findEntityById(tableId);
    List<SQLQuery> storedQueries = getQueries(table);
    Map<String, SQLQuery> storedMapQueries = new HashMap<>();
    if (storedQueries != null) {
      for (SQLQuery q : storedQueries) {
        storedMapQueries.put(q.getChecksum(), q);
      }
    }
    storedMapQueries.put(query.getChecksum(), query);
    List<SQLQuery> updatedQueries = new ArrayList<>(storedMapQueries.values());
    daoCollection
        .entityExtensionDAO()
        .insert(tableId.toString(), "table.tableQueries", "sqlQuery", JsonUtils.pojoToJson(updatedQueries));
    setFields(table, Fields.EMPTY_FIELDS);
    return table.withTableQueries(getQueries(table));
  }

  @Transaction
  public Table addTableTest(UUID tableId, TableTest tableTest) throws IOException {
    // Validate the request content
    Table table = daoCollection.tableDAO().findEntityById(tableId);
    // if ID is not passed we treat it as a new test case being added
    List<TableTest> storedTableTests = getTableTests(table);
    // we will override any test case name passed by user/client with tableName + testType
    // our assumption is there is only one instance of a test type as of now.
    tableTest.setName(table.getName() + "." + tableTest.getTestCase().getTableTestType().toString());
    Map<String, TableTest> storedMapTableTests = new HashMap<>();
    if (storedTableTests != null) {
      for (TableTest t : storedTableTests) {
        storedMapTableTests.put(t.getName(), t);
      }
    }
    // existing test, use the previous UUID
    if (storedMapTableTests.containsKey(tableTest.getName())) {
      TableTest prevTableTest = storedMapTableTests.get(tableTest.getName());
      tableTest.setId(prevTableTest.getId());
      // process test result
      if (!nullOrEmpty(tableTest.getResults())) {
        List<TestCaseResult> prevTestCaseResults = prevTableTest.getResults();
        prevTestCaseResults.addAll(tableTest.getResults());
        tableTest.setResults(prevTestCaseResults);
      }
    }

    storedMapTableTests.put(tableTest.getName(), tableTest);
    List<TableTest> updatedTests = new ArrayList<>(storedMapTableTests.values());
    daoCollection
        .entityExtensionDAO()
        .insert(tableId.toString(), "table.tableTests", "tableTest", JsonUtils.pojoToJson(updatedTests));
    setFields(table, Fields.EMPTY_FIELDS);
    // return the only test instead of querying all tests and results
    return table.withTableTests(List.of(tableTest));
  }

  @Transaction
  public Table deleteTableTest(UUID tableId, String tableTestType) throws IOException {
    // Validate the request content
    Table table = daoCollection.tableDAO().findEntityById(tableId);
    // if ID is not passed we treat it as a new test case being added
    List<TableTest> storedTableTests = getTableTests(table);
    // we will override any test case name passed by user/client with tableName + testType
    // our assumption is there is only one instance of a test type as of now.
    String tableTestName = table.getName() + "." + tableTestType;
    Map<String, TableTest> storedMapTableTests = new HashMap<>();
    if (storedTableTests != null) {
      for (TableTest t : storedTableTests) {
        storedMapTableTests.put(t.getName(), t);
      }
    }
    if (!storedMapTableTests.containsKey(tableTestName)) {
      throw new EntityNotFoundException(String.format("Failed to find %s for %s", tableTestName, table.getName()));
    }
    TableTest deleteTableTest = storedMapTableTests.get(tableTestName);
    storedMapTableTests.remove(tableTestName);
    List<TableTest> updatedTests = new ArrayList<>(storedMapTableTests.values());
    daoCollection
        .entityExtensionDAO()
        .insert(tableId.toString(), "table.tableTests", "tableTest", JsonUtils.pojoToJson(updatedTests));
    return table.withTableTests(List.of(deleteTableTest));
  }

  @Transaction
  public Table addColumnTest(UUID tableId, ColumnTest columnTest) throws IOException {
    // Validate the request content
    Table table = daoCollection.tableDAO().findEntityById(tableId);
    String columnName = columnTest.getColumnName();
    validateColumn(table, columnName);
    // we will override any test case name passed by user/client with columnName + testType
    // our assumption is there is only one instance of a test type as of now.
    columnTest.setName(columnName + "." + columnTest.getTestCase().getColumnTestType().toString());
    List<ColumnTest> storedColumnTests = getColumnTests(table, columnName);
    Map<String, ColumnTest> storedMapColumnTests = new HashMap<>();
    if (storedColumnTests != null) {
      for (ColumnTest ct : storedColumnTests) {
        storedMapColumnTests.put(ct.getName(), ct);
      }
    }

    // existingTest use the previous UUID
    if (storedMapColumnTests.containsKey(columnTest.getName())) {
      ColumnTest prevColumnTest = storedMapColumnTests.get(columnTest.getName());
      columnTest.setId(prevColumnTest.getId());

      // process test results
      if (!nullOrEmpty(columnTest.getResults())) {
        List<TestCaseResult> prevTestCaseResults = prevColumnTest.getResults();
        prevTestCaseResults.addAll(columnTest.getResults());
        columnTest.setResults(prevTestCaseResults);
      }
    }

    storedMapColumnTests.put(columnTest.getName(), columnTest);
    List<ColumnTest> updatedTests = new ArrayList<>(storedMapColumnTests.values());
    String extension = "table.column." + columnName + ".tests";
    daoCollection
        .entityExtensionDAO()
        .insert(table.getId().toString(), extension, "columnTest", JsonUtils.pojoToJson(updatedTests));
    setFields(table, Fields.EMPTY_FIELDS);
    // return the newly created/updated column test only
    for (Column column : table.getColumns()) {
      if (column.getName().equals(columnName)) {
        column.setColumnTests(List.of(columnTest));
      }
    }
    return table;
  }

  @Transaction
  public Table deleteColumnTest(UUID tableId, String columnName, String columnTestType) throws IOException {
    // Validate the request content
    Table table = daoCollection.tableDAO().findEntityById(tableId);
    validateColumn(table, columnName);
    // we will override any test case name passed by user/client with columnName + testType
    // our assumption is there is only one instance of a test type as of now.
    String columnTestName = columnName + "." + columnTestType;
    List<ColumnTest> storedColumnTests = getColumnTests(table, columnName);
    Map<String, ColumnTest> storedMapColumnTests = new HashMap<>();
    if (storedColumnTests != null) {
      for (ColumnTest ct : storedColumnTests) {
        storedMapColumnTests.put(ct.getName(), ct);
      }
    }

    if (!storedMapColumnTests.containsKey(columnTestName)) {
      throw new EntityNotFoundException(String.format("Failed to find %s for %s", columnTestName, table.getName()));
    }

    ColumnTest deleteColumnTest = storedMapColumnTests.get(columnTestName);
    storedMapColumnTests.remove(columnTestName);
    List<ColumnTest> updatedTests = new ArrayList<>(storedMapColumnTests.values());
    String extension = "table.column." + columnName + ".tests";
    daoCollection
        .entityExtensionDAO()
        .insert(table.getId().toString(), extension, "columnTest", JsonUtils.pojoToJson(updatedTests));
    // return the newly created/updated column test only
    for (Column column : table.getColumns()) {
      if (column.getName().equals(columnName)) {
        column.setColumnTests(List.of(deleteColumnTest));
      }
    }
    return table;
  }

  @Transaction
  public Table addCustomMetric(UUID tableId, CustomMetric customMetric) throws IOException {
    // Validate the request content
    Table table = daoCollection.tableDAO().findEntityById(tableId);
    String columnName = customMetric.getColumnName();
    validateColumn(table, columnName);

    // Override any custom metric definition with the same name
    List<CustomMetric> storedCustomMetrics = getCustomMetrics(table, columnName);
    Map<String, CustomMetric> storedMapCustomMetrics = new HashMap<>();
    if (storedCustomMetrics != null) {
      for (CustomMetric cm : storedCustomMetrics) {
        storedMapCustomMetrics.put(cm.getName(), cm);
      }
    }

    // existing metric use the previous UUID
    if (storedMapCustomMetrics.containsKey(customMetric.getName())) {
      CustomMetric prevMetric = storedMapCustomMetrics.get(customMetric.getName());
      customMetric.setId(prevMetric.getId());
    }

    storedMapCustomMetrics.put(customMetric.getName(), customMetric);
    List<CustomMetric> updatedMetrics = new ArrayList<>(storedMapCustomMetrics.values());
    String extension = "table.column." + columnName + ".customMetrics";
    daoCollection
        .entityExtensionDAO()
        .insert(table.getId().toString(), extension, "customMetric", JsonUtils.pojoToJson(updatedMetrics));
    setFields(table, Fields.EMPTY_FIELDS);
    // return the newly created/updated custom metric only
    for (Column column : table.getColumns()) {
      if (column.getName().equals(columnName)) {
        column.setCustomMetrics(List.of(customMetric));
      }
    }
    return table;
  }

  @Transaction
  public Table deleteCustomMetric(UUID tableId, String columnName, String metricName) throws IOException {
    // Validate the request content
    Table table = daoCollection.tableDAO().findEntityById(tableId);
    validateColumn(table, columnName);

    // Override any custom metric definition with the same name
    List<CustomMetric> storedCustomMetrics = getCustomMetrics(table, columnName);
    Map<String, CustomMetric> storedMapCustomMetrics = new HashMap<>();
    if (storedCustomMetrics != null) {
      for (CustomMetric cm : storedCustomMetrics) {
        storedMapCustomMetrics.put(cm.getName(), cm);
      }
    }

    if (!storedMapCustomMetrics.containsKey(metricName)) {
      throw new EntityNotFoundException(String.format("Failed to find %s for %s", metricName, table.getName()));
    }

    CustomMetric deleteCustomMetric = storedMapCustomMetrics.get(metricName);
    storedMapCustomMetrics.remove(metricName);
    List<CustomMetric> updatedMetrics = new ArrayList<>(storedMapCustomMetrics.values());
    String extension = "table.column." + columnName + ".customMetrics";
    daoCollection
        .entityExtensionDAO()
        .insert(table.getId().toString(), extension, "customMetric", JsonUtils.pojoToJson(updatedMetrics));
    // return the newly created/updated custom metric test only
    for (Column column : table.getColumns()) {
      if (column.getName().equals(columnName)) {
        column.setCustomMetrics(List.of(deleteCustomMetric));
      }
    }
    return table;
  }

  @Transaction
  public Table addDataModel(UUID tableId, DataModel dataModel) throws IOException {
    Table table = daoCollection.tableDAO().findEntityById(tableId);
    table.withDataModel(dataModel);

    // Carry forward the table description from the model to table entity, if empty
    if (nullOrEmpty(table.getDescription())) {
      table.setDescription(dataModel.getDescription());
    }
    // Carry forward the column description from the model to table columns, if empty
    for (Column modelColumn : listOrEmpty(dataModel.getColumns())) {
      Column stored =
          table.getColumns().stream()
              .filter(c -> EntityUtil.columnNameMatch.test(c, modelColumn))
              .findAny()
              .orElse(null);
      if (stored == null) {
        continue;
      }
      if (nullOrEmpty(stored.getDescription())) {
        stored.setDescription(modelColumn.getDescription());
      }
    }
    daoCollection.tableDAO().update(table.getId(), JsonUtils.pojoToJson(table));
    setFields(table, Fields.EMPTY_FIELDS);
    return table;
  }

  @Transaction
  public void deleteLocation(String tableId) {
    deleteFrom(UUID.fromString(tableId), TABLE, Relationship.HAS, LOCATION);
  }

  private void setColumnFQN(String parentFQN, List<Column> columns) {
    columns.forEach(
        c -> {
          String columnFqn = FullyQualifiedName.add(parentFQN, c.getName());
          c.setFullyQualifiedName(columnFqn);
          if (c.getChildren() != null) {
            setColumnFQN(columnFqn, c.getChildren());
          }
        });
  }

  private void addDerivedColumnTags(List<Column> columns) {
    if (nullOrEmpty(columns)) {
      return;
    }

    for (Column column : columns) {
      column.setTags(addDerivedTags(column.getTags()));
      if (column.getChildren() != null) {
        addDerivedColumnTags(column.getChildren());
      }
    }
  }

  @Override
  public void prepare(Table table) throws IOException {
    DatabaseSchema schema = Entity.getEntity(table.getDatabaseSchema(), Fields.EMPTY_FIELDS, Include.ALL);
    table.setDatabaseSchema(new DatabaseSchemaEntityInterface(schema).getEntityReference());
    table.setDatabase(schema.getDatabase());
    table.setService(schema.getService());
    table.setServiceType(schema.getServiceType());

    // Set data in table entity based on database relationship
    table.setFullyQualifiedName(getFQN(table));
    setColumnFQN(table.getFullyQualifiedName(), table.getColumns());

    // Check if owner is valid and set the relationship
    table.setOwner(Entity.getEntityReference(table.getOwner()));

    // Validate table tags and add derived tags to the list
    table.setTags(addDerivedTags(table.getTags()));

    // Validate column tags
    addDerivedColumnTags(table.getColumns());
  }

  private EntityReference getLocation(Table table) throws IOException {
    List<String> refs = findTo(table.getId(), TABLE, Relationship.HAS, LOCATION);
    ensureSingleRelationship(TABLE, table.getId(), refs, "location", false);
    return refs.isEmpty() ? null : Entity.getEntityReferenceById(LOCATION, UUID.fromString(refs.get(0)), Include.ALL);
  }

  @Override
  public void storeEntity(Table table, boolean update) throws IOException {
    // Relationships and fields such as href are derived and not stored as part of json
    EntityReference owner = table.getOwner();
    List<TagLabel> tags = table.getTags();
    EntityReference service = table.getService();

    // Don't store owner, database, href and tags as JSON. Build it on the fly based on relationships
    table.withOwner(null).withHref(null).withTags(null).withService(null);

    // Don't store column tags as JSON but build it on the fly based on relationships
    List<Column> columnWithTags = table.getColumns();
    table.setColumns(cloneWithoutTags(columnWithTags));
    table.getColumns().forEach(column -> column.setTags(null));

    store(table.getId(), table, update);

    // Restore the relationships
    table.withOwner(owner).withTags(tags).withColumns(columnWithTags).withService(service);
  }

  @Override
  public void storeRelationships(Table table) {
    // Add relationship from database to table
    addRelationship(table.getDatabaseSchema().getId(), table.getId(), DATABASE_SCHEMA, TABLE, Relationship.CONTAINS);

    // Add table owner relationship
    storeOwner(table, table.getOwner());

    // Add tag to table relationship
    applyTags(table);
  }

  @Override
  public EntityUpdater getUpdater(Table original, Table updated, Operation operation) {
    return new TableUpdater(original, updated, operation);
  }

  List<Column> cloneWithoutTags(List<Column> columns) {
    if (nullOrEmpty(columns)) {
      return columns;
    }
    List<Column> copy = new ArrayList<>();
    columns.forEach(c -> copy.add(cloneWithoutTags(c)));
    return copy;
  }

  private Column cloneWithoutTags(Column column) {
    List<Column> children = cloneWithoutTags(column.getChildren());
    return new Column()
        .withDescription(column.getDescription())
        .withName(column.getName())
        .withDisplayName(column.getDisplayName())
        .withFullyQualifiedName(column.getFullyQualifiedName())
        .withArrayDataType(column.getArrayDataType())
        .withConstraint(column.getConstraint())
        .withDataTypeDisplay(column.getDataTypeDisplay())
        .withDataType(column.getDataType())
        .withDataLength(column.getDataLength())
        .withPrecision(column.getPrecision())
        .withScale(column.getScale())
        .withOrdinalPosition(column.getOrdinalPosition())
        .withChildren(children);
  }

  private void applyTags(List<Column> columns) {
    // Add column level tags by adding tag to column relationship
    for (Column column : columns) {
      applyTags(column.getTags(), column.getFullyQualifiedName());
      if (column.getChildren() != null) {
        applyTags(column.getChildren());
      }
    }
  }

  @Override
  public void applyTags(Table table) {
    // Add table level tags by adding tag to table relationship
    super.applyTags(table);
    applyTags(table.getColumns());
  }

  private void getColumnTags(boolean setTags, List<Column> columns) {
    for (Column c : listOrEmpty(columns)) {
      c.setTags(setTags ? getTags(c.getFullyQualifiedName()) : null);
      getColumnTags(setTags, c.getChildren());
    }
  }

  // Validate if a given column exists in the table
  private void validateColumn(Table table, String columnName) {
    boolean validColumn = table.getColumns().stream().anyMatch(col -> col.getName().equals(columnName));
    if (!validColumn) {
      throw new IllegalArgumentException("Invalid column name " + columnName);
    }
  }

  // Validate if a given column exists in the table
  private void validateColumnFQN(Table table, String columnFQN) {
    boolean validColumn = false;
    for (Column column : table.getColumns()) {
      if (column.getFullyQualifiedName().equals(columnFQN)) {
        validColumn = true;
        break;
      }
    }
    if (!validColumn) {
      throw new IllegalArgumentException(CatalogExceptionMessage.invalidColumnFQN(columnFQN));
    }
  }

  private void validateColumnFQNs(List<JoinedWith> joinedWithList) {
    for (JoinedWith joinedWith : joinedWithList) {
      // Validate table
      String tableFQN = FullyQualifiedName.getTableFQN(joinedWith.getFullyQualifiedName());
      Table joinedWithTable = daoCollection.tableDAO().findEntityByName(tableFQN);

      // Validate column
      validateColumnFQN(joinedWithTable, joinedWith.getFullyQualifiedName());
    }
  }

  private void addJoin(String date, String columnFQN, List<JoinedWith> joinedWithList) throws IOException {
    for (JoinedWith joinedWith : joinedWithList) {
      // Use the column that comes alphabetically first as the from field and the other as to field.
      // This helps us keep the bidirectional relationship to a single row instead one row for
      // capturing relationship in each direction.
      //
      // One row like this     - fromColumn <--- joinedWith --> toColumn
      // Instead of additional - toColumn <--- joinedWith --> fromColumn
      String fromColumnFQN;
      String toColumnFQN;
      if (columnFQN.compareTo(joinedWith.getFullyQualifiedName()) < 0) {
        fromColumnFQN = columnFQN;
        toColumnFQN = joinedWith.getFullyQualifiedName();
      } else {
        fromColumnFQN = joinedWith.getFullyQualifiedName();
        toColumnFQN = columnFQN;
      }
      String json =
          daoCollection
              .fieldRelationshipDAO()
              .find(
                  fromColumnFQN,
                  toColumnFQN,
                  "table.columns.column",
                  "table.columns.column",
                  Relationship.JOINED_WITH.ordinal());

      DailyCount dailyCount = new DailyCount().withCount(joinedWith.getJoinCount()).withDate(date);
      List<DailyCount> dailyCountList;
      if (json == null) { // Create first entry
        dailyCountList = Collections.singletonList(dailyCount);
      } else { // Update the existing entry
        dailyCountList = JsonUtils.readObjects(json, DailyCount.class);
        boolean foundDate = false;
        for (DailyCount d :
            dailyCountList) { // If the date already exists, update the count. Otherwise, add a new entry
          if (d.getDate().equals(dailyCount.getDate())) {
            // Entry for date already exists. Update the count
            d.setCount(dailyCount.getCount());
            foundDate = true;
            break;
          }
        }
        if (!foundDate) {
          dailyCountList.add(dailyCount);
        }

        // Sort the dailyCount list by date
        dailyCountList.sort(
            (d1, d2) -> {
              try {
                return RestUtil.compareDates(d1.getDate(), d2.getDate());
              } catch (ParseException ignored) {
                // This should never happen
              }
              return 0;
            });

        ListIterator<DailyCount> iterator = dailyCountList.listIterator();
        while (iterator.hasNext()) {
          String reportedOnDate = iterator.next().getDate();
          if (!CommonUtil.dateInRange(RestUtil.DATE_FORMAT, reportedOnDate, 0, 30)) {
            iterator.remove();
            LOG.info(
                "Removed join entry for column {} with column {} on older date {}",
                columnFQN,
                joinedWith.getFullyQualifiedName(),
                reportedOnDate);
          }
        }
      }
      json = JsonUtils.pojoToJson(dailyCountList);

      daoCollection
          .fieldRelationshipDAO()
          .upsert(
              fromColumnFQN,
              toColumnFQN,
              "table.columns.column",
              "table.columns.column",
              Relationship.JOINED_WITH.ordinal(),
              "dailyCount",
              json);
    }
  }

  private TableJoins getJoins(Table table) throws IOException {
    String today = RestUtil.DATE_FORMAT.format(new Date()); // today
    String todayMinus30Days = CommonUtil.getDateStringByOffset(RestUtil.DATE_FORMAT, today, -30);
    TableJoins tableJoins =
        new TableJoins().withStartDate(todayMinus30Days).withDayCount(30).withColumnJoins(Collections.emptyList());

    List<List<String>> list =
        daoCollection
            .fieldRelationshipDAO()
            .listToByPrefix(
                table.getFullyQualifiedName(),
                "table.columns.column",
                "table.columns.column",
                Relationship.JOINED_WITH.ordinal());
    list.addAll(
        daoCollection
            .fieldRelationshipDAO()
            .listFromByPrefix(
                table.getFullyQualifiedName(),
                "table.columns.column",
                "table.columns.column",
                Relationship.JOINED_WITH.ordinal()));

    if (list.isEmpty()) { // No join information found. Return empty list
      return tableJoins;
    }

    // Map of <ColumnName> to List of <Fully Qualified Column names> it is joined with
    Map<String, List<JoinedWith>> map = new HashMap<>();

    // list [ [fromFQN, toFQN, json], ...] contains innerList [fromFQN, toFQN, json]
    for (List<String> innerList : list) {
      String columnName = FullyQualifiedName.getColumnName(innerList.get(0));
      List<JoinedWith> columnJoinList = map.computeIfAbsent(columnName, k -> new ArrayList<>());

      // Parse JSON to get daily counts and aggregate it
      List<DailyCount> dailyCountList = JsonUtils.readObjects(innerList.get(2), DailyCount.class);
      int aggregatedCount = 0;
      for (DailyCount d : dailyCountList) {
        if (CommonUtil.dateInRange(RestUtil.DATE_FORMAT, d.getDate(), 0, 30)) {
          aggregatedCount += d.getCount();
        }
      }
      columnJoinList.add(new JoinedWith().withFullyQualifiedName(innerList.get(1)).withJoinCount(aggregatedCount));
    }

    List<ColumnJoin> columnJoins = new ArrayList<>();
    for (Entry<String, List<JoinedWith>> entry : map.entrySet()) {
      columnJoins.add(new ColumnJoin().withColumnName(entry.getKey()).withJoinedWith(entry.getValue()));
    }
    return tableJoins.withColumnJoins(columnJoins);
  }

  private TableData getSampleData(Table table) throws IOException {
    return JsonUtils.readValue(
        daoCollection.entityExtensionDAO().getExtension(table.getId().toString(), "table.sampleData"), TableData.class);
  }

  private List<TableProfile> getTableProfile(Table table) throws IOException {
    List<TableProfile> tableProfiles =
        JsonUtils.readObjects(
            daoCollection.entityExtensionDAO().getExtension(table.getId().toString(), "table.tableProfile"),
            TableProfile.class);
    if (tableProfiles != null) {
      tableProfiles.sort(
          Comparator.comparing(p -> parseDate(p.getProfileDate(), RestUtil.DATE_FORMAT), Comparator.reverseOrder()));
    }
    return tableProfiles;
  }

  private List<SQLQuery> getQueries(Table table) throws IOException {
    List<SQLQuery> tableQueries =
        JsonUtils.readObjects(
            daoCollection.entityExtensionDAO().getExtension(table.getId().toString(), "table.tableQueries"),
            SQLQuery.class);
    if (tableQueries != null) {
      tableQueries.sort(Comparator.comparing(SQLQuery::getVote, Comparator.reverseOrder()));
    }
    return tableQueries;
  }

  private List<TableTest> getTableTests(Table table) throws IOException {
    return JsonUtils.readObjects(
        daoCollection.entityExtensionDAO().getExtension(table.getId().toString(), "table.tableTests"), TableTest.class);
  }

  private List<ColumnTest> getColumnTests(Table table, String columnName) throws IOException {
    String extension = "table.column." + columnName + ".tests";
    return JsonUtils.readObjects(
        daoCollection.entityExtensionDAO().getExtension(table.getId().toString(), extension), ColumnTest.class);
  }

  private void getColumnTests(boolean setTests, Table table) throws IOException {
    List<Column> columns = table.getColumns();
    for (Column c : listOrEmpty(columns)) {
      c.setColumnTests(setTests ? getColumnTests(table, c.getName()) : null);
    }
  }

  private List<CustomMetric> getCustomMetrics(Table table, String columnName) throws IOException {
    String extension = "table.column." + columnName + ".customMetrics";
    return JsonUtils.readObjects(
        daoCollection.entityExtensionDAO().getExtension(table.getId().toString(), extension), CustomMetric.class);
  }

  private void getCustomMetrics(boolean setMetrics, Table table) throws IOException {
    // Add custom metrics info to columns if requested
    List<Column> columns = table.getColumns();
    for (Column c : listOrEmpty(columns)) {
      c.setCustomMetrics(setMetrics ? getCustomMetrics(table, c.getName()) : null);
    }
  }

  public static class TableEntityInterface extends EntityInterface<Table> {
    public TableEntityInterface(Table entity) {
      super(Entity.TABLE, entity);
    }

    @Override
    public UUID getId() {
      return entity.getId();
    }

    @Override
    public String getDescription() {
      return entity.getDescription();
    }

    @Override
    public String getDisplayName() {
      return entity.getDisplayName();
    }

    @Override
    public String getName() {
      return entity.getName();
    }

    @Override
    public Boolean isDeleted() {
      return entity.getDeleted();
    }

    @Override
    public EntityReference getOwner() {
      return entity.getOwner();
    }

    @Override
    public String getFullyQualifiedName() {
      return entity.getFullyQualifiedName() != null ? entity.getFullyQualifiedName() : TableRepository.getFQN(entity);
    }

    @Override
    public List<TagLabel> getTags() {
      return entity.getTags();
    }

    @Override
    public Double getVersion() {
      return entity.getVersion();
    }

    @Override
    public String getUpdatedBy() {
      return entity.getUpdatedBy();
    }

    @Override
    public long getUpdatedAt() {
      return entity.getUpdatedAt();
    }

    @Override
    public URI getHref() {
      return entity.getHref();
    }

    @Override
    public List<EntityReference> getFollowers() {
      return entity.getFollowers();
    }

    @Override
    public Table getEntity() {
      return entity;
    }

    @Override
    public EntityReference getContainer() {
      return entity.getDatabase();
    }

    @Override
    public ChangeDescription getChangeDescription() {
      return entity.getChangeDescription();
    }

    @Override
    public void setId(UUID id) {
      entity.setId(id);
    }

    @Override
    public void setDescription(String description) {
      entity.setDescription(description);
    }

    @Override
    public void setDisplayName(String displayName) {
      entity.setDisplayName(displayName);
    }

    @Override
    public void setName(String name) {
      entity.setName(name);
    }

    @Override
    public void setUpdateDetails(String updatedBy, long updatedAt) {
      entity.setUpdatedBy(updatedBy);
      entity.setUpdatedAt(updatedAt);
    }

    @Override
    public void setChangeDescription(Double newVersion, ChangeDescription changeDescription) {
      entity.setVersion(newVersion);
      entity.setChangeDescription(changeDescription);
    }

    @Override
    public void setOwner(EntityReference owner) {
      entity.setOwner(owner);
    }

    @Override
    public void setDeleted(boolean flag) {
      entity.setDeleted(flag);
    }

    @Override
    public Table withHref(URI href) {
      return entity.withHref(href);
    }

    @Override
    public void setTags(List<TagLabel> tags) {
      entity.setTags(tags);
    }
  }

  /** Handles entity updated from PUT and POST operation. */
  public class TableUpdater extends EntityUpdater {
    public TableUpdater(Table original, Table updated, Operation operation) {
      super(original, updated, operation);
    }

    @Override
    public void entitySpecificUpdate() throws IOException {
      Table origTable = original.getEntity();
      Table updatedTable = updated.getEntity();
      DatabaseUtil.validateColumns(updatedTable);
      recordChange("tableType", origTable.getTableType(), updatedTable.getTableType());
      recordChange("profileSample", origTable.getProfileSample(), updatedTable.getProfileSample());
      updateConstraints(origTable, updatedTable);
      updateColumns("columns", origTable.getColumns(), updated.getEntity().getColumns(), EntityUtil.columnMatch);
    }

    private void updateConstraints(Table origTable, Table updatedTable) throws JsonProcessingException {
      List<TableConstraint> origConstraints = listOrEmpty(origTable.getTableConstraints());
      List<TableConstraint> updatedConstraints = listOrEmpty(updatedTable.getTableConstraints());

      origConstraints.sort(EntityUtil.compareTableConstraint);
      origConstraints.stream().map(TableConstraint::getColumns).forEach(Collections::sort);

      updatedConstraints.sort(EntityUtil.compareTableConstraint);
      updatedConstraints.stream().map(TableConstraint::getColumns).forEach(Collections::sort);

      List<TableConstraint> added = new ArrayList<>();
      List<TableConstraint> deleted = new ArrayList<>();
      recordListChange(
          "tableConstraints", origConstraints, updatedConstraints, added, deleted, EntityUtil.tableConstraintMatch);
    }

    private void updateColumns(
        String fieldName,
        List<Column> origColumns,
        List<Column> updatedColumns,
        BiPredicate<Column, Column> columnMatch)
        throws IOException {
      List<Column> deletedColumns = new ArrayList<>();
      List<Column> addedColumns = new ArrayList<>();
      recordListChange(fieldName, origColumns, updatedColumns, addedColumns, deletedColumns, columnMatch);
      // carry forward tags and description if deletedColumns matches added column
      Map<String, Column> addedColumnMap =
          addedColumns.stream().collect(Collectors.toMap(Column::getName, Function.identity()));

      for (Column deleted : deletedColumns) {
        if (addedColumnMap.containsKey(deleted.getName())) {
          Column addedColumn = addedColumnMap.get(deleted.getName());
          if ((addedColumn.getDescription() == null || addedColumn.getDescription().isEmpty())
              && (deleted.getDescription() == null || !deleted.getDescription().isEmpty())) {
            addedColumn.setDescription(deleted.getDescription());
          }
          if ((addedColumn.getTags() == null || addedColumn.getTags().isEmpty())
              && (deleted.getTags() == null || !deleted.getTags().isEmpty())) {
            addedColumn.setTags(deleted.getTags());
          }
        }
      }

      // Delete tags related to deleted columns
      deletedColumns.forEach(
          deleted -> daoCollection.tagUsageDAO().deleteTagsByTarget(deleted.getFullyQualifiedName()));

      // Add tags related to newly added columns
      for (Column added : addedColumns) {
        applyTags(added.getTags(), added.getFullyQualifiedName());
      }

      // Carry forward the user generated metadata from existing columns to new columns
      for (Column updated : updatedColumns) {
        // Find stored column matching name, data type and ordinal position
        Column stored = origColumns.stream().filter(c -> columnMatch.test(c, updated)).findAny().orElse(null);
        if (stored == null) { // New column added
          continue;
        }

        updateColumnDescription(stored, updated);
        updateColumnDataLength(stored, updated);
        updateColumnPrecision(stored, updated);
        updateColumnScale(stored, updated);
        updateTags(
            stored.getFullyQualifiedName(),
            EntityUtil.getFieldName(fieldName, updated.getName(), FIELD_TAGS),
            stored.getTags(),
            updated.getTags());
        updateColumnConstraint(stored, updated);

        if (updated.getChildren() != null && stored.getChildren() != null) {
          String childrenFieldName = EntityUtil.getFieldName(fieldName, updated.getName());
          updateColumns(childrenFieldName, stored.getChildren(), updated.getChildren(), columnMatch);
        }
      }

      majorVersionChange = majorVersionChange || !deletedColumns.isEmpty();
    }

    private void updateColumnDescription(Column origColumn, Column updatedColumn) throws JsonProcessingException {
      if (operation.isPut() && !nullOrEmpty(origColumn.getDescription())) {
        // Update description only when stored is empty to retain user authored descriptions
        updatedColumn.setDescription(origColumn.getDescription());
        return;
      }
      String columnField = getColumnField(original.getEntity(), origColumn, FIELD_DESCRIPTION);
      recordChange(columnField, origColumn.getDescription(), updatedColumn.getDescription());
    }

    private void updateColumnConstraint(Column origColumn, Column updatedColumn) throws JsonProcessingException {
      String columnField = getColumnField(original.getEntity(), origColumn, "constraint");
      recordChange(columnField, origColumn.getConstraint(), updatedColumn.getConstraint());
    }

    private void updateColumnDataLength(Column origColumn, Column updatedColumn) throws JsonProcessingException {
      String columnField = getColumnField(original.getEntity(), origColumn, "dataLength");
      boolean updated = recordChange(columnField, origColumn.getDataLength(), updatedColumn.getDataLength());
      if (updated && updatedColumn.getDataLength() < origColumn.getDataLength()) {
        // The data length of a column was reduced. Treat it as backward-incompatible change
        majorVersionChange = true;
      }
    }

    private void updateColumnPrecision(Column origColumn, Column updatedColumn) throws JsonProcessingException {
      String columnField = getColumnField(original.getEntity(), origColumn, "precision");
      boolean updated = recordChange(columnField, origColumn.getPrecision(), updatedColumn.getPrecision());
      if (origColumn.getPrecision() != null) { // Previously precision was set
        if (updated && updatedColumn.getPrecision() < origColumn.getPrecision()) {
          // The precision was reduced. Treat it as backward-incompatible change
          majorVersionChange = true;
        }
      }
    }

    private void updateColumnScale(Column origColumn, Column updatedColumn) throws JsonProcessingException {
      String columnField = getColumnField(original.getEntity(), origColumn, "scale");
      boolean updated = recordChange(columnField, origColumn.getScale(), updatedColumn.getScale());
      if (origColumn.getScale() != null) { // Previously scale was set
        if (updated && updatedColumn.getScale() < origColumn.getScale()) {
          // The scale was reduced. Treat it as backward-incompatible change
          majorVersionChange = true;
        }
      }
    }
  }
}
