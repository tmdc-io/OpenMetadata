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

import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { capitalize } from 'lodash';
import React from 'react';
import { TITLE_FOR_NON_ADMIN_ACTION } from '../../constants/constants';
import { UserType } from '../../enums/user.enum';
import { TeamsAndUsersProps } from '../../interface/teamsAndUsers.interface';
import AddUsersModal from '../../pages/teams/AddUsersModal';
import { getActiveCatClass, getCountBadge } from '../../utils/CommonUtils';
import { getActiveUsers } from '../../utils/TeamUtils';
import { Button } from '../buttons/Button/Button';
import NonAdminAction from '../common/non-admin-action/NonAdminAction';
import PageLayout from '../containers/PageLayout';
import Loader from '../Loader/Loader';
import TeamDetails from '../TeamDetails/TeamDetails';
import UserDetails from '../UserDetails/UserDetails';

const TeamsAndUsers = ({
  users,
  isUsersLoading,
  admins,
  bots,
  activeUserTab,
  userSearchTerm,
  selectedUserList,
  handleUserSearchTerm,
  handleDeleteUser,
  handleJoinTeamClick,
  handleLeaveTeamClick,
  isRightPannelLoading,
  hasAccess,
  isTeamVisible,
  teams,
  currentTeam,
  currentTeamUsers,
  teamUserPagin,
  currentTeamUserPage,
  teamUsersSearchText,
  isDescriptionEditable,
  errorNewTeamData,
  isAddingTeam,
  createNewTeam,
  handleAddNewUser,
  handleAddTeam,
  onNewTeamDataChange,
  updateTeamHandler,
  onDescriptionUpdate,
  descriptionHandler,
  handleTeamUsersSearchAction,
  teamUserPaginHandler,
  changeCurrentTeam,
  isAddingUsers,
  isTeamMemberLoading,
  getUniqueUserList,
  addUsersToTeam,
  handleAddUser,
  removeUserFromTeam,
  afterDeleteAction,
}: TeamsAndUsersProps) => {
  const usersData = [
    {
      name: UserType.USERS,
      data: users,
    },
    {
      name: UserType.ADMINS,
      data: admins,
    },
    {
      name: UserType.BOTS,
      data: bots,
    },
  ];

  /**
   *
   * @returns - Teams data for left panel
   */
  const fetchLeftPanel = () => {
    return (
      <>
        <div className="tw-mb-8">
          <div className="tw-flex tw-justify-between tw-items-center tw-mb-2 tw-border-b">
            <p className="tw-heading">Teams</p>
            {hasAccess && (
              <NonAdminAction
                position="bottom"
                title={TITLE_FOR_NON_ADMIN_ACTION}>
                <Button
                  className="tw-h-7 tw-px-2 tw-mb-4"
                  data-testid="add-teams"
                  size="small"
                  theme="primary"
                  variant="contained"
                  onClick={() => {
                    handleAddTeam(true);
                  }}>
                  <FontAwesomeIcon icon="plus" />
                </Button>
              </NonAdminAction>
            )}
          </div>
          {teams.map((team) => (
            <div
              className="tw-flex tw-items-center tw-justify-between tw-mb-2 tw-cursor-pointer"
              key={team.name}
              onClick={() => {
                changeCurrentTeam(team.name, false);
              }}>
              <div
                className={`tw-group tw-text-grey-body tw-cursor-pointer tw-text-body tw-flex tw-justify-between ${getActiveCatClass(
                  team.name,
                  currentTeam?.name
                )}`}>
                <p
                  className="tag-category label-category tw-self-center tw-truncate tw-w-52"
                  title={team.displayName ?? team.name}>
                  {team.displayName ?? team.name}
                </p>
              </div>
              {getCountBadge(
                getActiveUsers(team.users).length,
                '',
                currentTeam?.name === team.name
              )}
            </div>
          ))}
        </div>
        {hasAccess && (
          <div>
            <div className="tw-flex tw-justify-between tw-items-center tw-mb-2 tw-border-b">
              <p className="tw-heading">All Users</p>
              {hasAccess && (
                <NonAdminAction
                  position="bottom"
                  title={TITLE_FOR_NON_ADMIN_ACTION}>
                  <Button
                    className="tw-h-7 tw-px-2 tw-mb-4"
                    data-testid="add-teams"
                    size="small"
                    theme="primary"
                    variant="contained"
                    onClick={handleAddNewUser}>
                    <FontAwesomeIcon icon="plus" />
                  </Button>
                </NonAdminAction>
              )}
            </div>
            {usersData.map((d) => (
              <div
                className="tw-flex tw-items-center tw-justify-between tw-mb-2 tw-cursor-pointer"
                key={d.name}
                onClick={() => {
                  changeCurrentTeam(d.name, true);
                }}>
                <div
                  className={`tw-group tw-text-grey-body tw-cursor-pointer tw-text-body tw-flex tw-justify-between ${getActiveCatClass(
                    d.name,
                    activeUserTab
                  )}`}>
                  <p
                    className="tag-category label-category tw-self-center tw-truncate tw-w-52"
                    title={capitalize(d.name)}>
                    {capitalize(d.name)}
                  </p>
                </div>
                {getCountBadge(d.data.length, '', activeUserTab === d.name)}
              </div>
            ))}
          </div>
        )}
      </>
    );
  };

  return (
    <PageLayout classes="tw-h-full tw-p-4" leftPanel={fetchLeftPanel()}>
      {isRightPannelLoading ? (
        <Loader />
      ) : (
        <div
          className="tw-pb-3 tw-w-full tw-h-full tw-flex tw-flex-col"
          data-testid="team-and-user-container">
          {!isTeamVisible ? (
            <UserDetails
              handleDeleteUser={handleDeleteUser}
              handleUserSearchTerm={handleUserSearchTerm}
              isUsersLoading={isUsersLoading}
              selectedUserList={selectedUserList}
              userSearchTerm={userSearchTerm}
            />
          ) : (
            <TeamDetails
              afterDeleteAction={afterDeleteAction}
              createNewTeam={createNewTeam}
              currentTeam={currentTeam}
              currentTeamUserPage={currentTeamUserPage}
              currentTeamUsers={currentTeamUsers}
              descriptionHandler={descriptionHandler}
              errorNewTeamData={errorNewTeamData}
              handleAddTeam={handleAddTeam}
              handleAddUser={handleAddUser}
              handleJoinTeamClick={handleJoinTeamClick}
              handleLeaveTeamClick={handleLeaveTeamClick}
              handleTeamUsersSearchAction={handleTeamUsersSearchAction}
              hasAccess={hasAccess}
              isAddingTeam={isAddingTeam}
              isDescriptionEditable={isDescriptionEditable}
              isTeamMemberLoading={isTeamMemberLoading}
              removeUserFromTeam={removeUserFromTeam}
              teamUserPagin={teamUserPagin}
              teamUserPaginHandler={teamUserPaginHandler}
              teamUsersSearchText={teamUsersSearchText}
              teams={teams}
              updateTeamHandler={updateTeamHandler}
              onDescriptionUpdate={onDescriptionUpdate}
              onNewTeamDataChange={onNewTeamDataChange}
            />
          )}
        </div>
      )}

      {isAddingUsers && (
        <AddUsersModal
          header={`Adding new users to ${
            currentTeam?.displayName ?? currentTeam?.name
          }`}
          list={getUniqueUserList()}
          onCancel={() => handleAddUser(false)}
          onSave={(data) => addUsersToTeam(data)}
        />
      )}
    </PageLayout>
  );
};

export default TeamsAndUsers;
