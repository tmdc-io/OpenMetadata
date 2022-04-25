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

import classNames from 'classnames';
import { compare } from 'fast-json-patch';
import { EntityTags, TagOption } from 'Models';
import React, { RefObject, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuthContext } from '../../authentication/auth-provider/AuthProvider';
import { FQN_SEPARATOR_CHAR } from '../../constants/char.constants';
import { getTeamAndUserDetailsPath } from '../../constants/constants';
import { observerOptions } from '../../constants/Mydata.constants';
import { EntityType } from '../../enums/entity.enum';
import { Dashboard } from '../../generated/entity/data/dashboard';
import { Operation } from '../../generated/entity/policies/accessControl/rule';
import { EntityReference } from '../../generated/type/entityReference';
import { Paging } from '../../generated/type/paging';
import { LabelType, State, TagLabel } from '../../generated/type/tagLabel';
import { useInfiniteScroll } from '../../hooks/useInfiniteScroll';
import {
  getCurrentUserId,
  getEntityName,
  getEntityPlaceHolder,
  getHtmlForNonAdminAction,
  getUserTeams,
  isEven,
  pluralize,
} from '../../utils/CommonUtils';
import { getEntityFeedLink } from '../../utils/EntityUtils';
import { getDefaultValue } from '../../utils/FeedElementUtils';
import { getEntityFieldThreadCounts } from '../../utils/FeedUtils';
import {
  fetchGlossaryTerms,
  getGlossaryTermlist,
} from '../../utils/GlossaryUtils';
import SVGIcons from '../../utils/SvgUtils';
import { getTagsWithoutTier } from '../../utils/TableUtils';
import { getTagCategories, getTaglist } from '../../utils/TagsUtils';
import ActivityFeedList from '../ActivityFeed/ActivityFeedList/ActivityFeedList';
import ActivityThreadPanel from '../ActivityFeed/ActivityThreadPanel/ActivityThreadPanel';
import Description from '../common/description/Description';
import EntityPageInfo from '../common/entityPageInfo/EntityPageInfo';
import NonAdminAction from '../common/non-admin-action/NonAdminAction';
import RichTextEditorPreviewer from '../common/rich-text-editor/RichTextEditorPreviewer';
import TabsPane from '../common/TabsPane/TabsPane';
import PageContainer from '../containers/PageContainer';
import Entitylineage from '../EntityLineage/EntityLineage.component';
import Loader from '../Loader/Loader';
import ManageTabComponent from '../ManageTab/ManageTab.component';
import { ModalWithMarkdownEditor } from '../Modals/ModalWithMarkdownEditor/ModalWithMarkdownEditor';
import RequestDescriptionModal from '../Modals/RequestDescriptionModal/RequestDescriptionModal';
import TagsContainer from '../tags-container/tags-container';
import TagsViewer from '../tags-viewer/tags-viewer';
import Tags from '../tags/tags';
import { ChartType, DashboardDetailsProps } from './DashboardDetails.interface';

const DashboardDetails = ({
  entityName,
  followers,
  followDashboardHandler,
  unfollowDashboardHandler,
  owner,
  tier,
  slashedDashboardName,
  activeTab,
  setActiveTabHandler,
  description,
  serviceType,
  dashboardUrl,
  dashboardTags,
  dashboardDetails,
  users,
  descriptionUpdateHandler,
  settingsUpdateHandler,
  tagUpdateHandler,
  charts,
  chartDescriptionUpdateHandler,
  chartTagUpdateHandler,
  entityLineage,
  isNodeLoading,
  lineageLeafNodes,
  loadNodeHandler,
  versionHandler,
  version,
  deleted,
  addLineageHandler,
  removeLineageHandler,
  entityLineageHandler,
  isLineageLoading,
  entityThread,
  isentityThreadLoading,
  postFeedHandler,
  feedCount,
  entityFieldThreadCount,
  createThread,
  dashboardFQN,
  deletePostHandler,
  paging,
  fetchFeedHandler,
}: DashboardDetailsProps) => {
  const { isAuthDisabled } = useAuthContext();
  const [isEdit, setIsEdit] = useState(false);
  const [followersCount, setFollowersCount] = useState(0);
  const [isFollowing, setIsFollowing] = useState(false);
  const [editChart, setEditChart] = useState<{
    chart: ChartType;
    index: number;
  }>();
  const [editChartTags, setEditChartTags] = useState<{
    chart: ChartType;
    index: number;
  }>();
  const [tagList, setTagList] = useState<Array<TagOption>>([]);
  const [tagFetchFailed, setTagFetchFailed] = useState<boolean>(false);
  const [isTagLoading, setIsTagLoading] = useState<boolean>(false);
  const [threadLink, setThreadLink] = useState<string>('');
  const [selectedField, setSelectedField] = useState<string>('');
  const [elementRef, isInView] = useInfiniteScroll(observerOptions);

  const onEntityFieldSelect = (value: string) => {
    setSelectedField(value);
  };
  const closeRequestModal = () => {
    setSelectedField('');
  };
  const hasEditAccess = () => {
    if (owner?.type === 'user') {
      return owner.id === getCurrentUserId();
    } else {
      return getUserTeams().some((team) => team.id === owner?.id);
    }
  };
  const setFollowersData = (followers: Array<EntityReference>) => {
    setIsFollowing(
      followers.some(({ id }: { id: string }) => id === getCurrentUserId())
    );
    setFollowersCount(followers?.length);
  };
  const tabs = [
    {
      name: 'Details',
      icon: {
        alt: 'schema',
        name: 'icon-schema',
        title: 'Details',
        selectedName: 'icon-schemacolor',
      },
      isProtected: false,
      position: 1,
    },
    {
      name: 'Activity Feed',
      icon: {
        alt: 'activity_feed',
        name: 'activity_feed',
        title: 'Activity Feed',
        selectedName: 'activity-feed-color',
      },
      isProtected: false,
      position: 2,
      count: feedCount,
    },
    {
      name: 'Lineage',
      icon: {
        alt: 'lineage',
        name: 'icon-lineage',
        title: 'Lineage',
        selectedName: 'icon-lineagecolor',
      },
      isProtected: false,
      position: 3,
    },
    {
      name: 'Manage',
      icon: {
        alt: 'manage',
        name: 'icon-manage',
        title: 'Manage',
        selectedName: 'icon-managecolor',
      },
      isProtected: true,
      isHidden: deleted,
      protectedState: !owner || hasEditAccess(),
      position: 4,
    },
  ];

  const extraInfo = [
    {
      key: 'Owner',
      value:
        owner?.type === 'team'
          ? getTeamAndUserDetailsPath(owner?.name || '')
          : getEntityName(owner),
      placeholderText: getEntityPlaceHolder(
        getEntityName(owner),
        owner?.deleted
      ),
      isLink: owner?.type === 'team',
      openInNewTab: false,
    },
    {
      key: 'Tier',
      value: tier?.tagFQN ? tier.tagFQN.split(FQN_SEPARATOR_CHAR)[1] : '',
    },
    {
      key: `${serviceType} Url`,
      value: dashboardUrl,
      placeholderText: entityName,
      isLink: true,
      openInNewTab: true,
    },
  ];

  const onDescriptionEdit = (): void => {
    setIsEdit(true);
  };
  const onCancel = () => {
    setIsEdit(false);
  };

  const onDescriptionUpdate = (updatedHTML: string) => {
    if (description !== updatedHTML) {
      const updatedDashboardDetails = {
        ...dashboardDetails,
        description: updatedHTML,
      };
      descriptionUpdateHandler(updatedDashboardDetails);
      setIsEdit(false);
    } else {
      setIsEdit(false);
    }
  };

  const onSettingsUpdate = (
    newOwner?: Dashboard['owner'],
    newTier?: string
  ) => {
    if (newOwner || newTier) {
      const tierTag: Dashboard['tags'] = newTier
        ? [
            ...getTagsWithoutTier(dashboardDetails.tags as Array<EntityTags>),
            {
              tagFQN: newTier,
              labelType: LabelType.Manual,
              state: State.Confirmed,
            },
          ]
        : dashboardDetails.tags;
      const updatedDashboardDetails = {
        ...dashboardDetails,
        owner: newOwner ? newOwner : dashboardDetails.owner,
        tags: tierTag,
      };

      return settingsUpdateHandler(updatedDashboardDetails);
    } else {
      return Promise.reject();
    }
  };

  const onTagUpdate = (selectedTags?: Array<EntityTags>) => {
    if (selectedTags) {
      const updatedTags = [...(tier ? [tier] : []), ...selectedTags];
      const updatedDashboard = { ...dashboardDetails, tags: updatedTags };
      tagUpdateHandler(updatedDashboard);
    }
  };
  const followDashboard = () => {
    if (isFollowing) {
      setFollowersCount((preValu) => preValu - 1);
      setIsFollowing(false);
      unfollowDashboardHandler();
    } else {
      setFollowersCount((preValu) => preValu + 1);
      setIsFollowing(true);
      followDashboardHandler();
    }
  };
  const handleUpdateChart = (chart: ChartType, index: number) => {
    setEditChart({ chart, index });
  };
  const handleEditChartTag = (chart: ChartType, index: number): void => {
    setEditChartTags({ chart, index });
  };

  const closeEditChartModal = (): void => {
    setEditChart(undefined);
  };
  const onChartUpdate = (chartDescription: string) => {
    if (editChart) {
      const updatedChart = {
        ...editChart.chart,
        description: chartDescription,
      };
      const jsonPatch = compare(charts[editChart.index], updatedChart);
      chartDescriptionUpdateHandler(
        editChart.index,
        editChart.chart.id,
        jsonPatch
      );
      setEditChart(undefined);
    } else {
      setEditChart(undefined);
    }
  };

  const handleChartTagSelection = (selectedTags?: Array<EntityTags>) => {
    if (selectedTags && editChartTags) {
      const prevTags = editChartTags.chart.tags?.filter((tag) =>
        selectedTags.some((selectedTag) => selectedTag.tagFQN === tag.tagFQN)
      );
      const newTags = selectedTags
        .filter(
          (selectedTag) =>
            !editChartTags.chart.tags?.some(
              (tag) => tag.tagFQN === selectedTag.tagFQN
            )
        )
        .map((tag) => ({
          labelType: 'Manual',
          state: 'Confirmed',
          source: tag.source,
          tagFQN: tag.tagFQN,
        }));

      const updatedChart = {
        ...editChartTags.chart,
        tags: [...(prevTags as TagLabel[]), ...newTags],
      };
      const jsonPatch = compare(charts[editChartTags.index], updatedChart);
      chartTagUpdateHandler(
        editChartTags.index,
        editChartTags.chart.id,
        jsonPatch
      );
      setEditChartTags(undefined);
    } else {
      setEditChartTags(undefined);
    }
  };

  const fetchTagsAndGlossaryTerms = () => {
    setIsTagLoading(true);
    Promise.all([getTagCategories(), fetchGlossaryTerms()])
      .then((values) => {
        let tagsAndTerms: TagOption[] = [];
        if (values[0].data) {
          tagsAndTerms = getTaglist(values[0].data).map((tag) => {
            return { fqn: tag, source: 'Tag' };
          });
        }
        if (values[1] && values[1].length > 0) {
          const glossaryTerms: TagOption[] = getGlossaryTermlist(values[1]).map(
            (tag) => {
              return { fqn: tag, source: 'Glossary' };
            }
          );
          tagsAndTerms = [...tagsAndTerms, ...glossaryTerms];
        }
        setTagList(tagsAndTerms);
        setTagFetchFailed(false);
      })
      .catch(() => {
        setTagList([]);
        setTagFetchFailed(true);
      })
      .finally(() => {
        setIsTagLoading(false);
      });
  };

  const onThreadLinkSelect = (link: string) => {
    setThreadLink(link);
  };

  const onThreadPanelClose = () => {
    setThreadLink('');
  };

  const getDeleteEntityMessage = () => {
    return `Deleting this ${EntityType.DASHBOARD} will also delete ${pluralize(
      charts.length,
      'chart',
      's'
    )}`;
  };

  const getLoader = () => {
    return isentityThreadLoading ? <Loader /> : null;
  };

  const fetchMoreThread = (
    isElementInView: boolean,
    pagingObj: Paging,
    isLoading: boolean
  ) => {
    if (isElementInView && pagingObj?.after && !isLoading) {
      fetchFeedHandler(pagingObj.after);
    }
  };

  useEffect(() => {
    if (isAuthDisabled && users.length && followers.length) {
      setFollowersData(followers);
    }
  }, [users, followers]);

  useEffect(() => {
    setFollowersData(followers);
  }, [followers]);

  useEffect(() => {
    fetchMoreThread(isInView as boolean, paging, isentityThreadLoading);
  }, [paging, isentityThreadLoading, isInView]);

  return (
    <PageContainer>
      <div className="tw-px-6 tw-w-full tw-h-full tw-flex tw-flex-col">
        <EntityPageInfo
          isTagEditable
          deleted={deleted}
          entityFieldThreads={getEntityFieldThreadCounts(
            'tags',
            entityFieldThreadCount
          )}
          entityFqn={dashboardFQN}
          entityName={entityName}
          entityType={EntityType.DASHBOARD}
          extraInfo={extraInfo}
          followHandler={followDashboard}
          followers={followersCount}
          followersList={followers}
          hasEditAccess={hasEditAccess()}
          isFollowing={isFollowing}
          owner={owner}
          tags={dashboardTags}
          tagsHandler={onTagUpdate}
          tier={tier || ''}
          titleLinks={slashedDashboardName}
          version={version}
          versionHandler={versionHandler}
          onThreadLinkSelect={onThreadLinkSelect}
        />
        <div className="tw-mt-4 tw-flex tw-flex-col tw-flex-grow">
          <TabsPane
            activeTab={activeTab}
            className="tw-flex-initial"
            setActiveTab={setActiveTabHandler}
            tabs={tabs}
          />

          <div className="tw-bg-white tw-flex-grow tw--mx-6 tw-px-7 tw-py-4">
            {activeTab === 1 && (
              <>
                <div className="tw-grid tw-grid-cols-4 tw-gap-4 tw-w-full">
                  <div className="tw-col-span-full">
                    <Description
                      description={description}
                      entityFieldThreads={getEntityFieldThreadCounts(
                        'description',
                        entityFieldThreadCount
                      )}
                      entityFqn={dashboardFQN}
                      entityName={entityName}
                      entityType={EntityType.DASHBOARD}
                      hasEditAccess={hasEditAccess()}
                      isEdit={isEdit}
                      isReadOnly={deleted}
                      owner={owner}
                      onCancel={onCancel}
                      onDescriptionEdit={onDescriptionEdit}
                      onDescriptionUpdate={onDescriptionUpdate}
                      onEntityFieldSelect={onEntityFieldSelect}
                      onThreadLinkSelect={onThreadLinkSelect}
                    />
                  </div>
                </div>
                <div className="tw-table-responsive tw-my-6">
                  <table className="tw-w-full" data-testid="charts-table">
                    <thead>
                      <tr className="tableHead-row">
                        <th className="tableHead-cell">Chart Name</th>
                        <th className="tableHead-cell">Chart Type</th>
                        <th className="tableHead-cell">Description</th>
                        <th className="tableHead-cell tw-w-60">Tags</th>
                      </tr>
                    </thead>
                    <tbody className="tableBody">
                      {charts.map((chart, index) => (
                        <tr
                          className={classNames(
                            'tableBody-row',
                            !isEven(index + 1) ? 'odd-row' : null
                          )}
                          key={index}>
                          <td className="tableBody-cell">
                            <Link
                              target="_blank"
                              to={{ pathname: chart.chartUrl }}>
                              <span className="tw-flex">
                                <span className="tw-mr-1">
                                  {chart.displayName}
                                </span>
                                <SVGIcons
                                  alt="external-link"
                                  className="tw-align-middle"
                                  icon="external-link"
                                  width="12px"
                                />
                              </span>
                            </Link>
                          </td>
                          <td className="tableBody-cell">{chart.chartType}</td>
                          <td className="tw-group tableBody-cell tw-relative">
                            <div className="tw-inline-block">
                              <div
                                className="tw-cursor-pointer tw-flex"
                                data-testid="description">
                                <div>
                                  {chart.description ? (
                                    <RichTextEditorPreviewer
                                      markdown={chart.description}
                                    />
                                  ) : (
                                    <span className="tw-no-description">
                                      No description
                                    </span>
                                  )}
                                </div>
                                {!deleted && (
                                  <NonAdminAction
                                    html={getHtmlForNonAdminAction(
                                      Boolean(owner)
                                    )}
                                    isOwner={hasEditAccess()}
                                    permission={Operation.UpdateDescription}
                                    position="top">
                                    <button
                                      className="tw-self-start tw-w-8 tw-h-auto tw-opacity-0 tw-ml-1 group-hover:tw-opacity-100 focus:tw-outline-none"
                                      onClick={() =>
                                        handleUpdateChart(chart, index)
                                      }>
                                      <SVGIcons
                                        alt="edit"
                                        icon="icon-edit"
                                        title="Edit"
                                        width="10px"
                                      />
                                    </button>
                                  </NonAdminAction>
                                )}
                              </div>
                            </div>
                          </td>
                          <td
                            className="tw-group tw-relative tableBody-cell"
                            onClick={() => {
                              if (!editChartTags) {
                                // Fetch tags and terms only once
                                if (tagList.length === 0 || tagFetchFailed) {
                                  fetchTagsAndGlossaryTerms();
                                }
                                handleEditChartTag(chart, index);
                              }
                            }}>
                            {deleted ? (
                              <div className="tw-flex tw-flex-wrap">
                                <TagsViewer
                                  sizeCap={-1}
                                  tags={chart.tags || []}
                                />
                              </div>
                            ) : (
                              <NonAdminAction
                                html={getHtmlForNonAdminAction(Boolean(owner))}
                                isOwner={hasEditAccess()}
                                permission={Operation.UpdateTags}
                                position="left"
                                trigger="click">
                                <TagsContainer
                                  editable={editChartTags?.index === index}
                                  isLoading={
                                    isTagLoading &&
                                    editChartTags?.index === index
                                  }
                                  selectedTags={chart.tags as EntityTags[]}
                                  size="small"
                                  tagList={tagList}
                                  type="label"
                                  onCancel={() => {
                                    handleChartTagSelection();
                                  }}
                                  onSelectionChange={(tags) => {
                                    handleChartTagSelection(tags);
                                  }}>
                                  {chart.tags?.length ? (
                                    <button
                                      className="tw-opacity-0 tw-ml-1 group-hover:tw-opacity-100 focus:tw-outline-none"
                                      data-testid="edit-tags">
                                      <SVGIcons
                                        alt="edit"
                                        icon="icon-edit"
                                        title="Edit"
                                        width="10px"
                                      />
                                    </button>
                                  ) : (
                                    <span className="tw-opacity-60 group-hover:tw-opacity-100 tw-text-grey-muted group-hover:tw-text-primary">
                                      <Tags
                                        startWith="+ "
                                        tag="Add tag"
                                        type="outlined"
                                      />
                                    </span>
                                  )}
                                </TagsContainer>
                              </NonAdminAction>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </>
            )}
            {activeTab === 2 && (
              <div
                className="tw-py-4 tw-px-7 tw-grid tw-grid-cols-3 entity-feed-list tw--mx-7 tw--my-4"
                id="activityfeed">
                <div />
                <ActivityFeedList
                  isEntityFeed
                  withSidePanel
                  className=""
                  deletePostHandler={deletePostHandler}
                  entityName={entityName}
                  feedList={entityThread}
                  postFeedHandler={postFeedHandler}
                />
                <div />
              </div>
            )}
            {activeTab === 3 && (
              <div className="tw-h-full">
                <Entitylineage
                  addLineageHandler={addLineageHandler}
                  deleted={deleted}
                  entityLineage={entityLineage}
                  entityLineageHandler={entityLineageHandler}
                  isLoading={isLineageLoading}
                  isNodeLoading={isNodeLoading}
                  isOwner={hasEditAccess()}
                  lineageLeafNodes={lineageLeafNodes}
                  loadNodeHandler={loadNodeHandler}
                  removeLineageHandler={removeLineageHandler}
                />
              </div>
            )}
            {activeTab === 4 && !deleted && (
              <div>
                <ManageTabComponent
                  allowDelete
                  currentTier={tier?.tagFQN}
                  currentUser={owner?.id}
                  deletEntityMessage={getDeleteEntityMessage()}
                  entityId={dashboardDetails.id}
                  entityName={dashboardDetails.name}
                  entityType={EntityType.DASHBOARD}
                  hasEditAccess={hasEditAccess()}
                  manageSectionType={EntityType.DASHBOARD}
                  onSave={onSettingsUpdate}
                />
              </div>
            )}
            <div
              data-testid="observer-element"
              id="observer-element"
              ref={elementRef as RefObject<HTMLDivElement>}>
              {getLoader()}
            </div>
          </div>
        </div>
      </div>
      {editChart && (
        <ModalWithMarkdownEditor
          header={`Edit Chart: "${editChart.chart.displayName}"`}
          placeholder="Enter Chart Description"
          value={editChart.chart.description || ''}
          onCancel={closeEditChartModal}
          onSave={onChartUpdate}
        />
      )}
      {threadLink ? (
        <ActivityThreadPanel
          createThread={createThread}
          deletePostHandler={deletePostHandler}
          open={Boolean(threadLink)}
          postFeedHandler={postFeedHandler}
          threadLink={threadLink}
          onCancel={onThreadPanelClose}
        />
      ) : null}
      {selectedField ? (
        <RequestDescriptionModal
          createThread={createThread}
          defaultValue={getDefaultValue(owner as EntityReference)}
          header="Request description"
          threadLink={getEntityFeedLink(
            EntityType.DASHBOARD,
            dashboardFQN,
            selectedField
          )}
          onCancel={closeRequestModal}
        />
      ) : null}
    </PageContainer>
  );
};

export default DashboardDetails;
