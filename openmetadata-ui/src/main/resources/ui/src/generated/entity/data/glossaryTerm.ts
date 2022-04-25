/* eslint-disable @typescript-eslint/no-explicit-any */
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

/**
 * This schema defines te Glossary term entities.
 */
export interface GlossaryTerm {
  /**
   * Change that lead to this version of the entity.
   */
  changeDescription?: ChangeDescription;
  /**
   * Other glossary terms that are children of this glossary term.
   */
  children?: EntityReference[];
  /**
   * When `true` indicates the entity has been soft deleted.
   */
  deleted?: boolean;
  /**
   * Description of the glossary term.
   */
  description: string;
  /**
   * Display Name that identifies this glossary.
   */
  displayName?: string;
  /**
   * A unique name that identifies a glossary term. It captures name hierarchy of glossary of
   * terms in the form of `glossaryName.parentTerm.childTerm`.
   */
  fullyQualifiedName?: string;
  /**
   * Glossary that this term belongs to.
   */
  glossary: EntityReference;
  /**
   * Link to the resource corresponding to this entity.
   */
  href?: string;
  /**
   * Unique identifier of a glossary term instance.
   */
  id: string;
  /**
   * Preferred name for the glossary term.
   */
  name: string;
  /**
   * Parent glossary term that this term is child of. When `null` this term is the root term
   * of the glossary.
   */
  parent?: EntityReference;
  /**
   * Link to a reference from an external glossary.
   */
  references?: TermReference[];
  /**
   * Other glossary terms that are related to this glossary term.
   */
  relatedTerms?: EntityReference[];
  /**
   * User names of the reviewers for this glossary.
   */
  reviewers?: EntityReference[];
  /**
   * Status of the glossary term.
   */
  status?: Status;
  /**
   * Alternate names that are synonyms or near-synonyms for the glossary term.
   */
  synonyms?: string[];
  /**
   * Tags associated with this glossary term. These tags captures relationship of a glossary
   * term with a tag automatically. As an example a glossary term 'User.PhoneNumber' might
   * have an associated tag 'PII.Sensitive'. When 'User.Address' is used to label a column in
   * a table, 'PII.Sensitive' label is also applied automatically due to Associated tag
   * relationship.
   */
  tags?: TagLabel[];
  /**
   * Last update time corresponding to the new version of the entity in Unix epoch time
   * milliseconds.
   */
  updatedAt?: number;
  /**
   * User who made the update.
   */
  updatedBy?: string;
  /**
   * Count of how many times this and it's children glossary terms are used as labels.
   */
  usageCount?: number;
  /**
   * Metadata version of the entity.
   */
  version?: number;
}

/**
 * Change that lead to this version of the entity.
 *
 * Description of the change.
 */
export interface ChangeDescription {
  /**
   * Names of fields added during the version changes.
   */
  fieldsAdded?: FieldChange[];
  /**
   * Fields deleted during the version changes with old value before deleted.
   */
  fieldsDeleted?: FieldChange[];
  /**
   * Fields modified during the version changes with old and new values.
   */
  fieldsUpdated?: FieldChange[];
  /**
   * When a change did not result in change, this could be same as the current version.
   */
  previousVersion?: number;
}

export interface FieldChange {
  /**
   * Name of the entity field that changed.
   */
  name?: string;
  /**
   * New value of the field. Note that this is a JSON string and use the corresponding field
   * type to deserialize it.
   */
  newValue?: any;
  /**
   * Previous value of the field. Note that this is a JSON string and use the corresponding
   * field type to deserialize it.
   */
  oldValue?: any;
}

/**
 * Other glossary terms that are children of this glossary term.
 *
 * This schema defines the EntityReference type used for referencing an entity.
 * EntityReference is used for capturing relationships from one entity to another. For
 * example, a table has an attribute called database of type EntityReference that captures
 * the relationship of a table `belongs to a` database.
 *
 * Glossary that this term belongs to.
 *
 * Parent glossary term that this term is child of. When `null` this term is the root term
 * of the glossary.
 */
export interface EntityReference {
  /**
   * If true the entity referred to has been soft-deleted.
   */
  deleted?: boolean;
  /**
   * Optional description of entity.
   */
  description?: string;
  /**
   * Display Name that identifies this entity.
   */
  displayName?: string;
  /**
   * Fully qualified name of the entity instance. For entities such as tables, databases
   * fullyQualifiedName is returned in this field. For entities that don't have name hierarchy
   * such as `user` and `team` this will be same as the `name` field.
   */
  fullyQualifiedName?: string;
  /**
   * Link to the entity resource.
   */
  href?: string;
  /**
   * Unique identifier that identifies an entity instance.
   */
  id: string;
  /**
   * Name of the entity instance.
   */
  name?: string;
  /**
   * Entity type/class name - Examples: `database`, `table`, `metrics`, `databaseService`,
   * `dashboardService`...
   */
  type: string;
}

export interface TermReference {
  /**
   * Name that identifies the source of an external glossary term. Example `HealthCare.gov`.
   */
  endpoint?: string;
  /**
   * Name that identifies the source of an external glossary term. Example `HealthCare.gov`.
   */
  name?: string;
}

/**
 * Status of the glossary term.
 */
export enum Status {
  Approved = 'Approved',
  Deprecated = 'Deprecated',
  Draft = 'Draft',
}

/**
 * This schema defines the type for labeling an entity with a Tag.
 */
export interface TagLabel {
  /**
   * Unique name of the tag category.
   */
  description?: string;
  /**
   * Link to the tag resource.
   */
  href?: string;
  /**
   * Label type describes how a tag label was applied. 'Manual' indicates the tag label was
   * applied by a person. 'Derived' indicates a tag label was derived using the associated tag
   * relationship (see TagCategory.json for more details). 'Propagated` indicates a tag label
   * was propagated from upstream based on lineage. 'Automated' is used when a tool was used
   * to determine the tag label.
   */
  labelType: LabelType;
  /**
   * Label is from Tags or Glossary.
   */
  source: Source;
  /**
   * 'Suggested' state is used when a tag label is suggested by users or tools. Owner of the
   * entity must confirm the suggested labels before it is marked as 'Confirmed'.
   */
  state: State;
  tagFQN: string;
}

/**
 * Label type describes how a tag label was applied. 'Manual' indicates the tag label was
 * applied by a person. 'Derived' indicates a tag label was derived using the associated tag
 * relationship (see TagCategory.json for more details). 'Propagated` indicates a tag label
 * was propagated from upstream based on lineage. 'Automated' is used when a tool was used
 * to determine the tag label.
 */
export enum LabelType {
  Automated = 'Automated',
  Derived = 'Derived',
  Manual = 'Manual',
  Propagated = 'Propagated',
}

/**
 * Label is from Tags or Glossary.
 */
export enum Source {
  Glossary = 'Glossary',
  Tag = 'Tag',
}

/**
 * 'Suggested' state is used when a tag label is suggested by users or tools. Owner of the
 * entity must confirm the suggested labels before it is marked as 'Confirmed'.
 */
export enum State {
  Confirmed = 'Confirmed',
  Suggested = 'Suggested',
}
