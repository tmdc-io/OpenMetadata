package org.openmetadata.catalog.selenium.objectRepository;

import java.util.List;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

@Getter
@RequiredArgsConstructor
public class Common {
  @Nonnull WebDriver webDriver;
  static String enterDescription = "//div[@data-testid='enterDescription']/div/div[2]/div/div/div/div/div/div";

  By displayName = By.name("name");
  By descriptionBoldButton = By.cssSelector("[class='bold toastui-editor-toolbar-icons']");
  By descriptionItalicButton = By.cssSelector("[class='italic toastui-editor-toolbar-icons']");
  By descriptionLinkButton = By.cssSelector("[class='link toastui-editor-toolbar-icons']");
  By descriptionSaveButton = By.cssSelector("[data-testid='saveButton']");
  By addDescriptionString = By.xpath(enterDescription);
  By editTagCategoryDescription = By.cssSelector("[data-testid='edit-description']");
  By editDescriptionSaveButton = By.cssSelector("[data-testid='save']");
  By closeWhatsNew = By.cssSelector("[data-testid='closeWhatsNew']");
  By headerSettings = By.cssSelector("[data-testid='menu-button'][id='menu-button-Settings']");
  By explore = By.cssSelector("[data-testid='appbar-item'][id='explore']");
  By headerSettingsServices = By.cssSelector("[data-testid='menu-item-Services']");
  By addServiceButton = By.cssSelector("[data-testid='add-new-service-button']");
  By noServicesAddServiceButton = By.cssSelector("[data-testid='add-new-user-button']");
  By serviceName = By.cssSelector("[data-testid='service-name']");
  By serviceUsername = By.cssSelector("[id='root_username']");
  By servicePassword = By.cssSelector("[id='root_password']");
  By nextButton = By.cssSelector("[data-testid='next-button']");
  By saveServiceButton = By.cssSelector("[data-testid='submit-btn']");
  By saveEditedService = By.cssSelector("[data-testid='save-button']");
  By saveConnectionConfig = By.cssSelector("[data-testid='saveManageTab']");
  By searchResults = By.cssSelector("[data-testid='search-results']");
  By searchBar = By.cssSelector("[data-testid='searchBox']");
  By searchSuggestion = By.cssSelector("[data-testid='data-name']");
  By editAssociatedTagButton = By.xpath("//div[@data-testid='tag-container']//span");
  By enterAssociatedTagName = By.cssSelector("[data-testid='associatedTagName']");
  By tagListItem = By.cssSelector("[data-testid='list-item']");
  By saveAssociatedTag = By.cssSelector("[data-testid='saveAssociatedTag']");
  By searchResultsList = By.xpath("//div[@data-testid='search-results']/div");
  By ownerDropdown = By.cssSelector("[data-testid='owner-dropdown']");
  By ownerSearchBox = By.cssSelector("[data-testid='searchInputText']");
  By closeErrorMessage = By.cssSelector("[class='Toastify__close-button Toastify__close-button--light']");
  By serviceUrl = By.cssSelector("[data-testid='url']");
  By servicePort = By.cssSelector("[id='root_hostPort']");
  By databaseName = By.cssSelector("[id='root_database']");
  By addTagCategory = By.cssSelector("[data-testid='add-category']");
  By addTagButton = By.cssSelector("[data-testid='add-new-tag-button']");
  By tagCount = By.xpath("//div[@data-testid='tag-container']/div/div");
  By errorMessage = By.cssSelector("[data-testid='error-message']");
  By matchesInDescription = By.xpath("(//div[@data-testid='matches-stats'][1])/span[3]");
  By tagCountSearch = By.xpath("(//div[@data-testid='viewer-container'])[1]//span[@class='text-highlighter']");
  By tagFilterCount = By.xpath("//div[@data-testid='filter-containers-2']/label");
  By noSearchResult = By.cssSelector("[data-testid='no-search-results']");
  By resultsCount = By.xpath("//div[@data-testid='search-container']/div");
  By matchesStats = By.cssSelector("[data-testid='matches-stats']");
  By editLineageButton = By.cssSelector("[data-testid='edit-lineage']");
  By whatsNewModalChangeLogs = By.cssSelector("[data-testid='WhatsNewModalChangeLogs']");
  By tableCount = By.xpath("(//span[@data-testid='filter-count'])[1]");
  By removeAssociatedTag = By.xpath("//span[@data-testid='remove']");
  By breadCrumb = By.xpath("//li[@data-testid='breadcrumb-link']");
  By descriptionContainer = By.xpath("(//div[@data-testid='description']/div/span)[1]");
  By manage = By.cssSelector("[id='manage']");
  By selectTier1 = By.xpath("(//div[@data-testid='card-list'])[1]");
  By difference = By.xpath("//span[@class='diff-added']");
  By differenceRemoved = By.xpath("//span[@class='diff-removed']");
  By searchBox = By.xpath("//input[@data-testid=\"searchBox\"]");
  By follow = By.cssSelector("button[data-testid='follow-button']");
  By users = By.xpath("//div[@data-testid='dropdown-list']//div[2]//button[2]");
  By selectUser = By.xpath("//div[@data-testid=\"list-item\"]");
  By saveManage = By.cssSelector("[data-testid='saveManageTab']");
  By editDescriptionButton = By.xpath("//button[@data-testid= 'edit-description']");
  By editDescriptionBox = By.xpath("//div[@data-testid='enterDescription']/div/div[2]/div/div/div/div/div/div");
  By version = By.xpath("//button[@data-testid=\"version-button\"]");
  By versionRadioButton = By.xpath("//span[@data-testid=\"select-version\"]");
  By topics = By.xpath("(//button[@data-testid='tab'])[2]");
  By dashboard = By.xpath("(//button[@data-testid='tab'])[3]");
  By pipeline = By.xpath("(//button[@data-testid='tab'])[4]");
  By selectTable = By.xpath("(//button[@data-testid=\"table-link\"])[1]");
  By addTag = By.xpath("(//div[@data-testid='tag-container'])[1]");
  By breadCrumbTags = By.xpath("(//span[@data-testid='tags'])");
  By tagsAdded = By.cssSelector("span[class='tw-no-underline hover:tw-no-underline tw-px-1']");
  By headerSettingsTeams = By.cssSelector("[data-testid='menu-item-Teams & Users']");
  By viewMore = By.xpath("//div[@data-testid='filter-containers-2']/p");
  By home = By.cssSelector("[data-testid='image']");
  By saveWebhook = By.cssSelector("[data-testid='save-webhook']");
  By reviewCount = By.cssSelector("[data-testid='user-card-container']");
  By tagsCount = By.xpath("(//div[@data-testid='tags']/div)");
  By addGlossaryReviewer = By.cssSelector("[data-testid='add-new-reviewer']");
  By connectionConfig = By.cssSelector("[data-testid='Connection']");
  By ingestion = By.cssSelector("[data-testid='Ingestions']");
  By descriptionBox = By.xpath("//div[@class='ProseMirror']");
  By focusedDescriptionBox = By.xpath("//div[@class='ProseMirror ProseMirror-focused']");
  By urlLink = By.cssSelector("[id='toastuiLinkUrlInput']");
  By linkText = By.cssSelector("[id='toastuiLinkTextInput']");
  By okButton = By.cssSelector("[class='toastui-editor-ok-button']");
  By hostPort = By.cssSelector("[id='root_hostPort']");
  By addIngestion = By.cssSelector("[data-testid='add-ingestion-button']");
  By deployButton = By.cssSelector("[data-testid='deploy-button']");
  By confirmButton = By.cssSelector("[data-testid='confirm-button']");

  public List<WebElement> versionRadioButton() {
    return webDriver.findElements(versionRadioButton);
  }

  public By containsText(String matchingText) {
    return By.xpath("//*[text()[contains(.,'" + matchingText + "')]] ");
  }

  public By serviceType(String serviceType) {
    return By.cssSelector("[data-testid='" + serviceType + "']");
  }

  public By deleteServiceButton(String serviceName) {
    return By.cssSelector("[data-testid='delete-service-" + serviceName + "']");
  }

  public By serviceDetailsTabs(String tab) {
    return By.cssSelector("[data-testid='tab'][id='" + tab + "']");
  }

  public By selectServiceTab(int index) {
    return By.xpath("(//div[@data-testid='tab'])[" + index + "]");
  }

  public By headerSettingsMenu(String menuItem) {
    return By.cssSelector("[data-testid='menu-item-" + menuItem + "']");
  }

  public By selectOverview(String overview) {
    return By.cssSelector("[data-testid='" + overview + "']");
  }

  public By explorePagination(int index) {
    return By.xpath("//div[@data-testid='pagination-button']//ul//li[" + index + "]");
  }

  public By selectFilterExplore(String filter) {
    return By.cssSelector("[data-testid='checkbox'][id='" + filter + "']");
  }

  public By headerItem(String item) {
    return By.cssSelector("[data-testid='appbar-item'][id='" + item + "']");
  }

  public By exploreFilterCount(String filter) {
    return By.xpath("//label[@data-testid='filter-container-" + filter + "']//span[@data-testid='filter-count']");
  }

  public By entityTabIndex(int index) {
    return By.xpath("(//button[@data-testid='tab'])" + "[" + index + "]");
  }

  public By selectSuggestionSearch(String result) {
    return By.cssSelector("[data-testid='data-name'][id='" + result + "']");
  }

  public By tourNavigationArrow(String arrow) {
    return By.cssSelector("[data-tour-elem='" + arrow + "']");
  }

  public By whatsNewDotButtons(int index) {
    return By.xpath("//ul[@class='slick-dots testid-dots-button']//li[" + index + "]");
  }

  public By overviewFilterCount(String entity) {
    return By.xpath("//div[@data-testid='" + entity + "-summary']//span[@data-testid='filter-count']");
  }

  public List<WebElement> breadCrumb() {
    return webDriver.findElements(breadCrumb);
  }

  public By selectTableLink(int index) {
    return By.xpath("(//button[@data-testid='table-link'])[" + index + "]");
  }

  public By followingTable(String table) {
    return By.xpath("//div[@data-testid='Following data-" + table + "']");
  }

  public By recentlySearchedText(String text) {
    return By.xpath("//div[@data-testid='Recently-Search-" + text + "']");
  }

  public By removeRecentlySearchedText(String text) {
    return By.xpath("//div[@data-testid='Recently-Search-" + text + "']/div/div/button/img");
  }
}
