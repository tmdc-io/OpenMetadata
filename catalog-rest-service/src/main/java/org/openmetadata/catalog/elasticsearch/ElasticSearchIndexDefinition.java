package org.openmetadata.catalog.elasticsearch;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.openmetadata.catalog.Entity;
import org.openmetadata.catalog.entity.data.Dashboard;
import org.openmetadata.catalog.entity.data.GlossaryTerm;
import org.openmetadata.catalog.entity.data.Pipeline;
import org.openmetadata.catalog.entity.data.Table;
import org.openmetadata.catalog.entity.data.Topic;
import org.openmetadata.catalog.entity.teams.Team;
import org.openmetadata.catalog.entity.teams.User;
import org.openmetadata.catalog.type.Column;
import org.openmetadata.catalog.type.EntityReference;
import org.openmetadata.catalog.type.EventType;
import org.openmetadata.catalog.type.TagLabel;
import org.openmetadata.catalog.type.Task;
import org.openmetadata.catalog.util.FullyQualifiedName;

@Slf4j
public class ElasticSearchIndexDefinition {
  final EnumMap<ElasticSearchIndexType, ElasticSearchIndexStatus> elasticSearchIndexes =
      new EnumMap<>(ElasticSearchIndexType.class);
  private final RestHighLevelClient client;

  public ElasticSearchIndexDefinition(RestHighLevelClient client) {
    this.client = client;
    for (ElasticSearchIndexType elasticSearchIndexType : ElasticSearchIndexType.values()) {
      elasticSearchIndexes.put(elasticSearchIndexType, ElasticSearchIndexStatus.NOT_CREATED);
    }
  }

  public enum ElasticSearchIndexStatus {
    CREATED,
    NOT_CREATED,
    FAILED
  }

  public enum ElasticSearchIndexType {
    TABLE_SEARCH_INDEX("table_search_index", "/elasticsearch/table_index_mapping.json"),
    TOPIC_SEARCH_INDEX("topic_search_index", "/elasticsearch/topic_index_mapping.json"),
    DASHBOARD_SEARCH_INDEX("dashboard_search_index", "/elasticsearch/dashboard_index_mapping.json"),
    PIPELINE_SEARCH_INDEX("pipeline_search_index", "/elasticsearch/pipeline_index_mapping.json"),
    USER_SEARCH_INDEX("user_search_index", "/elasticsearch/user_index_mapping.json"),
    TEAM_SEARCH_INDEX("team_search_index", "/elasticsearch/team_index_mapping.json"),
    GLOSSARY_SEARCH_INDEX("glossary_search_index", "/elasticsearch/glossary_index_mapping.json");

    public final String indexName;
    public final String indexMappingFile;

    ElasticSearchIndexType(String indexName, String indexMappingFile) {
      this.indexName = indexName;
      this.indexMappingFile = indexMappingFile;
    }
  }

  public void createIndexes() {
    for (ElasticSearchIndexType elasticSearchIndexType : ElasticSearchIndexType.values()) {
      createIndex(elasticSearchIndexType);
    }
  }

  public void updateIndexes() {
    for (ElasticSearchIndexType elasticSearchIndexType : ElasticSearchIndexType.values()) {
      updateIndex(elasticSearchIndexType);
    }
  }

  public void dropIndexes() {
    for (ElasticSearchIndexType elasticSearchIndexType : ElasticSearchIndexType.values()) {
      deleteIndex(elasticSearchIndexType);
    }
  }

  public boolean checkIndexExistsOrCreate(ElasticSearchIndexType indexType) {
    boolean exists = elasticSearchIndexes.get(indexType) == ElasticSearchIndexStatus.CREATED;
    if (!exists) {
      exists = createIndex(indexType);
    }
    return exists;
  }

  private boolean createIndex(ElasticSearchIndexType elasticSearchIndexType) {
    try {
      GetIndexRequest gRequest = new GetIndexRequest(elasticSearchIndexType.indexName);
      gRequest.local(false);
      boolean exists = client.indices().exists(gRequest, RequestOptions.DEFAULT);
      if (!exists) {
        String elasticSearchIndexMapping = getIndexMapping(elasticSearchIndexType);
        CreateIndexRequest request = new CreateIndexRequest(elasticSearchIndexType.indexName);
        request.mapping(elasticSearchIndexMapping, XContentType.JSON);
        CreateIndexResponse createIndexResponse = client.indices().create(request, RequestOptions.DEFAULT);
        LOG.info("{} Created {}", elasticSearchIndexType.indexName, createIndexResponse.isAcknowledged());
      }
      setIndexStatus(elasticSearchIndexType, ElasticSearchIndexStatus.CREATED);
    } catch (Exception e) {
      setIndexStatus(elasticSearchIndexType, ElasticSearchIndexStatus.FAILED);
      LOG.error("Failed to create Elastic Search indexes due to", e);
      return false;
    }
    return true;
  }

  private void updateIndex(ElasticSearchIndexType elasticSearchIndexType) {
    try {
      GetIndexRequest gRequest = new GetIndexRequest(elasticSearchIndexType.indexName);
      gRequest.local(false);
      boolean exists = client.indices().exists(gRequest, RequestOptions.DEFAULT);
      String elasticSearchIndexMapping = getIndexMapping(elasticSearchIndexType);
      if (exists) {
        PutMappingRequest request = new PutMappingRequest(elasticSearchIndexType.indexName);
        request.source(elasticSearchIndexMapping, XContentType.JSON);
        AcknowledgedResponse putMappingResponse = client.indices().putMapping(request, RequestOptions.DEFAULT);
        LOG.info("{} Updated {}", elasticSearchIndexType.indexName, putMappingResponse.isAcknowledged());
      } else {
        CreateIndexRequest request = new CreateIndexRequest(elasticSearchIndexType.indexName);
        request.mapping(elasticSearchIndexMapping, XContentType.JSON);
        CreateIndexResponse createIndexResponse = client.indices().create(request, RequestOptions.DEFAULT);
        LOG.info("{} Created {}", elasticSearchIndexType.indexName, createIndexResponse.isAcknowledged());
      }
      setIndexStatus(elasticSearchIndexType, ElasticSearchIndexStatus.CREATED);
    } catch (Exception e) {
      setIndexStatus(elasticSearchIndexType, ElasticSearchIndexStatus.FAILED);
      LOG.error("Failed to update Elastic Search indexes due to", e);
    }
  }

  private void deleteIndex(ElasticSearchIndexType elasticSearchIndexType) {
    try {
      DeleteIndexRequest request = new DeleteIndexRequest(elasticSearchIndexType.indexName);
      AcknowledgedResponse deleteIndexResponse = client.indices().delete(request, RequestOptions.DEFAULT);
      LOG.info("{} Deleted {}", elasticSearchIndexType.indexName, deleteIndexResponse.isAcknowledged());
    } catch (IOException e) {
      LOG.error("Failed to delete Elastic Search indexes due to", e);
    }
  }

  private void setIndexStatus(ElasticSearchIndexType indexType, ElasticSearchIndexStatus elasticSearchIndexStatus) {
    elasticSearchIndexes.put(indexType, elasticSearchIndexStatus);
  }

  public String getIndexMapping(ElasticSearchIndexType elasticSearchIndexType) throws IOException {
    InputStream in = ElasticSearchIndexDefinition.class.getResourceAsStream(elasticSearchIndexType.indexMappingFile);
    return new String(in.readAllBytes());
  }

  public ElasticSearchIndexType getIndexMappingByEntityType(String type) {
    if (type.equalsIgnoreCase(Entity.TABLE)) {
      return ElasticSearchIndexType.TABLE_SEARCH_INDEX;
    } else if (type.equalsIgnoreCase(Entity.DASHBOARD)) {
      return ElasticSearchIndexType.DASHBOARD_SEARCH_INDEX;
    } else if (type.equalsIgnoreCase(Entity.PIPELINE)) {
      return ElasticSearchIndexType.PIPELINE_SEARCH_INDEX;
    } else if (type.equalsIgnoreCase(Entity.TOPIC)) {
      return ElasticSearchIndexType.TOPIC_SEARCH_INDEX;
    } else if (type.equalsIgnoreCase(Entity.USER)) {
      return ElasticSearchIndexType.USER_SEARCH_INDEX;
    } else if (type.equalsIgnoreCase(Entity.TEAM)) {
      return ElasticSearchIndexType.TEAM_SEARCH_INDEX;
    } else if (type.equalsIgnoreCase(Entity.GLOSSARY)) {
      return ElasticSearchIndexType.GLOSSARY_SEARCH_INDEX;
    }
    throw new RuntimeException("Failed to find index doc for type " + type);
  }
}

@Getter
@Setter
@Builder
class ESEntityReference {
  String id;
  String name;
  String fullyQualifiedName;
  String description;
  Boolean deleted;
  String href;
}

@SuperBuilder
@Data
class ElasticSearchIndex {
  String name;

  @JsonProperty("display_name")
  String displayName;

  String fqdn;
  String service;
  Boolean deleted;

  @JsonProperty("service_type")
  String serviceType;

  @JsonProperty("service_category")
  String serviceCategory;

  @JsonProperty("entity_type")
  String entityType;

  List<ElasticSearchSuggest> suggest;
  String description;
  String tier;
  List<String> tags;
  EntityReference owner;
  List<String> followers;

  @JsonProperty("last_updated_timestamp")
  @Builder.Default
  Long lastUpdatedTimestamp = System.currentTimeMillis();
}

@Getter
@Builder
class ElasticSearchSuggest {
  String input;
  Integer weight;
}

@Getter
@Builder
class FlattenColumn {
  String name;
  String description;
  List<String> tags;
}

class ParseTags {
  String tierTag;
  final List<String> tags;

  ParseTags(List<String> tags) {
    if (!tags.isEmpty()) {
      List<String> tagsList = new ArrayList<>(tags);
      for (String tag : tagsList) {
        if (tag.toLowerCase().matches("(.*)tier(.*)")) {
          tierTag = tag;
          break;
        }
      }
      if (tierTag != null) {
        tagsList.remove(tierTag);
      }
      this.tags = tagsList;
    } else {
      this.tags = tags;
    }
  }
}

@EqualsAndHashCode(callSuper = true)
@Getter
@SuperBuilder(builderMethodName = "internalBuilder")
@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
class TableESIndex extends ElasticSearchIndex {

  @JsonProperty("table_id")
  String tableId;

  String database;

  @JsonProperty("database_schema")
  String databaseSchema;

  @JsonProperty("table_type")
  String tableType;

  @JsonProperty("column_names")
  List<String> columnNames;

  @JsonProperty("column_descriptions")
  List<String> columnDescriptions;

  @JsonProperty("monthly_stats")
  Integer monthlyStats;

  @JsonProperty("monthly_percentile_rank")
  Integer monthlyPercentileRank;

  @JsonProperty("weekly_stats")
  Integer weeklyStats;

  @JsonProperty("weekly_percentile_rank")
  Integer weeklyPercentileRank;

  @JsonProperty("daily_stats")
  Integer dailyStats;

  @JsonProperty("daily_percentile_rank")
  Integer dailyPercentileRank;

  public static TableESIndexBuilder builder(Table table, EventType eventType) {
    String tableId = table.getId().toString();
    String tableName = table.getName();
    String description = table.getDescription() != null ? table.getDescription() : "";
    String tableType = table.getTableType() != null ? table.getTableType().toString() : "Regular";
    List<String> tags = new ArrayList<>();
    List<String> columnNames = new ArrayList<>();
    List<String> columnDescriptions = new ArrayList<>();
    List<ElasticSearchSuggest> suggest = new ArrayList<>();
    suggest.add(ElasticSearchSuggest.builder().input(table.getFullyQualifiedName()).weight(5).build());
    suggest.add(ElasticSearchSuggest.builder().input(table.getName()).weight(10).build());

    if (table.getTags() != null) {
      table.getTags().forEach(tag -> tags.add(tag.getTagFQN()));
    }

    if (table.getColumns() != null) {
      List<FlattenColumn> cols = new ArrayList<>();
      cols = parseColumns(table.getColumns(), cols, null);

      for (FlattenColumn col : cols) {
        if (col.getTags() != null) {
          tags.addAll(col.getTags());
        }
        columnDescriptions.add(col.getDescription());
        columnNames.add(col.getName());
      }
    }
    ParseTags parseTags = new ParseTags(tags);
    Long updatedTimestamp = table.getUpdatedAt();
    TableESIndexBuilder tableESIndexBuilder =
        internalBuilder()
            .tableId(tableId)
            .deleted(table.getDeleted())
            .name(tableName)
            .displayName(tableName)
            .description(description)
            .lastUpdatedTimestamp(updatedTimestamp)
            .fqdn(table.getFullyQualifiedName())
            .suggest(suggest)
            .entityType("table")
            .serviceCategory("databaseService")
            .columnNames(columnNames)
            .columnDescriptions(columnDescriptions)
            .tableType(tableType)
            .tags(parseTags.tags)
            .tier(parseTags.tierTag);

    if (table.getDatabase() != null) {
      tableESIndexBuilder.database(table.getDatabase().getName());
    }

    if (table.getDatabaseSchema() != null) {
      tableESIndexBuilder.databaseSchema(table.getDatabaseSchema().getName());
    }

    if (table.getService() != null) {
      tableESIndexBuilder.service(table.getService().getName());
      tableESIndexBuilder.serviceType(table.getServiceType().toString());
    }

    if (table.getUsageSummary() != null) {
      tableESIndexBuilder
          .weeklyStats(table.getUsageSummary().getWeeklyStats().getCount())
          .weeklyPercentileRank(table.getUsageSummary().getWeeklyStats().getPercentileRank().intValue())
          .dailyStats(table.getUsageSummary().getDailyStats().getCount())
          .dailyPercentileRank(table.getUsageSummary().getDailyStats().getPercentileRank().intValue())
          .monthlyStats(table.getUsageSummary().getMonthlyStats().getCount())
          .monthlyPercentileRank(table.getUsageSummary().getMonthlyStats().getPercentileRank().intValue());
    }
    if (table.getFollowers() != null) {
      tableESIndexBuilder.followers(
          table.getFollowers().stream().map(item -> item.getId().toString()).collect(Collectors.toList()));
    } else if (eventType == EventType.ENTITY_CREATED) {
      tableESIndexBuilder.followers(Collections.emptyList());
    }

    if (table.getOwner() != null) {
      tableESIndexBuilder.owner(table.getOwner());
    }

    return tableESIndexBuilder;
  }

  private static List<FlattenColumn> parseColumns(
      List<Column> columns, List<FlattenColumn> flattenColumns, String parentColumn) {
    Optional<String> optParentColumn = Optional.ofNullable(parentColumn).filter(Predicate.not(String::isEmpty));
    List<String> tags = new ArrayList<>();
    for (Column col : columns) {
      String columnName = col.getName();
      if (optParentColumn.isPresent()) {
        columnName = FullyQualifiedName.add(optParentColumn.get(), columnName);
      }
      if (col.getTags() != null) {
        tags = col.getTags().stream().map(TagLabel::getTagFQN).collect(Collectors.toList());
      }

      FlattenColumn flattenColumn = FlattenColumn.builder().name(columnName).description(col.getDescription()).build();

      if (!tags.isEmpty()) {
        flattenColumn.tags = tags;
      }
      flattenColumns.add(flattenColumn);
      if (col.getChildren() != null) {
        parseColumns(col.getChildren(), flattenColumns, col.getName());
      }
    }
    return flattenColumns;
  }
}

@EqualsAndHashCode(callSuper = true)
@Getter
@SuperBuilder(builderMethodName = "internalBuilder")
@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
class TopicESIndex extends ElasticSearchIndex {
  @JsonProperty("topic_id")
  String topicId;

  public static TopicESIndexBuilder builder(Topic topic, EventType eventType) {
    List<String> tags = new ArrayList<>();
    List<ElasticSearchSuggest> suggest = new ArrayList<>();
    suggest.add(ElasticSearchSuggest.builder().input(topic.getFullyQualifiedName()).weight(5).build());
    suggest.add(ElasticSearchSuggest.builder().input(topic.getName()).weight(10).build());

    if (topic.getTags() != null) {
      topic.getTags().forEach(tag -> tags.add(tag.getTagFQN()));
    }
    ParseTags parseTags = new ParseTags(tags);
    Long updatedTimestamp = topic.getUpdatedAt();
    String description = topic.getDescription() != null ? topic.getDescription() : "";
    String displayName = topic.getDisplayName() != null ? topic.getDisplayName() : "";
    TopicESIndexBuilder topicESIndexBuilder =
        internalBuilder()
            .topicId(topic.getId().toString())
            .deleted(topic.getDeleted())
            .name(topic.getName())
            .displayName(displayName)
            .description(description)
            .fqdn(topic.getFullyQualifiedName())
            .lastUpdatedTimestamp(updatedTimestamp)
            .suggest(suggest)
            .serviceCategory("messagingService")
            .entityType("topic")
            .tags(parseTags.tags)
            .tier(parseTags.tierTag);

    if (topic.getService() != null) {
      topicESIndexBuilder.service(topic.getService().getName());
      topicESIndexBuilder.serviceType(topic.getServiceType().toString());
    }
    if (topic.getFollowers() != null) {
      topicESIndexBuilder.followers(
          topic.getFollowers().stream().map(item -> item.getId().toString()).collect(Collectors.toList()));
    } else if (eventType == EventType.ENTITY_CREATED) {
      topicESIndexBuilder.followers(Collections.emptyList());
    }

    if (topic.getOwner() != null) {
      topicESIndexBuilder.owner(topic.getOwner());
    }
    return topicESIndexBuilder;
  }
}

@EqualsAndHashCode(callSuper = true)
@Getter
@SuperBuilder(builderMethodName = "internalBuilder")
@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
class DashboardESIndex extends ElasticSearchIndex {
  @JsonProperty("dashboard_id")
  String dashboardId;

  @JsonProperty("chart_names")
  List<String> chartNames;

  @JsonProperty("chart_descriptions")
  List<String> chartDescriptions;

  @JsonProperty("monthly_stats")
  Integer monthlyStats;

  @JsonProperty("monthly_percentile_rank")
  Integer monthlyPercentileRank;

  @JsonProperty("weekly_stats")
  Integer weeklyStats;

  @JsonProperty("weekly_percentile_rank")
  Integer weeklyPercentileRank;

  @JsonProperty("daily_stats")
  Integer dailyStats;

  @JsonProperty("daily_percentile_rank")
  Integer dailyPercentileRank;

  public static DashboardESIndexBuilder builder(Dashboard dashboard, EventType eventType) {
    List<String> tags = new ArrayList<>();
    List<String> chartNames = new ArrayList<>();
    List<String> chartDescriptions = new ArrayList<>();
    List<ElasticSearchSuggest> suggest = new ArrayList<>();
    suggest.add(ElasticSearchSuggest.builder().input(dashboard.getFullyQualifiedName()).weight(5).build());
    suggest.add(ElasticSearchSuggest.builder().input(dashboard.getDisplayName()).weight(10).build());
    Long updatedTimestamp = dashboard.getUpdatedAt();
    if (dashboard.getTags() != null) {
      dashboard.getTags().forEach(tag -> tags.add(tag.getTagFQN()));
    }

    for (EntityReference chart : dashboard.getCharts()) {
      chartNames.add(chart.getDisplayName());
      chartDescriptions.add(chart.getDescription());
    }
    ParseTags parseTags = new ParseTags(tags);
    String description = dashboard.getDescription() != null ? dashboard.getDescription() : "";
    String displayName = dashboard.getDisplayName() != null ? dashboard.getDisplayName() : "";
    DashboardESIndexBuilder dashboardESIndexBuilder =
        internalBuilder()
            .dashboardId(dashboard.getId().toString())
            .deleted(dashboard.getDeleted())
            .name(dashboard.getDisplayName())
            .displayName(displayName)
            .description(description)
            .fqdn(dashboard.getFullyQualifiedName())
            .lastUpdatedTimestamp(updatedTimestamp)
            .chartNames(chartNames)
            .chartDescriptions(chartDescriptions)
            .entityType("dashboard")
            .suggest(suggest)
            .serviceCategory("dashboardService")
            .tags(parseTags.tags)
            .tier(parseTags.tierTag);

    if (dashboard.getService() != null) {
      dashboardESIndexBuilder.service(dashboard.getService().getName());
      dashboardESIndexBuilder.serviceType(dashboard.getServiceType().toString());
    }
    if (dashboard.getUsageSummary() != null) {
      dashboardESIndexBuilder
          .weeklyStats(dashboard.getUsageSummary().getWeeklyStats().getCount())
          .weeklyPercentileRank(dashboard.getUsageSummary().getWeeklyStats().getPercentileRank().intValue())
          .dailyStats(dashboard.getUsageSummary().getDailyStats().getCount())
          .dailyPercentileRank(dashboard.getUsageSummary().getDailyStats().getPercentileRank().intValue())
          .monthlyStats(dashboard.getUsageSummary().getMonthlyStats().getCount())
          .monthlyPercentileRank(dashboard.getUsageSummary().getMonthlyStats().getPercentileRank().intValue());
    }

    if (dashboard.getFollowers() != null) {
      dashboardESIndexBuilder.followers(
          dashboard.getFollowers().stream().map(item -> item.getId().toString()).collect(Collectors.toList()));
    } else if (eventType == EventType.ENTITY_CREATED) {
      dashboardESIndexBuilder.followers(Collections.emptyList());
    }
    if (dashboard.getOwner() != null) {
      dashboardESIndexBuilder.owner(dashboard.getOwner());
    }
    return dashboardESIndexBuilder;
  }
}

@EqualsAndHashCode(callSuper = true)
@Getter
@SuperBuilder(builderMethodName = "internalBuilder")
@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
class PipelineESIndex extends ElasticSearchIndex {
  @JsonProperty("pipeline_id")
  String pipelineId;

  @JsonProperty("task_names")
  List<String> taskNames;

  @JsonProperty("task_descriptions")
  List<String> taskDescriptions;

  public static PipelineESIndexBuilder builder(Pipeline pipeline, EventType eventType) {
    List<String> tags = new ArrayList<>();
    List<String> taskNames = new ArrayList<>();
    List<String> taskDescriptions = new ArrayList<>();
    List<ElasticSearchSuggest> suggest = new ArrayList<>();
    suggest.add(ElasticSearchSuggest.builder().input(pipeline.getFullyQualifiedName()).weight(5).build());
    suggest.add(ElasticSearchSuggest.builder().input(pipeline.getDisplayName()).weight(10).build());

    if (pipeline.getTags() != null) {
      pipeline.getTags().forEach(tag -> tags.add(tag.getTagFQN()));
    }

    if (pipeline.getTasks() != null) {
      for (Task task : pipeline.getTasks()) {
        taskNames.add(task.getDisplayName());
        taskDescriptions.add(task.getDescription());
      }
    }

    Long updatedTimestamp = pipeline.getUpdatedAt();
    ParseTags parseTags = new ParseTags(tags);
    String description = pipeline.getDescription() != null ? pipeline.getDescription() : "";
    String displayName = pipeline.getDisplayName() != null ? pipeline.getDisplayName() : "";
    PipelineESIndexBuilder pipelineESIndexBuilder =
        internalBuilder()
            .pipelineId(pipeline.getId().toString())
            .deleted(pipeline.getDeleted())
            .name(
                displayName) // pipeline names can be unique ids from source, hence use displayName for search indexing
            .displayName(displayName)
            .description(description)
            .fqdn(pipeline.getFullyQualifiedName())
            .lastUpdatedTimestamp(updatedTimestamp)
            .taskNames(taskNames)
            .taskDescriptions(taskDescriptions)
            .entityType("pipeline")
            .suggest(suggest)
            .serviceCategory("pipelineService")
            .tags(parseTags.tags)
            .tier(parseTags.tierTag);

    if (pipeline.getService() != null) {
      pipelineESIndexBuilder.service(pipeline.getService().getName());
      pipelineESIndexBuilder.serviceType(pipeline.getServiceType().toString());
    }
    if (pipeline.getFollowers() != null) {
      pipelineESIndexBuilder.followers(
          pipeline.getFollowers().stream().map(item -> item.getId().toString()).collect(Collectors.toList()));
    } else if (eventType == EventType.ENTITY_CREATED) {
      pipelineESIndexBuilder.followers(Collections.emptyList());
    }

    if (pipeline.getOwner() != null) {
      pipelineESIndexBuilder.owner(pipeline.getOwner());
    }
    return pipelineESIndexBuilder;
  }
}

@Getter
@SuperBuilder(builderMethodName = "internalBuilder")
@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
class UserESIndex {
  @JsonProperty("user_id")
  String userId;

  @JsonProperty("name")
  String name;

  @JsonProperty("display_name")
  String displayName;

  @JsonProperty("email")
  String email;

  @JsonProperty("entity_type")
  String entityType;

  @JsonProperty("teams")
  List<String> teams;

  @JsonProperty("roles")
  List<String> roles;

  @JsonProperty("last_updated_timestamp")
  @Builder.Default
  Long lastUpdatedTimestamp = System.currentTimeMillis();

  List<ElasticSearchSuggest> suggest;

  Boolean deleted;

  public static UserESIndexBuilder builder(User user) {
    List<String> teams = new ArrayList<>();
    List<String> roles = new ArrayList<>();
    List<ElasticSearchSuggest> suggest = new ArrayList<>();
    suggest.add(ElasticSearchSuggest.builder().input(user.getName()).weight(5).build());
    suggest.add(ElasticSearchSuggest.builder().input(user.getDisplayName()).weight(10).build());
    Long updatedTimestamp = user.getUpdatedAt();
    String displayName = user.getDisplayName() != null ? user.getDisplayName() : "";
    if (user.getTeams() != null) {
      for (EntityReference team : user.getTeams()) {
        teams.add(team.getId().toString());
      }
    }

    for (EntityReference role : user.getRoles()) {
      roles.add(role.getId().toString());
    }

    return internalBuilder()
        .userId(user.getId().toString())
        .deleted(user.getDeleted())
        .name(user.getName())
        .email(user.getEmail())
        .displayName(displayName)
        .lastUpdatedTimestamp(updatedTimestamp)
        .entityType("user")
        .suggest(suggest)
        .teams(teams)
        .roles(roles);
  }
}

@Getter
@SuperBuilder(builderMethodName = "internalBuilder")
@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
class TeamESIndex {
  @JsonProperty("team_id")
  String teamId;

  @JsonProperty("name")
  String name;

  @JsonProperty("display_name")
  String displayName;

  @JsonProperty("entity_type")
  String entityType;

  @JsonProperty("users")
  List<String> users;

  @JsonProperty("owns")
  List<String> owns;

  @JsonProperty("last_updated_timestamp")
  @Builder.Default
  Long lastUpdatedTimestamp = System.currentTimeMillis();

  List<ElasticSearchSuggest> suggest;

  Boolean deleted;

  public static TeamESIndexBuilder builder(Team team) {
    List<String> users = new ArrayList<>();
    List<String> owns = new ArrayList<>();
    List<ElasticSearchSuggest> suggest = new ArrayList<>();
    suggest.add(ElasticSearchSuggest.builder().input(team.getName()).weight(5).build());
    suggest.add(ElasticSearchSuggest.builder().input(team.getDisplayName()).weight(10).build());
    Long updatedTimestamp = team.getUpdatedAt();
    String displayName = team.getDisplayName() != null ? team.getDisplayName() : "";
    if (team.getUsers() != null) {
      for (EntityReference user : team.getUsers()) {
        users.add(user.getId().toString());
      }
    }
    if (team.getOwns() != null) {
      for (EntityReference own : team.getOwns()) {
        owns.add(own.getId().toString());
      }
    }

    return internalBuilder()
        .teamId(team.getId().toString())
        .deleted(team.getDeleted())
        .name(team.getName()) // pipeline names can be unique ids from source, hence use displayName for search
        // indexing
        .displayName(displayName)
        .lastUpdatedTimestamp(updatedTimestamp)
        .entityType("team")
        .suggest(suggest)
        .owns(owns)
        .users(users);
  }
}

@EqualsAndHashCode(callSuper = true)
@Getter
@SuperBuilder(builderMethodName = "internalBuilder")
@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
class GlossaryTermESIndex extends ElasticSearchIndex {
  @JsonProperty("glossary_term_id")
  String glossaryTermId;

  @JsonProperty("glossary_id")
  String glossaryId;

  @JsonProperty("display_name")
  String displayName;

  @JsonProperty("entity_type")
  String entityType;

  @JsonProperty("status")
  String status;

  @JsonProperty("glossary_name")
  String glossaryName;

  public static GlossaryTermESIndexBuilder builder(GlossaryTerm glossaryTerm, EventType eventType) {
    List<String> tags = new ArrayList<>();
    List<ElasticSearchSuggest> suggest = new ArrayList<>();
    suggest.add(ElasticSearchSuggest.builder().input(glossaryTerm.getName()).weight(5).build());
    suggest.add(ElasticSearchSuggest.builder().input(glossaryTerm.getDisplayName()).weight(10).build());

    if (glossaryTerm.getTags() != null) {
      glossaryTerm.getTags().forEach(tag -> tags.add(tag.getTagFQN()));
    }

    Long updatedTimestamp = glossaryTerm.getUpdatedAt();
    ParseTags parseTags = new ParseTags(tags);
    String description = glossaryTerm.getDescription() != null ? glossaryTerm.getDescription() : "";
    String displayName = glossaryTerm.getDisplayName() != null ? glossaryTerm.getDisplayName() : "";
    return internalBuilder()
        .glossaryTermId(glossaryTerm.getId().toString())
        .name(glossaryTerm.getName())
        .displayName(displayName)
        .description(description)
        .fqdn(glossaryTerm.getFullyQualifiedName())
        .glossaryId(glossaryTerm.getGlossary().getId().toString())
        .glossaryName(glossaryTerm.getGlossary().getName())
        .lastUpdatedTimestamp(updatedTimestamp)
        .entityType("glossaryTerm")
        .suggest(suggest)
        .deleted(glossaryTerm.getDeleted())
        .tags(parseTags.tags);
  }
}
