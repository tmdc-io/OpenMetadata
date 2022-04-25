package org.openmetadata.catalog.selenium.objectRepository;

import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

@Getter
@RequiredArgsConstructor
public class DashboardDetails {
  @Nonnull WebDriver webDriver;

  By dashboard = By.xpath("(//button[@data-testid='tab'])[3]");
  By editChartDescription = By.xpath("//div[@data-testid='description']/span/span");
  By dashboardDescriptionBox = By.xpath("//div[@data-testid='viewer-container']/p");
  By descriptionBox = By.xpath("(//div[@data-testid='description'])[2]");
  By addChartTag = By.xpath("(//span[@data-testid='tags'])[2]");
  By selectedTag = By.xpath("//span[@class='tw-no-underline hover:tw-no-underline tw-py-0.5 tw-px-2 tw-pl-2 tw-pr-1']");
  By chartTags = By.xpath("//div[@data-testid='tag-container']/div/div");
  By breadCrumbTags = By.xpath("//div[@data-testid='entity-tags']/div");
  By selectTier = By.cssSelector("[data-testid='select-tier-buuton']");
}
