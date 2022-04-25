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

import { AxiosResponse } from 'axios';
import classNames from 'classnames';
import { isUndefined } from 'lodash';
import React, { FC, Fragment, useState } from 'react';
import { Link, useHistory } from 'react-router-dom';
import AppState from '../../../AppState';
import { getUserByName } from '../../../axiosAPIs/userAPI';
import { FQN_SEPARATOR_CHAR } from '../../../constants/char.constants';
import { getUserPath, TERM_ADMIN } from '../../../constants/constants';
import {
  EntityType,
  FqnPart,
  TabSpecificField,
} from '../../../enums/entity.enum';
import { User } from '../../../generated/entity/teams/user';
import { EntityReference } from '../../../generated/type/entityReference';
import {
  getEntityName,
  getNonDeletedTeams,
  getPartialNameFromFQN,
  getPartialNameFromTableFQN,
} from '../../../utils/CommonUtils';
import SVGIcons, { Icons } from '../../../utils/SvgUtils';
import { getEntityLink } from '../../../utils/TableUtils';
import { getDayTimeByTimeStamp } from '../../../utils/TimeUtils';
import { Button } from '../../buttons/Button/Button';
import Avatar from '../../common/avatar/Avatar';
import PopOver from '../../common/popover/PopOver';
import Loader from '../../Loader/Loader';
import { FeedHeaderProp } from '../ActivityFeedCard/ActivityFeedCard.interface';
import './FeedCardHeader.style.css';

const FeedCardHeader: FC<FeedHeaderProp> = ({
  className,
  createdBy,
  timeStamp,
  entityFQN,
  entityType,
  entityField,
  isEntityFeed,
}) => {
  const history = useHistory();
  const [userData, setUserData] = useState<User>({} as User);
  const [isLoading, setIsLoading] = useState(true);
  const [isError, setIsError] = useState(false);

  const onClickHandler = () => {
    getUserByName(createdBy, 'profile,roles,teams,follows,owns')
      .then((res: AxiosResponse) => {
        setUserData(res.data);
      })
      .catch(() => {
        setIsError(true);
      })
      .finally(() => setIsLoading(false));
  };

  const onTitleClickHandler = (path: string) => {
    history.push(path);
  };

  const getUserData = () => {
    const name = userData.name ?? '';
    const displayName = getEntityName(userData as unknown as EntityReference);
    const teams = getNonDeletedTeams(userData.teams ?? []);
    const roles = userData.roles;
    const isAdmin = userData?.isAdmin;

    return (
      <Fragment>
        {isError ? (
          <p>Error while getting user data.</p>
        ) : (
          <div>
            {isLoading ? (
              <Loader size="small" />
            ) : (
              <div>
                <div className="tw-flex">
                  <div className="tw-mr-2">
                    <Avatar name={createdBy} type="square" width="30" />
                  </div>
                  <div className="tw-self-center">
                    <Button
                      style={{ padding: '0px' }}
                      theme="primary"
                      variant="link"
                      onClick={() => onTitleClickHandler(getUserPath(name))}>
                      <span className="tw-font-medium tw-mr-2">
                        {displayName}
                      </span>
                    </Button>
                    {displayName !== name ? (
                      <span className="tw-text-grey-muted">{name}</span>
                    ) : null}
                  </div>
                </div>
                <div className="tw-text-left">
                  {teams?.length || roles?.length ? (
                    <hr className="tw-my-2 tw--mx-3" />
                  ) : null}
                  {teams?.length ? (
                    <p className="tw-mt-2">
                      <SVGIcons
                        alt="icon"
                        className="tw-w-4"
                        icon={Icons.TEAMS_GREY}
                      />
                      <span className="tw-mr-2 tw-ml-1 tw-align-middle tw-font-medium">
                        Teams
                      </span>
                      <span className="tw-flex tw-flex-wrap tw-mt-1">
                        {teams.map((team, i) => (
                          <span
                            className="tw-bg-gray-200 tw-rounded tw-px-1 tw-text-grey-body tw-m-0.5 tw-text-xs"
                            key={i}>
                            {team?.displayName ?? team?.name}
                          </span>
                        ))}
                      </span>
                    </p>
                  ) : null}
                  {roles?.length ? (
                    <p className="tw-mt-2">
                      <SVGIcons
                        alt="icon"
                        className="tw-w-4"
                        icon={Icons.USERS}
                      />
                      <span className="tw-mr-2 tw-ml-1 tw-align-middle tw-font-medium">
                        Roles
                      </span>
                      <span className="tw-flex tw-flex-wrap tw-mt-1">
                        {isAdmin && (
                          <span className="tw-bg-gray-200 tw-rounded tw-px-1 tw-text-grey-body tw-m-0.5 tw-text-xs">
                            {TERM_ADMIN}
                          </span>
                        )}
                        {roles.map((role, i) => (
                          <span
                            className="tw-bg-gray-200 tw-rounded tw-px-1 tw-text-grey-body tw-m-0.5 tw-text-xs"
                            key={i}>
                            {role?.displayName ?? role?.name}
                          </span>
                        ))}
                      </span>
                    </p>
                  ) : null}
                </div>
              </div>
            )}
          </div>
        )}
      </Fragment>
    );
  };

  const entityDisplayName = () => {
    let displayName;
    if (entityType === EntityType.TABLE) {
      displayName = getPartialNameFromTableFQN(entityFQN, [FqnPart.Table]);
    } else if (entityType === EntityType.DATABASE_SCHEMA) {
      displayName = getPartialNameFromTableFQN(entityFQN, [FqnPart.Schema]);
    } else if (
      [
        EntityType.DATABASE_SERVICE,
        EntityType.DASHBOARD_SERVICE,
        EntityType.MESSAGING_SERVICE,
        EntityType.PIPELINE_SERVICE,
      ].includes(entityType as EntityType)
    ) {
      displayName = getPartialNameFromFQN(entityFQN, ['service']);
    } else if (
      [EntityType.GLOSSARY, EntityType.GLOSSARY_TERM].includes(
        entityType as EntityType
      )
    ) {
      displayName = entityFQN.split(FQN_SEPARATOR_CHAR).pop();
    } else {
      displayName = getPartialNameFromFQN(entityFQN, ['database']);
    }

    return displayName;
  };

  const prepareFeedLink = () => {
    const withoutFeedEntities = [
      EntityType.WEBHOOK,
      EntityType.GLOSSARY,
      EntityType.GLOSSARY_TERM,
    ];

    const entityLink = getEntityLink(entityType, entityFQN);

    if (!withoutFeedEntities.includes(entityType as EntityType)) {
      return `${entityLink}/${TabSpecificField.ACTIVITY_FEED}`;
    } else {
      return entityLink;
    }
  };

  const getFeedLinkElement = () => {
    if (!isUndefined(entityFQN) && !isUndefined(entityType)) {
      return (
        <span className="tw-pl-1 tw-font-normal" data-testid="headerText">
          posted on{' '}
          {isEntityFeed ? (
            <span className="tw-heading" data-testid="headerText-entityField">
              {entityField}
            </span>
          ) : (
            <Fragment>
              <span data-testid="entityType">{entityType} </span>
              <Link data-testid="entitylink" to={prepareFeedLink()}>
                <button className="link-text" disabled={AppState.isTourOpen}>
                  {entityDisplayName()}
                </button>
              </Link>
            </Fragment>
          )}
        </span>
      );
    } else {
      return null;
    }
  };

  return (
    <div className={classNames('tw-flex tw-mb-1.5', className)}>
      <PopOver
        hideDelay={500}
        html={getUserData()}
        position="top"
        theme="light"
        trigger="click">
        <span
          className="tw-cursor-pointer"
          data-testid="authorAvatar"
          onClick={onClickHandler}>
          <Avatar name={createdBy} type="square" width="30" />
        </span>
      </PopOver>
      <h6 className="tw-flex tw-items-center tw-m-0 tw-heading tw-pl-2">
        <PopOver
          hideDelay={500}
          html={getUserData()}
          position="top"
          theme="light"
          trigger="click">
          <span
            className="thread-author tw-cursor-pointer"
            onClick={onClickHandler}>
            {createdBy}
          </span>
        </PopOver>
        {getFeedLinkElement()}
        <span
          className="tw-text-grey-muted tw-pl-2 tw-text-xs"
          data-testid="timestamp">
          {getDayTimeByTimeStamp(timeStamp)}
        </span>
      </h6>
    </div>
  );
};

export default FeedCardHeader;
