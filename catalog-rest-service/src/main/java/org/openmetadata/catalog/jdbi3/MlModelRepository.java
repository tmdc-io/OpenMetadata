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

import static org.openmetadata.catalog.Entity.FIELD_FOLLOWERS;
import static org.openmetadata.catalog.Entity.FIELD_OWNER;
import static org.openmetadata.catalog.Entity.FIELD_TAGS;
import static org.openmetadata.catalog.Entity.MLMODEL;
import static org.openmetadata.catalog.type.Include.ALL;
import static org.openmetadata.catalog.util.EntityUtil.entityReferenceMatch;
import static org.openmetadata.catalog.util.EntityUtil.mlFeatureMatch;
import static org.openmetadata.catalog.util.EntityUtil.mlHyperParameterMatch;
import static org.openmetadata.common.utils.CommonUtil.nullOrEmpty;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.openmetadata.catalog.Entity;
import org.openmetadata.catalog.entity.data.MlModel;
import org.openmetadata.catalog.resources.mlmodels.MlModelResource;
import org.openmetadata.catalog.type.ChangeDescription;
import org.openmetadata.catalog.type.EntityReference;
import org.openmetadata.catalog.type.MlFeature;
import org.openmetadata.catalog.type.MlFeatureSource;
import org.openmetadata.catalog.type.MlHyperParameter;
import org.openmetadata.catalog.type.Relationship;
import org.openmetadata.catalog.type.TagLabel;
import org.openmetadata.catalog.util.EntityInterface;
import org.openmetadata.catalog.util.EntityUtil;
import org.openmetadata.catalog.util.EntityUtil.Fields;
import org.openmetadata.catalog.util.FullyQualifiedName;

@Slf4j
public class MlModelRepository extends EntityRepository<MlModel> {
  private static final String MODEL_UPDATE_FIELDS = "owner,dashboard,tags";
  private static final String MODEL_PATCH_FIELDS = "owner,dashboard,tags";

  public MlModelRepository(CollectionDAO dao) {
    super(
        MlModelResource.COLLECTION_PATH,
        Entity.MLMODEL,
        MlModel.class,
        dao.mlModelDAO(),
        dao,
        MODEL_PATCH_FIELDS,
        MODEL_UPDATE_FIELDS);
  }

  public static String getFQN(MlModel model) {
    return FullyQualifiedName.build(model.getName());
  }

  @Override
  public MlModel setFields(MlModel mlModel, Fields fields) throws IOException {
    mlModel.setOwner(fields.contains(FIELD_OWNER) ? getOwner(mlModel) : null);
    mlModel.setDashboard(fields.contains("dashboard") ? getDashboard(mlModel) : null);
    mlModel.setFollowers(fields.contains(FIELD_FOLLOWERS) ? getFollowers(mlModel) : null);
    mlModel.setTags(fields.contains(FIELD_TAGS) ? getTags(mlModel.getFullyQualifiedName()) : null);
    mlModel.setUsageSummary(
        fields.contains("usageSummary") ? EntityUtil.getLatestUsage(daoCollection.usageDAO(), mlModel.getId()) : null);
    return mlModel;
  }

  @Override
  public void restorePatchAttributes(MlModel original, MlModel updated) {
    // Patch can't make changes to following fields. Ignore the changes
    updated
        .withFullyQualifiedName(original.getFullyQualifiedName())
        .withName(original.getName())
        .withId(original.getId());
  }

  @Override
  public EntityInterface<MlModel> getEntityInterface(MlModel entity) {
    return new MlModelEntityInterface(entity);
  }

  private void setMlFeatureSourcesFQN(List<MlFeatureSource> mlSources) {
    mlSources.forEach(
        s -> {
          if (s.getDataSource() != null) {
            s.setFullyQualifiedName(FullyQualifiedName.add(s.getDataSource().getFullyQualifiedName(), s.getName()));
          } else {
            s.setFullyQualifiedName(s.getName());
          }
        });
  }

  private void setMlFeatureFQN(String parentFQN, List<MlFeature> mlFeatures) {
    mlFeatures.forEach(
        f -> {
          String featureFqn = FullyQualifiedName.add(parentFQN, f.getName());
          f.setFullyQualifiedName(featureFqn);
          if (f.getFeatureSources() != null) {
            setMlFeatureSourcesFQN(f.getFeatureSources());
          }
        });
  }

  /** Make sure that all the MlFeatureSources are pointing to correct EntityReferences in tha Table DAO. */
  private void validateReferences(List<MlFeature> mlFeatures) throws IOException {
    for (MlFeature feature : mlFeatures) {
      if (!nullOrEmpty(feature.getFeatureSources())) {
        for (MlFeatureSource source : feature.getFeatureSources()) {
          validateMlDataSource(source);
        }
      }
    }
  }

  private void validateMlDataSource(MlFeatureSource source) throws IOException {
    if (source.getDataSource() != null) {
      Entity.getEntityReferenceById(source.getDataSource().getType(), source.getDataSource().getId());
    }
  }

  @Override
  public void prepare(MlModel mlModel) throws IOException {
    mlModel.setFullyQualifiedName(getFQN(mlModel));

    if (!nullOrEmpty(mlModel.getMlFeatures())) {
      validateReferences(mlModel.getMlFeatures());
      setMlFeatureFQN(mlModel.getFullyQualifiedName(), mlModel.getMlFeatures());
    }

    // Check if owner is valid and set the relationship
    populateOwner(mlModel.getOwner());

    // Check that the dashboard exists
    if (mlModel.getDashboard() != null) {
      daoCollection.dashboardDAO().findEntityReferenceById(mlModel.getDashboard().getId());
    }

    mlModel.setTags(addDerivedTags(mlModel.getTags()));
  }

  @Override
  public void storeEntity(MlModel mlModel, boolean update) throws IOException {
    // Relationships and fields such as href are derived and not stored as part of json
    EntityReference owner = mlModel.getOwner();
    List<TagLabel> tags = mlModel.getTags();
    EntityReference dashboard = mlModel.getDashboard();

    // Don't store owner, dashboard, href and tags as JSON. Build it on the fly based on relationships
    mlModel.withOwner(null).withDashboard(null).withHref(null).withTags(null);

    store(mlModel.getId(), mlModel, update);

    // Restore the relationships
    mlModel.withOwner(owner).withDashboard(dashboard).withTags(tags);
  }

  @Override
  public void storeRelationships(MlModel mlModel) {
    storeOwner(mlModel, mlModel.getOwner());

    setDashboard(mlModel, mlModel.getDashboard());

    if (mlModel.getDashboard() != null) {
      // Add relationship from MlModel --- uses ---> Dashboard
      addRelationship(
          mlModel.getId(), mlModel.getDashboard().getId(), Entity.MLMODEL, Entity.DASHBOARD, Relationship.USES);
    }

    applyTags(mlModel);
  }

  @Override
  public EntityUpdater getUpdater(MlModel original, MlModel updated, Operation operation) {
    return new MlModelUpdater(original, updated, operation);
  }

  private EntityReference getDashboard(MlModel mlModel) throws IOException {
    if (mlModel != null) {
      List<String> ids = findTo(mlModel.getId(), Entity.MLMODEL, Relationship.USES, Entity.DASHBOARD);
      ensureSingleRelationship(MLMODEL, mlModel.getId(), ids, "dashboards", false);
      return ids.isEmpty()
          ? null
          : daoCollection.dashboardDAO().findEntityReferenceById(UUID.fromString(ids.get(0)), ALL);
    }
    return null;
  }

  public void setDashboard(MlModel mlModel, EntityReference dashboard) {
    if (dashboard != null) {
      addRelationship(
          mlModel.getId(), mlModel.getDashboard().getId(), Entity.MLMODEL, Entity.DASHBOARD, Relationship.USES);
    }
  }

  public void removeDashboard(MlModel mlModel) {
    deleteTo(mlModel.getId(), Entity.MLMODEL, Relationship.USES, Entity.DASHBOARD);
  }

  public static class MlModelEntityInterface extends EntityInterface<MlModel> {
    public MlModelEntityInterface(MlModel entity) {
      super(MLMODEL, entity);
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
      return entity.getFullyQualifiedName() != null ? entity.getFullyQualifiedName() : MlModelRepository.getFQN(entity);
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
    public ChangeDescription getChangeDescription() {
      return entity.getChangeDescription();
    }

    @Override
    public MlModel getEntity() {
      return entity;
    }

    @Override
    public EntityReference getContainer() {
      return null;
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
    public MlModel withHref(URI href) {
      return entity.withHref(href);
    }

    @Override
    public void setTags(List<TagLabel> tags) {
      entity.setTags(tags);
    }
  }

  /** Handles entity updated from PUT and POST operation. */
  public class MlModelUpdater extends EntityUpdater {
    public MlModelUpdater(MlModel original, MlModel updated, Operation operation) {
      super(original, updated, operation);
    }

    @Override
    public void entitySpecificUpdate() throws IOException {
      MlModel origMlModel = original.getEntity();
      MlModel updatedMlModel = updated.getEntity();
      updateAlgorithm(origMlModel, updatedMlModel);
      updateDashboard(origMlModel, updatedMlModel);
      updateMlFeatures(origMlModel, updatedMlModel);
      updateMlHyperParameters(origMlModel, updatedMlModel);
      updateMlStore(origMlModel, updatedMlModel);
      updateServer(origMlModel, updatedMlModel);
      updateTarget(origMlModel, updatedMlModel);
    }

    private void updateAlgorithm(MlModel origModel, MlModel updatedModel) throws JsonProcessingException {
      // Updating an algorithm should be flagged for an ML Model
      if (recordChange("algorithm", origModel.getAlgorithm(), updatedModel.getAlgorithm())) {
        // Mark the EntityUpdater version change to major
        majorVersionChange = true;
      }
    }

    private void updateMlFeatures(MlModel origModel, MlModel updatedModel) throws JsonProcessingException {
      List<MlFeature> addedList = new ArrayList<>();
      List<MlFeature> deletedList = new ArrayList<>();
      recordListChange(
          "mlFeatures",
          origModel.getMlFeatures(),
          updatedModel.getMlFeatures(),
          addedList,
          deletedList,
          mlFeatureMatch);
    }

    private void updateMlHyperParameters(MlModel origModel, MlModel updatedModel) throws JsonProcessingException {
      List<MlHyperParameter> addedList = new ArrayList<>();
      List<MlHyperParameter> deletedList = new ArrayList<>();
      recordListChange(
          "mlHyperParameters",
          origModel.getMlHyperParameters(),
          updatedModel.getMlHyperParameters(),
          addedList,
          deletedList,
          mlHyperParameterMatch);
    }

    private void updateMlStore(MlModel origModel, MlModel updatedModel) throws JsonProcessingException {
      recordChange("mlStore", origModel.getMlStore(), updatedModel.getMlStore(), true);
    }

    private void updateServer(MlModel origModel, MlModel updatedModel) throws JsonProcessingException {
      // Updating the server can break current integrations to the ML services or enable new integrations
      if (recordChange("server", origModel.getServer(), updatedModel.getServer())) {
        // Mark the EntityUpdater version change to major
        majorVersionChange = true;
      }
    }

    private void updateTarget(MlModel origModel, MlModel updatedModel) throws JsonProcessingException {
      // Updating the target changes the model response
      if (recordChange("target", origModel.getTarget(), updatedModel.getTarget())) {
        majorVersionChange = true;
      }
    }

    private void updateDashboard(MlModel origModel, MlModel updatedModel) throws JsonProcessingException {
      EntityReference origDashboard = origModel.getDashboard();
      EntityReference updatedDashboard = updatedModel.getDashboard();
      if (recordChange("dashboard", origDashboard, updatedDashboard, true, entityReferenceMatch)) {

        // Remove the dashboard associated with the model, if any
        if (origModel.getDashboard() != null) {
          deleteTo(updatedModel.getId(), Entity.MLMODEL, Relationship.USES, Entity.DASHBOARD);
        }

        // Add relationship from model -- uses --> dashboard
        if (updatedDashboard != null) {
          addRelationship(
              updatedModel.getId(), updatedDashboard.getId(), Entity.MLMODEL, Entity.DASHBOARD, Relationship.USES);
        }
      }
    }
  }
}
