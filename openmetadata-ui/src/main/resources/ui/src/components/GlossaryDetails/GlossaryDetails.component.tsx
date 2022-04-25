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

import { AxiosError } from 'axios';
import classNames from 'classnames';
import { cloneDeep, includes, isEqual } from 'lodash';
import { EntityTags, FormattedUsersData } from 'Models';
import React, { useEffect, useState } from 'react';
import {
  TITLE_FOR_NON_ADMIN_ACTION,
  TITLE_FOR_NON_OWNER_ACTION,
} from '../../constants/constants';
import { EntityType } from '../../enums/entity.enum';
import { Glossary } from '../../generated/entity/data/glossary';
import { Operation } from '../../generated/entity/policies/policy';
import { LabelType, Source, State } from '../../generated/type/tagLabel';
import jsonData from '../../jsons/en';
import UserCard from '../../pages/teams/UserCard';
import { getEntityName, hasEditAccess } from '../../utils/CommonUtils';
import SVGIcons from '../../utils/SvgUtils';
import {
  getTagCategories,
  getTaglist,
  getTagOptionsFromFQN,
} from '../../utils/TagsUtils';
import { showErrorToast } from '../../utils/ToastUtils';
import { Button } from '../buttons/Button/Button';
import Avatar from '../common/avatar/Avatar';
import Description from '../common/description/Description';
import NonAdminAction from '../common/non-admin-action/NonAdminAction';
import TabsPane from '../common/TabsPane/TabsPane';
import ManageTabComponent from '../ManageTab/ManageTab.component';
import ReviewerModal from '../Modals/ReviewerModal/ReviewerModal.component';
import TagsContainer from '../tags-container/tags-container';
import TagsViewer from '../tags-viewer/tags-viewer';
import Tags from '../tags/tags';

type props = {
  isHasAccess: boolean;
  glossary: Glossary;
  updateGlossary: (value: Glossary) => void;
  afterDeleteAction?: () => void;
  handleUserRedirection?: (name: string) => void;
};

const GlossaryDetails = ({
  isHasAccess,
  glossary,
  updateGlossary,
  afterDeleteAction,
  handleUserRedirection,
}: props) => {
  const [activeTab, setActiveTab] = useState(1);
  const [isDescriptionEditable, setIsDescriptionEditable] = useState(false);
  const [isTagEditable, setIsTagEditable] = useState<boolean>(false);
  const [tagList, setTagList] = useState<Array<string>>([]);
  const [isTagLoading, setIsTagLoading] = useState<boolean>(false);

  const [showRevieweModal, setShowRevieweModal] = useState(false);
  const [reviewer, setReviewer] = useState<Array<FormattedUsersData>>([]);

  const tabs = [
    {
      name: 'Manage',
      icon: {
        alt: 'manage',
        name: 'icon-manage',
        title: 'Manage',
        selectedName: 'icon-managecolor',
      },
      isProtected: false,
      position: 1,
    },
  ];

  const onReviewerModalCancel = () => {
    setShowRevieweModal(false);
  };

  const handleReviewerSave = (data: Array<FormattedUsersData>) => {
    if (!isEqual(data, reviewer)) {
      let updatedGlossary = cloneDeep(glossary);
      const oldReviewer = data.filter((d) => includes(reviewer, d));
      const newReviewer = data
        .filter((d) => !includes(reviewer, d))
        .map((d) => ({ id: d.id, type: d.type }));
      updatedGlossary = {
        ...updatedGlossary,
        reviewers: [...oldReviewer, ...newReviewer],
      };
      setReviewer(data);
      updateGlossary(updatedGlossary);
    }
    onReviewerModalCancel();
  };

  const onTagUpdate = (selectedTags?: Array<string>) => {
    if (selectedTags) {
      const prevTags =
        glossary?.tags?.filter((tag) =>
          selectedTags.includes(tag?.tagFQN as string)
        ) || [];
      const newTags = selectedTags
        .filter((tag) => {
          return !prevTags?.map((prevTag) => prevTag.tagFQN).includes(tag);
        })
        .map((tag) => ({
          labelType: LabelType.Manual,
          state: State.Confirmed,
          source: Source.Tag,
          tagFQN: tag,
        }));
      const updatedTags = [...prevTags, ...newTags];
      const updatedGlossary = { ...glossary, tags: updatedTags };
      updateGlossary(updatedGlossary);
    }
  };
  const handleTagSelection = (selectedTags?: Array<EntityTags>) => {
    onTagUpdate?.(selectedTags?.map((tag) => tag.tagFQN));
    setIsTagEditable(false);
  };

  const onDescriptionEdit = (): void => {
    setIsDescriptionEditable(true);
  };
  const onCancel = () => {
    setIsDescriptionEditable(false);
  };

  const getSelectedTags = () => {
    return (glossary.tags || []).map((tag) => ({
      tagFQN: tag.tagFQN,
      isRemovable: true,
    }));
  };

  const fetchTags = () => {
    setIsTagLoading(true);
    getTagCategories()
      .then((res) => {
        setTagList(getTaglist(res.data));
      })
      .catch((err: AxiosError) => {
        showErrorToast(err, jsonData['api-error-messages']['fetch-tags-error']);
      })
      .finally(() => {
        setIsTagLoading(false);
      });
  };

  const onDescriptionUpdate = (updatedHTML: string) => {
    if (glossary.description !== updatedHTML) {
      const updatedTableDetails = {
        ...glossary,
        description: updatedHTML,
      };
      updateGlossary(updatedTableDetails);
      setIsDescriptionEditable(false);
    } else {
      setIsDescriptionEditable(false);
    }
  };

  const handleRemoveReviewer = (id: string) => {
    let updatedGlossary = cloneDeep(glossary);
    const reviewer = updatedGlossary.reviewers?.filter(
      (glossary) => glossary.id !== id
    );
    updatedGlossary = {
      ...updatedGlossary,
      reviewers: reviewer,
    };

    updateGlossary(updatedGlossary);
  };

  const setActiveTabHandler = (value: number) => {
    setActiveTab(value);
  };

  const handleUpdateOwner = (owner: Glossary['owner']) => {
    const updatedData = {
      ...glossary,
      owner,
    };

    return new Promise<void>((_, reject) => {
      updateGlossary(updatedData);
      setTimeout(() => {
        reject();
      }, 500);
    });
  };

  useEffect(() => {
    if (glossary.reviewers && glossary.reviewers.length) {
      setReviewer(
        glossary.reviewers.map((d) => ({
          ...(d as FormattedUsersData),
          type: 'user',
        }))
      );
    } else {
      setReviewer([]);
    }
  }, [glossary.reviewers]);

  const AddReviewerButton = () => {
    return (
      <NonAdminAction position="bottom" title={TITLE_FOR_NON_ADMIN_ACTION}>
        <Button
          className={classNames('tw-h-8 tw-mr-1 tw-rounded', {
            'tw-opacity-40': isHasAccess,
          })}
          data-testid="add-new-reviewer"
          size="small"
          theme="primary"
          variant="outlined"
          onClick={() => setShowRevieweModal(true)}>
          Add Reviewer
        </Button>
      </NonAdminAction>
    );
  };

  const getReviewerTabData = () => {
    return (
      <div className="tw-border tw-border-main tw-rounded tw-mt-3 tw-shadow tw-px-5">
        <div className="tw-flex tw-justify-between tw-items-center tw-py-3">
          <div className="tw-w-10/12">
            <p className="tw-text-sm tw-mb-1 tw-font-medium">Reviewers</p>
            <p className="tw-text-grey-muted tw-text-xs">
              Add users as reviewer
            </p>
          </div>

          {AddReviewerButton()}
        </div>
        {glossary.reviewers && glossary.reviewers.length > 0 && (
          <div className="tw-grid xxl:tw-grid-cols-3 md:tw-grid-cols-2 tw-border-t tw-gap-4 tw-py-3">
            {glossary.reviewers?.map((term) => (
              <UserCard
                isActionVisible
                isIconVisible
                item={{
                  fqn: term.fullyQualifiedName || '',
                  displayName: term.displayName || term.name || '',
                  id: term.id,
                  type: term.type,
                  name: term.name,
                }}
                key={term.name}
                onRemove={handleRemoveReviewer}
                onTitleClick={handleUserRedirection}
              />
            ))}
          </div>
        )}
      </div>
    );
  };

  return (
    <div
      className="tw-w-full tw-h-full tw-flex tw-flex-col"
      data-testid="glossary-details">
      <div className="tw-mb-3 tw-flex tw-items-center">
        {glossary.owner && getEntityName(glossary.owner) && (
          <div className="tw-inline-block tw-mr-2">
            <Avatar
              name={getEntityName(glossary.owner)}
              textClass="tw-text-xs"
              width="20"
            />
          </div>
        )}
        {glossary.owner && getEntityName(glossary.owner) ? (
          <span>{getEntityName(glossary.owner)}</span>
        ) : (
          <span className="tw-text-grey-muted">No owner</span>
        )}
      </div>

      <div className="tw-flex tw-flex-wrap tw-group" data-testid="tags">
        {!isTagEditable && (
          <>
            {glossary?.tags && glossary.tags.length > 0 && (
              <>
                <SVGIcons
                  alt="icon-tag"
                  className="tw-mx-1"
                  icon="icon-tag-grey"
                  width="16"
                />
                <TagsViewer tags={glossary.tags} />
              </>
            )}
          </>
        )}
        <NonAdminAction
          isOwner={Boolean(glossary.owner)}
          permission={Operation.UpdateTags}
          position="bottom"
          title={TITLE_FOR_NON_OWNER_ACTION}
          trigger="click">
          <div
            className="tw-inline-block"
            onClick={() => {
              fetchTags();
              setIsTagEditable(true);
            }}>
            <TagsContainer
              dropDownHorzPosRight={false}
              editable={isTagEditable}
              isLoading={isTagLoading}
              selectedTags={getSelectedTags()}
              showTags={false}
              size="small"
              tagList={getTagOptionsFromFQN(tagList)}
              type="label"
              onCancel={() => {
                handleTagSelection();
              }}
              onSelectionChange={(tags) => {
                handleTagSelection(tags);
              }}>
              {glossary?.tags && glossary?.tags.length ? (
                <button className=" tw-ml-1 focus:tw-outline-none">
                  <SVGIcons
                    alt="edit"
                    icon="icon-edit"
                    title="Edit"
                    width="12px"
                  />
                </button>
              ) : (
                <span>
                  <Tags
                    className="tw-text-primary"
                    startWith="+ "
                    tag="Add tag"
                    type="label"
                  />
                </span>
              )}
            </TagsContainer>
          </div>
        </NonAdminAction>
      </div>

      <div className="tw--ml-5" data-testid="description-container">
        <Description
          blurWithBodyBG
          removeBlur
          description={glossary?.description}
          entityName={glossary?.displayName ?? glossary?.name}
          isEdit={isDescriptionEditable}
          onCancel={onCancel}
          onDescriptionEdit={onDescriptionEdit}
          onDescriptionUpdate={onDescriptionUpdate}
        />
      </div>

      <div className="tw-flex tw-flex-col tw-flex-grow">
        <TabsPane
          activeTab={activeTab}
          className="tw-flex-initial"
          setActiveTab={setActiveTabHandler}
          tabs={tabs}
        />

        <div className="tw-flex-grow tw--mx-6 tw-px-7 tw-py-4">
          {activeTab === 1 && (
            <div
              className="tw-bg-white tw-shadow-md tw-py-6 tw-flex-grow"
              data-testid="manage-glossary">
              <div className="tw-max-w-3xl tw-mx-auto">
                {getReviewerTabData()}
              </div>
              <div className="tw-mt-7">
                <ManageTabComponent
                  allowDelete
                  hideTier
                  isRecursiveDelete
                  afterDeleteAction={afterDeleteAction}
                  currentUser={glossary?.owner?.id}
                  entityId={glossary.id}
                  entityName={glossary?.name}
                  entityType={EntityType.GLOSSARY}
                  hasEditAccess={hasEditAccess(
                    glossary?.owner?.type || '',
                    glossary?.owner?.id || ''
                  )}
                  onSave={handleUpdateOwner}
                />
              </div>
            </div>
          )}
        </div>

        {showRevieweModal && (
          <ReviewerModal
            header="Add Reviewer"
            reviewer={reviewer}
            onCancel={onReviewerModalCancel}
            onSave={handleReviewerSave}
          />
        )}
      </div>
    </div>
  );
};

export default GlossaryDetails;
