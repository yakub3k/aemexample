package com.example.core.services.impl;

import com.example.core.services.EkwSearchService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * OSGi service implementation for automated EKW (Elektroniczne Księgi Wieczyste) search.
 * Uses Selenium WebDriver with Chrome to bypass Incapsula/Imperva anti-bot protection,
 * matching the behavior of the Python script (scripts/ekw_search.py).
 */
@Component(service = EkwSearchService.class, immediate = true)
@Designate(ocd = EkwSearchServiceImpl.Config.class)
public class EkwSearchServiceImpl implements EkwSearchService {

    private static final Logger LOG = LoggerFactory.getLogger(EkwSearchServiceImpl.class);

    private static final String DEFAULT_KOD_WYDZIALU = "LU1I";
    private static final String DEFAULT_NUMER_KSIEGI = "00016057";

    private static final String EKW_SEARCH_URL =
            "https://przegladarka-ekw.ms.gov.pl/eukw_prz/KsiegiWieczyste/wyszukiwanieKW"
                    + "?komunikaty=true&kontakt=true&okienkoSerwisowe=false";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36";

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int ANTI_BOT_WAIT_SECONDS = 10;
    private static final int RESULTS_WAIT_SECONDS = 6;

    private String outputDirectory;
    private int timeoutSeconds;
    private boolean headless;

    @ObjectClassDefinition(
            name = "EKW Search Service Configuration",
            description = "Configuration for the Elektroniczne Księgi Wieczyste search service"
    )
    @interface Config {

        @AttributeDefinition(
                name = "Output Directory",
                description = "Directory path for saving HTML results and temporary files"
        )
        String outputDirectory() default "scripts";

        @AttributeDefinition(
                name = "Connection Timeout (seconds)",
                description = "WebDriver wait timeout in seconds"
        )
        int timeoutSeconds() default DEFAULT_TIMEOUT_SECONDS;

        @AttributeDefinition(
                name = "Headless Mode",
                description = "Run Chrome in headless mode (no visible browser window)"
        )
        boolean headless() default true;
    }

    @Activate
    @Modified
    protected void activate(Config config) {
        this.outputDirectory = config.outputDirectory();
        this.timeoutSeconds = config.timeoutSeconds();
        this.headless = config.headless();
        LOG.info("EkwSearchService activated. Output directory: {}, timeout: {}s, headless: {}",
                outputDirectory, timeoutSeconds, headless);
    }

    @Override
    public EkwSearchResult searchDefault() {
        return searchKsiegaWieczysta(DEFAULT_KOD_WYDZIALU, DEFAULT_NUMER_KSIEGI);
    }

    @Override
    public EkwSearchResult searchKsiegaWieczysta(String kodWydzialu, String numerKsiegi) {
        String cyfraKontrolna = String.valueOf(calculateCheckDigit(kodWydzialu, numerKsiegi));
        LOG.info("Searching for księga wieczysta: {}/{}/{}", kodWydzialu, numerKsiegi, cyfraKontrolna);

        WebDriver driver = null;
        try {
            driver = createDriver();

            // Step 1: Open the search page
            LOG.info("[1] Opening search page: {}", EKW_SEARCH_URL);
            driver.get(EKW_SEARCH_URL);

            // Step 2: Wait for Incapsula anti-bot protection to resolve
            LOG.info("[2] Waiting {}s for anti-bot protection to resolve...", ANTI_BOT_WAIT_SECONDS);
            Thread.sleep(ANTI_BOT_WAIT_SECONDS * 1000L);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

            // Step 3: Fill in the form fields
            LOG.info("[3] Filling in kod wydziału: {}", kodWydzialu);
            WebElement kodInput = wait.until(
                    ExpectedConditions.presenceOfElementLocated(By.id("kodWydzialuInput")));
            kodInput.clear();
            kodInput.sendKeys(kodWydzialu);

            LOG.info("[4] Filling in numer księgi: {}", numerKsiegi);
            WebElement numerInput = wait.until(
                    ExpectedConditions.presenceOfElementLocated(By.id("numerKsiegiWieczystej")));
            numerInput.clear();
            numerInput.sendKeys(numerKsiegi);

            LOG.info("[5] Filling in cyfra kontrolna: {}", cyfraKontrolna);
            WebElement cyfraInput = wait.until(
                    ExpectedConditions.presenceOfElementLocated(By.id("cyfraKontrolna")));
            cyfraInput.clear();
            cyfraInput.sendKeys(cyfraKontrolna);

            // Step 4: Click the search button
            LOG.info("[6] Clicking 'Wyszukaj księgę wieczystą' button");
            WebElement submitBtn = wait.until(
                    ExpectedConditions.elementToBeClickable(By.id("wyszukaj")));
            submitBtn.click();

            // Step 5: Wait for results to load
            LOG.info("[7] Waiting {}s for results to load...", RESULTS_WAIT_SECONDS);
            Thread.sleep(RESULTS_WAIT_SECONDS * 1000L);

            // Step 6: Get and validate results
            String pageSource = driver.getPageSource();
            String title = driver.getTitle();
            String currentUrl = driver.getCurrentUrl();

            LOG.info("[8] Page title: {}, URL: {}", title, currentUrl);

            boolean isSuccess = validateResponse(pageSource, kodWydzialu);

            if (isSuccess) {
                String cleanHtml = cleanHtml(pageSource);
                saveHtmlToFile(cleanHtml, kodWydzialu, numerKsiegi, cyfraKontrolna);
                LOG.info("[9] Search completed successfully. Results saved.");
                return new EkwSearchResult(true, cleanHtml,
                        "Księga wieczysta " + kodWydzialu + "/" + numerKsiegi + "/" + cyfraKontrolna
                                + " found successfully.");
            } else {
                saveHtmlToFile(pageSource, kodWydzialu, numerKsiegi, cyfraKontrolna);
                LOG.warn("[9] Search completed but results may not be valid.");
                return new EkwSearchResult(false, pageSource,
                        "Search completed but could not confirm valid results for "
                                + kodWydzialu + "/" + numerKsiegi + "/" + cyfraKontrolna);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("EKW search interrupted", e);
            return new EkwSearchResult(false, null, "Search interrupted: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Unexpected error during EKW search", e);
            return new EkwSearchResult(false, null, "Unexpected error: " + e.getMessage());
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    LOG.warn("Error closing WebDriver", e);
                }
            }
        }
    }

    /**
     * Create a Chrome WebDriver with anti-detection options,
     * matching the Python script configuration.
     */
    private WebDriver createDriver() {
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();

        if (headless) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--window-size=1920,1080");

        // Anti-detection options (matching Python script)
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);
        options.addArguments("user-agent=" + USER_AGENT);

        ChromeDriver chromeDriver = new ChromeDriver(options);

        // Hide navigator.webdriver (matching Python script)
        chromeDriver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument",
                Map.of("source", "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"));

        return chromeDriver;
    }

    /**
     * Validate if the response HTML contains expected search results.
     */
    private boolean validateResponse(String html, String kodWydzialu) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        String lowerHtml = html.toLowerCase();
        return (lowerHtml.contains("księga") || html.contains(kodWydzialu))
                && !lowerHtml.contains("incapsula")
                && !lowerHtml.contains("access denied");
    }

    /**
     * Clean HTML by removing scripts, inline styles, and unnecessary whitespace.
     */
    private String cleanHtml(String html) {
        if (html == null) {
            return "";
        }
        // Remove script tags and their content
        String cleaned = html.replaceAll("(?si)<script[^>]*>.*?</script>", "");
        // Remove inline event handlers
        cleaned = cleaned.replaceAll("(?i)\\s+on\\w+=\"[^\"]*\"", "");
        cleaned = cleaned.replaceAll("(?i)\\s+on\\w+='[^']*'", "");
        // Remove noscript tags
        cleaned = cleaned.replaceAll("(?si)<noscript[^>]*>.*?</noscript>", "");
        // Normalize whitespace (collapse multiple blank lines)
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        return cleaned.trim();
    }

    @Override
    public int calculateCheckDigit(String kodWydzialu, String numerKsiegi) {
        if (kodWydzialu == null || numerKsiegi == null) {
            throw new IllegalArgumentException("kodWydzialu and numerKsiegi must not be null");
        }

        String input = (kodWydzialu + numerKsiegi).toUpperCase();
        if (input.length() != 12) {
            throw new IllegalArgumentException(
                    "kodWydzialu (4 chars) + numerKsiegi (8 chars) must be 12 characters total, got: " + input.length());
        }

        int[] weights = {1, 3, 7, 1, 3, 7, 1, 3, 7, 1, 3, 7};
        int sum = 0;

        for (int i = 0; i < 12; i++) {
            char c = input.charAt(i);
            int value;
            if (Character.isDigit(c)) {
                value = c - '0';
            } else if (Character.isLetter(c)) {
                int alphabetPosition = c - 'A' + 1;
                value = ((alphabetPosition - 1) % 9) + 1;
            } else {
                throw new IllegalArgumentException("Invalid character in input: " + c);
            }
            sum += value * weights[i];
        }

        return sum % 10;
    }

    /**
     * Save HTML content to a file in the configured output directory.
     */
    private void saveHtmlToFile(String html, String kwDepartment, String kwNumber, String kwChecksum) {
        try {
            @SuppressFBWarnings("PATH_TRAVERSAL_IN")
            Path outputDir = Paths.get(outputDirectory);
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("ekw_%s_%s_%s_%s.html",
                    kwDepartment, kwNumber, kwChecksum, timestamp);
            Path filePath = outputDir.resolve(filename);

            Files.writeString(filePath, html, StandardCharsets.UTF_8);
            LOG.info("HTML result saved to: {}", filePath.toAbsolutePath());

        } catch (IOException e) {
            LOG.error("Failed to save HTML result to file", e);
        }
    }
}
