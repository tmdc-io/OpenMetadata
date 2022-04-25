#  Copyright 2021 Collate
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#  http://www.apache.org/licenses/LICENSE-2.0
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import json
import logging
import ssl
import traceback
from datetime import datetime
from typing import List, Optional

from elasticsearch import Elasticsearch
from elasticsearch.connection import create_ssl_context

from metadata.config.common import ConfigModel
from metadata.generated.schema.entity.data.chart import Chart
from metadata.generated.schema.entity.data.dashboard import Dashboard
from metadata.generated.schema.entity.data.database import Database
from metadata.generated.schema.entity.data.databaseSchema import DatabaseSchema
from metadata.generated.schema.entity.data.glossaryTerm import GlossaryTerm
from metadata.generated.schema.entity.data.pipeline import Pipeline, Task
from metadata.generated.schema.entity.data.table import Column, Table
from metadata.generated.schema.entity.data.topic import Topic
from metadata.generated.schema.entity.services.connections.metadata.openMetadataConnection import (
    OpenMetadataConnection,
)
from metadata.generated.schema.entity.services.dashboardService import DashboardService
from metadata.generated.schema.entity.services.databaseService import DatabaseService
from metadata.generated.schema.entity.services.messagingService import MessagingService
from metadata.generated.schema.entity.services.pipelineService import PipelineService
from metadata.generated.schema.entity.teams.team import Team
from metadata.generated.schema.entity.teams.user import User
from metadata.generated.schema.type import entityReference
from metadata.ingestion.api.common import Entity
from metadata.ingestion.api.sink import Sink, SinkStatus
from metadata.ingestion.models.table_metadata import (
    DashboardESDocument,
    GlossaryTermESDocument,
    PipelineESDocument,
    TableESDocument,
    TeamESDocument,
    TopicESDocument,
    UserESDocument,
)
from metadata.ingestion.ometa.ometa_api import OpenMetadata
from metadata.ingestion.sink.elasticsearch_constants import (
    DASHBOARD_ELASTICSEARCH_INDEX_MAPPING,
    GLOSSARY_TERM_ELASTICSEARCH_INDEX_MAPPING,
    PIPELINE_ELASTICSEARCH_INDEX_MAPPING,
    TABLE_ELASTICSEARCH_INDEX_MAPPING,
    TEAM_ELASTICSEARCH_INDEX_MAPPING,
    TOPIC_ELASTICSEARCH_INDEX_MAPPING,
    USER_ELASTICSEARCH_INDEX_MAPPING,
)

logger = logging.getLogger(__name__)


def epoch_ms(dt: datetime):
    return int(dt.timestamp() * 1000)


class ElasticSearchConfig(ConfigModel):
    es_host: str
    es_port: int = 9200
    es_username: Optional[str] = None
    es_password: Optional[str] = None
    index_tables: Optional[bool] = True
    index_topics: Optional[bool] = True
    index_dashboards: Optional[bool] = True
    index_pipelines: Optional[bool] = True
    index_users: Optional[bool] = True
    index_teams: Optional[bool] = True
    index_glossary_terms: Optional[bool] = True
    table_index_name: str = "table_search_index"
    topic_index_name: str = "topic_search_index"
    dashboard_index_name: str = "dashboard_search_index"
    pipeline_index_name: str = "pipeline_search_index"
    user_index_name: str = "user_search_index"
    team_index_name: str = "team_search_index"
    glossary_term_index_name: str = "glossary_search_index"
    scheme: str = "http"
    use_ssl: bool = False
    verify_certs: bool = False
    timeout: int = 30
    ca_certs: Optional[str] = None


class ElasticsearchSink(Sink[Entity]):
    """ """

    DEFAULT_ELASTICSEARCH_INDEX_MAPPING = TABLE_ELASTICSEARCH_INDEX_MAPPING

    @classmethod
    def create(cls, config_dict: dict, metadata_config: OpenMetadataConnection):
        config = ElasticSearchConfig.parse_obj(config_dict)
        return cls(config, metadata_config)

    def __init__(
        self,
        config: ElasticSearchConfig,
        metadata_config: OpenMetadataConnection,
    ) -> None:

        self.config = config
        self.metadata_config = metadata_config

        self.status = SinkStatus()
        self.metadata = OpenMetadata(self.metadata_config)
        self.elasticsearch_doc_type = "_doc"
        http_auth = None
        if self.config.es_username:
            http_auth = (self.config.es_username, self.config.es_password)

        ssl_context = None
        if self.config.scheme == "https" and not self.config.verify_certs:
            ssl_context = create_ssl_context()
            ssl_context.check_hostname = False
            ssl_context.verify_mode = ssl.CERT_NONE

        self.elasticsearch_client = Elasticsearch(
            [
                {"host": self.config.es_host, "port": self.config.es_port},
            ],
            http_auth=http_auth,
            scheme=self.config.scheme,
            use_ssl=self.config.use_ssl,
            verify_certs=self.config.verify_certs,
            ssl_context=ssl_context,
            ca_certs=self.config.ca_certs,
        )

        if self.config.index_tables:
            self._check_or_create_index(
                self.config.table_index_name, TABLE_ELASTICSEARCH_INDEX_MAPPING
            )

        if self.config.index_topics:
            self._check_or_create_index(
                self.config.topic_index_name, TOPIC_ELASTICSEARCH_INDEX_MAPPING
            )
        if self.config.index_dashboards:
            self._check_or_create_index(
                self.config.dashboard_index_name, DASHBOARD_ELASTICSEARCH_INDEX_MAPPING
            )
        if self.config.index_pipelines:
            self._check_or_create_index(
                self.config.pipeline_index_name, PIPELINE_ELASTICSEARCH_INDEX_MAPPING
            )

        if self.config.index_users:
            self._check_or_create_index(
                self.config.user_index_name, USER_ELASTICSEARCH_INDEX_MAPPING
            )

        if self.config.index_teams:
            self._check_or_create_index(
                self.config.team_index_name, TEAM_ELASTICSEARCH_INDEX_MAPPING
            )

        if self.config.index_glossary_terms:
            self._check_or_create_index(
                self.config.glossary_term_index_name,
                GLOSSARY_TERM_ELASTICSEARCH_INDEX_MAPPING,
            )

    def _check_or_create_index(self, index_name: str, es_mapping: str):
        """
        Retrieve all indices that currently have {elasticsearch_alias} alias
        :return: list of elasticsearch indices
        """
        if self.elasticsearch_client.indices.exists(index_name):
            mapping = self.elasticsearch_client.indices.get_mapping()
            if not mapping[index_name]["mappings"]:
                logger.debug(
                    f"There are no mappings for index {index_name}. Updating the mapping"
                )
                es_mapping_dict = json.loads(es_mapping)
                es_mapping_update_dict = {
                    "properties": es_mapping_dict["mappings"]["properties"]
                }
                self.elasticsearch_client.indices.put_mapping(
                    index=index_name,
                    body=json.dumps(es_mapping_update_dict),
                    request_timeout=self.config.timeout,
                )
        else:
            logger.warning(
                "Received index not found error from Elasticsearch. "
                + "The index doesn't exist for a newly created ES. It's OK on first run."
            )
            # create new index with mapping
            self.elasticsearch_client.indices.create(
                index=index_name, body=es_mapping, request_timeout=self.config.timeout
            )

    def write_record(self, record: Entity) -> None:
        try:
            if isinstance(record, Table):
                table_doc = self._create_table_es_doc(record)
                self.elasticsearch_client.index(
                    index=self.config.table_index_name,
                    id=str(table_doc.table_id),
                    body=table_doc.json(),
                    request_timeout=self.config.timeout,
                )
            if isinstance(record, Topic):
                topic_doc = self._create_topic_es_doc(record)
                self.elasticsearch_client.index(
                    index=self.config.topic_index_name,
                    id=str(topic_doc.topic_id),
                    body=topic_doc.json(),
                    request_timeout=self.config.timeout,
                )
            if isinstance(record, Dashboard):
                dashboard_doc = self._create_dashboard_es_doc(record)
                self.elasticsearch_client.index(
                    index=self.config.dashboard_index_name,
                    id=str(dashboard_doc.dashboard_id),
                    body=dashboard_doc.json(),
                    request_timeout=self.config.timeout,
                )
            if isinstance(record, Pipeline):
                pipeline_doc = self._create_pipeline_es_doc(record)
                self.elasticsearch_client.index(
                    index=self.config.pipeline_index_name,
                    id=str(pipeline_doc.pipeline_id),
                    body=pipeline_doc.json(),
                    request_timeout=self.config.timeout,
                )

            if isinstance(record, User):
                user_doc = self._create_user_es_doc(record)
                self.elasticsearch_client.index(
                    index=self.config.user_index_name,
                    id=str(user_doc.user_id),
                    body=user_doc.json(),
                    request_timeout=self.config.timeout,
                )

            if isinstance(record, Team):
                team_doc = self._create_team_es_doc(record)
                self.elasticsearch_client.index(
                    index=self.config.team_index_name,
                    id=str(team_doc.team_id),
                    body=team_doc.json(),
                    request_timeout=self.config.timeout,
                )

            if isinstance(record, GlossaryTerm):
                glossary_term_doc = self._create_glossary_term_es_doc(record)
                self.elasticsearch_client.index(
                    index=self.config.glossary_term_index_name,
                    id=str(glossary_term_doc.glossary_term_id),
                    body=glossary_term_doc.json(),
                    request_timeout=self.config.timeout,
                )

            self.status.records_written(record.name.__root__)
        except Exception as e:
            logger.error(f"Failed to index entity {record} due to {e}")
            logger.debug(traceback.print_exc())

    def _create_table_es_doc(self, table: Table):
        fqdn = table.fullyQualifiedName.__root__
        table_name = table.name
        suggest = [
            {"input": [fqdn], "weight": 5},
            {"input": [table_name], "weight": 10},
        ]
        column_names = []
        column_descriptions = []
        tags = set()

        timestamp = table.updatedAt.__root__
        tier = None
        for table_tag in table.tags:
            if "Tier" in table_tag.tagFQN.__root__:
                tier = table_tag.tagFQN.__root__
            else:
                tags.add(table_tag.tagFQN.__root__)
        self._parse_columns(
            table.columns, None, column_names, column_descriptions, tags
        )

        database_entity = self.metadata.get_by_id(
            entity=Database, entity_id=str(table.database.id.__root__)
        )
        database_schema_entity = self.metadata.get_by_id(
            entity=DatabaseSchema, entity_id=str(table.databaseSchema.id.__root__)
        )
        service_entity = self.metadata.get_by_id(
            entity=DatabaseService, entity_id=str(database_entity.service.id.__root__)
        )
        table_followers = []
        if table.followers:
            for follower in table.followers.__root__:
                table_followers.append(str(follower.id.__root__))
        table_type = None
        if hasattr(table.tableType, "name"):
            table_type = table.tableType.name
        table_doc = TableESDocument(
            table_id=str(table.id.__root__),
            deleted=table.deleted,
            database=str(database_entity.name.__root__),
            service=service_entity.name.__root__,
            service_type=service_entity.serviceType.name,
            service_category="databaseService",
            name=table.name.__root__,
            suggest=suggest,
            database_schema=str(database_schema_entity.name.__root__),
            description=table.description,
            table_type=table_type,
            last_updated_timestamp=timestamp,
            column_names=column_names,
            column_descriptions=column_descriptions,
            monthly_stats=table.usageSummary.monthlyStats.count,
            monthly_percentile_rank=table.usageSummary.monthlyStats.percentileRank,
            weekly_stats=table.usageSummary.weeklyStats.count,
            weekly_percentile_rank=table.usageSummary.weeklyStats.percentileRank,
            daily_stats=table.usageSummary.dailyStats.count,
            daily_percentile_rank=table.usageSummary.dailyStats.percentileRank,
            tier=tier,
            tags=list(tags),
            fqdn=fqdn,
            owner=table.owner,
            followers=table_followers,
        )
        return table_doc

    def _create_topic_es_doc(self, topic: Topic):
        fqdn = topic.fullyQualifiedName.__root__
        topic_name = topic.name
        suggest = [
            {"input": [fqdn], "weight": 5},
            {"input": [topic_name], "weight": 10},
        ]
        tags = set()
        timestamp = topic.updatedAt.__root__
        service_entity = self.metadata.get_by_id(
            entity=MessagingService, entity_id=str(topic.service.id.__root__)
        )
        topic_followers = []
        if topic.followers:
            for follower in topic.followers.__root__:
                topic_followers.append(str(follower.id.__root__))
        tier = None
        for topic_tag in topic.tags:
            if "Tier" in topic_tag.tagFQN.__root__:
                tier = topic_tag.tagFQN.__root__
            else:
                tags.add(topic_tag.tagFQN.__root__)

        topic_doc = TopicESDocument(
            topic_id=str(topic.id.__root__),
            deleted=topic.deleted,
            service=service_entity.name.__root__,
            service_type=service_entity.serviceType.name,
            service_category="messagingService",
            name=topic.name.__root__,
            suggest=suggest,
            description=topic.description,
            last_updated_timestamp=timestamp,
            tier=tier,
            tags=list(tags),
            fqdn=fqdn,
            owner=topic.owner,
            followers=topic_followers,
        )
        return topic_doc

    def _create_dashboard_es_doc(self, dashboard: Dashboard):
        fqdn = dashboard.fullyQualifiedName.__root__
        suggest = [{"input": [dashboard.displayName], "weight": 10}]
        tags = set()
        timestamp = dashboard.updatedAt.__root__
        service_entity = self.metadata.get_by_id(
            entity=DashboardService, entity_id=str(dashboard.service.id.__root__)
        )
        dashboard_followers = []
        if dashboard.followers:
            for follower in dashboard.followers.__root__:
                dashboard_followers.append(str(follower.id.__root__))
        tier = None
        for dashboard_tag in dashboard.tags:
            if "Tier" in dashboard_tag.tagFQN.__root__:
                tier = dashboard_tag.tagFQN.__root__
            else:
                tags.add(dashboard_tag.tagFQN.__root__)
        charts: List[Chart] = self._get_charts(dashboard.charts)
        chart_names = []
        chart_descriptions = []
        for chart in charts:
            chart_names.append(chart.displayName)
            if chart.description is not None:
                chart_descriptions.append(chart.description)
            if len(chart.tags) > 0:
                for col_tag in chart.tags:
                    tags.add(col_tag.tagFQN.__root__)

        dashboard_doc = DashboardESDocument(
            dashboard_id=str(dashboard.id.__root__),
            deleted=dashboard.deleted,
            service=service_entity.name.__root__,
            service_type=service_entity.serviceType.name,
            service_category="dashboardService",
            name=dashboard.displayName,
            chart_names=chart_names,
            chart_descriptions=chart_descriptions,
            suggest=suggest,
            description=dashboard.description,
            last_updated_timestamp=timestamp,
            tier=tier,
            tags=list(tags),
            fqdn=fqdn,
            owner=dashboard.owner,
            followers=dashboard_followers,
            monthly_stats=dashboard.usageSummary.monthlyStats.count,
            monthly_percentile_rank=dashboard.usageSummary.monthlyStats.percentileRank,
            weekly_stats=dashboard.usageSummary.weeklyStats.count,
            weekly_percentile_rank=dashboard.usageSummary.weeklyStats.percentileRank,
            daily_stats=dashboard.usageSummary.dailyStats.count,
            daily_percentile_rank=dashboard.usageSummary.dailyStats.percentileRank,
        )

        return dashboard_doc

    def _create_pipeline_es_doc(self, pipeline: Pipeline):
        fqdn = pipeline.fullyQualifiedName.__root__
        suggest = [{"input": [pipeline.displayName], "weight": 10}]
        tags = set()
        timestamp = pipeline.updatedAt.__root__
        service_entity = self.metadata.get_by_id(
            entity=PipelineService, entity_id=str(pipeline.service.id.__root__)
        )
        pipeline_followers = []
        if pipeline.followers:
            for follower in pipeline.followers.__root__:
                pipeline_followers.append(str(follower.id.__root__))
        tier = None
        for pipeline_tag in pipeline.tags:
            if "Tier" in pipeline_tag.tagFQN.__root__:
                tier = pipeline_tag.tagFQN.__root__
            else:
                tags.add(pipeline_tag.tagFQN.__root__)
        tasks: List[Task] = pipeline.tasks
        task_names = []
        task_descriptions = []
        for task in tasks:
            task_names.append(task.displayName)
            if task.description:
                task_descriptions.append(task.description)
            if tags in task and len(task.tags) > 0:
                for col_tag in task.tags:
                    tags.add(col_tag.tagFQN)

        pipeline_doc = PipelineESDocument(
            pipeline_id=str(pipeline.id.__root__),
            deleted=pipeline.deleted,
            service=service_entity.name.__root__,
            service_type=service_entity.serviceType.name,
            service_category="pipelineService",
            name=pipeline.displayName,
            task_names=task_names,
            task_descriptions=task_descriptions,
            suggest=suggest,
            description=pipeline.description,
            last_updated_timestamp=timestamp,
            tier=tier,
            tags=list(tags),
            fqdn=fqdn,
            owner=pipeline.owner,
            followers=pipeline_followers,
        )

        return pipeline_doc

    def _create_user_es_doc(self, user: User):
        display_name = user.displayName if user.displayName else user.name.__root__
        suggest = [
            {"input": [display_name], "weight": 5},
            {"input": [user.name], "weight": 10},
        ]
        timestamp = user.updatedAt.__root__
        teams = []
        roles = []
        if user.teams:
            for team in user.teams.__root__:
                teams.append(str(team.id.__root__))

        if user.roles:
            for role in user.roles.__root__:
                roles.append(str(role.id.__root__))

        user_doc = UserESDocument(
            user_id=str(user.id.__root__),
            deleted=user.deleted,
            name=user.name.__root__,
            display_name=display_name,
            email=user.email.__root__,
            suggest=suggest,
            last_updated_timestamp=timestamp,
            teams=list(teams),
            roles=list(roles),
        )

        return user_doc

    def _create_team_es_doc(self, team: Team):
        suggest = [
            {"input": [team.displayName], "weight": 5},
            {"input": [team.name], "weight": 10},
        ]
        timestamp = team.updatedAt.__root__
        users = []
        owns = []
        if team.users:
            for user in team.users.__root__:
                users.append(user)

        if team.owns:
            for own in team.owns.__root__:
                owns.append(str(own.id.__root__))

        team_doc = TeamESDocument(
            team_id=str(team.id.__root__),
            deleted=team.deleted,
            name=team.name.__root__,
            display_name=team.displayName,
            suggest=suggest,
            last_updated_timestamp=timestamp,
            users=list(users),
            owns=list(owns),
        )

        return team_doc

    def _create_glossary_term_es_doc(self, glossary_term: GlossaryTerm):
        suggest = [
            {"input": [glossary_term.displayName], "weight": 5},
            {"input": [glossary_term.name], "weight": 10},
        ]
        timestamp = glossary_term.updatedAt.__root__
        description = glossary_term.description if glossary_term.description else ""
        glossary_term_doc = GlossaryTermESDocument(
            glossary_term_id=str(glossary_term.id.__root__),
            deleted=glossary_term.deleted,
            name=glossary_term.name.__root__,
            display_name=glossary_term.displayName,
            fqdn=glossary_term.fullyQualifiedName,
            description=description,
            glossary_id=str(glossary_term.glossary.id.__root__),
            glossary_name=glossary_term.glossary.name,
            status=glossary_term.status.name,
            suggest=suggest,
            last_updated_timestamp=timestamp,
        )

        return glossary_term_doc

    def _get_charts(self, chart_refs: Optional[List[entityReference.EntityReference]]):
        charts = []
        if chart_refs:
            for chart_ref in chart_refs:
                chart = self.metadata.get_by_id(
                    entity=Chart, entity_id=str(chart_ref.id.__root__), fields=["tags"]
                )
                charts.append(chart)
        return charts

    def _parse_columns(
        self,
        columns: List[Column],
        parent_column,
        column_names,
        column_descriptions,
        tags,
    ):
        for column in columns:
            col_name = (
                parent_column + "." + column.name.__root__
                if parent_column
                else column.name.__root__
            )
            column_names.append(col_name)
            if column.description:
                column_descriptions.append(column.description)
            if len(column.tags) > 0:
                for col_tag in column.tags:
                    tags.add(col_tag.tagFQN.__root__)
            if column.children:
                self._parse_columns(
                    column.children,
                    column.name.__root__,
                    column_names,
                    column_descriptions,
                    tags,
                )

    def get_status(self):
        return self.status

    def close(self):
        self.elasticsearch_client.close()
