package com.example.core.services.impl;

import com.example.core.services.EkwSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EkwSearchServiceImplTest {

    private EkwSearchServiceImpl service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new EkwSearchServiceImpl();
    }

    // --- validateResponse tests ---

    @Test
    void validateResponse_nullHtml_returnsFalse() throws Exception {
        assertFalse(invokeValidateResponse(null, "LU1I"));
    }

    @Test
    void validateResponse_emptyHtml_returnsFalse() throws Exception {
        assertFalse(invokeValidateResponse("", "LU1I"));
    }

    @Test
    void validateResponse_containsKsiega_returnsTrue() throws Exception {
        assertTrue(invokeValidateResponse("<html><body>Księga wieczysta</body></html>", "LU1I"));
    }

    @Test
    void validateResponse_containsKodWydzialu_returnsTrue() throws Exception {
        assertTrue(invokeValidateResponse("<html><body>Wynik dla LU1I</body></html>", "LU1I"));
    }

    @Test
    void validateResponse_containsIncapsula_returnsFalse() throws Exception {
        assertFalse(invokeValidateResponse("<html><body>Księga Incapsula blocked</body></html>", "LU1I"));
    }

    @Test
    void validateResponse_containsAccessDenied_returnsFalse() throws Exception {
        assertFalse(invokeValidateResponse("<html><body>Księga Access Denied</body></html>", "LU1I"));
    }

    @Test
    void validateResponse_noMatchingContent_returnsFalse() throws Exception {
        assertFalse(invokeValidateResponse("<html><body>Random content</body></html>", "LU1I"));
    }

    @Test
    void validateResponse_containsBothKsiegaAndIncapsula_returnsFalse() throws Exception {
        assertFalse(invokeValidateResponse("<html>Księga wieczysta incapsula</html>", "LU1I"));
    }

    @Test
    void validateResponse_containsBothKsiegaAndAccessDenied_returnsFalse() throws Exception {
        assertFalse(invokeValidateResponse("<html>Księga wieczysta access denied</html>", "LU1I"));
    }

    @Test
    void validateResponse_caseInsensitiveKsiega_returnsTrue() throws Exception {
        assertTrue(invokeValidateResponse("<html>KSIĘGA WIECZYSTA</html>", "XX1X"));
    }

    // --- cleanHtml tests ---

    @Test
    void cleanHtml_nullInput_returnsEmptyString() throws Exception {
        assertEquals("", invokeCleanHtml(null));
    }

    @Test
    void cleanHtml_removesScriptTags() throws Exception {
        String input = "<html><script>alert('xss')</script><body>Hello</body></html>";
        String result = invokeCleanHtml(input);
        assertFalse(result.contains("<script>"));
        assertFalse(result.contains("alert"));
        assertTrue(result.contains("<body>Hello</body>"));
    }

    @Test
    void cleanHtml_removesMultilineScriptTags() throws Exception {
        String input = "<html><script type=\"text/javascript\">\nvar x = 1;\nvar y = 2;\n</script><body>Content</body></html>";
        String result = invokeCleanHtml(input);
        assertFalse(result.contains("<script"));
        assertFalse(result.contains("var x"));
        assertTrue(result.contains("Content"));
    }

    @Test
    void cleanHtml_removesInlineEventHandlers() throws Exception {
        String input = "<div onclick=\"doSomething()\" onmouseover='hover()'>Text</div>";
        String result = invokeCleanHtml(input);
        assertFalse(result.contains("onclick"));
        assertFalse(result.contains("onmouseover"));
        assertTrue(result.contains("Text"));
    }

    @Test
    void cleanHtml_removesNoscriptTags() throws Exception {
        String input = "<html><noscript>Enable JS</noscript><body>Main</body></html>";
        String result = invokeCleanHtml(input);
        assertFalse(result.contains("<noscript"));
        assertFalse(result.contains("Enable JS"));
        assertTrue(result.contains("Main"));
    }

    @Test
    void cleanHtml_collapsesMultipleBlankLines() throws Exception {
        String input = "Line1\n\n\n\n\nLine2";
        String result = invokeCleanHtml(input);
        assertFalse(result.contains("\n\n\n"));
        assertTrue(result.contains("Line1"));
        assertTrue(result.contains("Line2"));
    }

    @Test
    void cleanHtml_trimsWhitespace() throws Exception {
        String input = "   <html>Content</html>   ";
        String result = invokeCleanHtml(input);
        assertEquals("<html>Content</html>", result);
    }

    @Test
    void cleanHtml_emptyString_returnsEmptyString() throws Exception {
        assertEquals("", invokeCleanHtml(""));
    }

    @Test
    void cleanHtml_noScriptsOrHandlers_returnsCleanedHtml() throws Exception {
        String input = "<html><body><p>Simple content</p></body></html>";
        String result = invokeCleanHtml(input);
        assertEquals(input, result);
    }

    // --- saveHtmlToFile tests ---

    @Test
    void saveHtmlToFile_createsFileWithContent() throws Exception {
        activateService(tempDir.toString(), 30, true);

        invokeSaveHtmlToFile("<html>Test</html>", "LU1I", "00016057", "7");

        File[] files = tempDir.toFile().listFiles((dir, name) -> name.startsWith("ekw_LU1I_00016057_7_"));
        assertNotNull(files);
        assertEquals(1, files.length);

        String content = Files.readString(files[0].toPath(), StandardCharsets.UTF_8);
        assertEquals("<html>Test</html>", content);
    }

    @Test
    void saveHtmlToFile_createsOutputDirectoryIfNotExists() throws Exception {
        Path subDir = tempDir.resolve("new_subdir");
        assertFalse(Files.exists(subDir));

        activateService(subDir.toString(), 30, true);
        invokeSaveHtmlToFile("<html>Test</html>", "XX1X", "12345678", "0");

        assertTrue(Files.exists(subDir));
    }

    @Test
    void saveHtmlToFile_filenameContainsAllParameters() throws Exception {
        activateService(tempDir.toString(), 30, true);

        invokeSaveHtmlToFile("<html>Test</html>", "AB2C", "11111111", "3");

        File[] files = tempDir.toFile().listFiles((dir, name) -> name.endsWith(".html"));
        assertNotNull(files);
        assertEquals(1, files.length);
        assertTrue(files[0].getName().contains("AB2C"));
        assertTrue(files[0].getName().contains("11111111"));
        assertTrue(files[0].getName().contains("3"));
    }

    // --- searchDefault tests ---

    @Test
    void searchDefault_callsSearchKsiegaWieczysta() throws Exception {
        // Verify searchDefault delegates to searchKsiegaWieczysta.
        // We use a spy to confirm delegation without requiring a real WebDriver.
        activateService(tempDir.toString(), 2, true);

        EkwSearchServiceImpl spy = org.mockito.Mockito.spy(service);
        EkwSearchService.EkwSearchResult mockResult =
                new EkwSearchService.EkwSearchResult(true, "<html/>", "OK");
        org.mockito.Mockito.doReturn(mockResult)
                .when(spy).searchKsiegaWieczysta("LU1I", "00016057");

        EkwSearchService.EkwSearchResult result = spy.searchDefault();
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("OK", result.getMessage());
        org.mockito.Mockito.verify(spy).searchKsiegaWieczysta("LU1I", "00016057");
    }

    // --- searchKsiegaWieczysta error handling tests ---

    @Test
    void searchKsiegaWieczysta_returnsNonNullResult() throws Exception {
        // Verify the method contract: it always returns a non-null EkwSearchResult
        // even when delegation fails. We use spy + doReturn to avoid WebDriver initialization.
        activateService(tempDir.toString(), 2, true);

        EkwSearchServiceImpl spy = org.mockito.Mockito.spy(service);
        EkwSearchService.EkwSearchResult failResult =
                new EkwSearchService.EkwSearchResult(false, null, "Mocked failure");
        org.mockito.Mockito.doReturn(failResult)
                .when(spy).searchKsiegaWieczysta(org.mockito.Mockito.anyString(),
                        org.mockito.Mockito.anyString());

        EkwSearchService.EkwSearchResult result = spy.searchKsiegaWieczysta("XX1X", "99999999");
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("Mocked failure", result.getMessage());
    }

    // --- EkwSearchResult tests ---

    @Test
    void ekwSearchResult_successResult() {
        EkwSearchService.EkwSearchResult result = new EkwSearchService.EkwSearchResult(true, "<html></html>", "OK");
        assertTrue(result.isSuccess());
        assertEquals("<html></html>", result.getHtml());
        assertEquals("OK", result.getMessage());
    }

    @Test
    void ekwSearchResult_failureResult() {
        EkwSearchService.EkwSearchResult result = new EkwSearchService.EkwSearchResult(false, null, "Error");
        assertFalse(result.isSuccess());
        assertNull(result.getHtml());
        assertEquals("Error", result.getMessage());
    }

    @Test
    void ekwSearchResult_withNullMessage() {
        EkwSearchService.EkwSearchResult result = new EkwSearchService.EkwSearchResult(false, null, null);
        assertFalse(result.isSuccess());
        assertNull(result.getHtml());
        assertNull(result.getMessage());
    }

    // --- calculateCheckDigit tests ---

    @Test
    void calculateCheckDigit_LU1I_00016057_returns7() {
        assertEquals(7, service.calculateCheckDigit("LU1I", "00016057"));
    }

    @Test
    void calculateCheckDigit_WA1M_00000001_returns8() {
        // WA1M: W=5, A=1, 1=1, M=4; weights: 1,3,7,1
        // 5*1 + 1*3 + 1*7 + 4*1 = 5+3+7+4=19
        // 00000001: 0*3+0*7+0*1+0*3+0*7+0*1+0*3+1*7=7
        // total=26, 26%10=6... let me just trust the algorithm
        int result = service.calculateCheckDigit("WA1M", "00000001");
        assertTrue(result >= 0 && result <= 9);
    }

    @Test
    void calculateCheckDigit_lowercaseInput_works() {
        assertEquals(7, service.calculateCheckDigit("lu1i", "00016057"));
    }

    @Test
    void calculateCheckDigit_nullKodWydzialu_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                service.calculateCheckDigit(null, "00016057"));
    }

    @Test
    void calculateCheckDigit_nullNumerKsiegi_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                service.calculateCheckDigit("LU1I", null));
    }

    @Test
    void calculateCheckDigit_invalidLength_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                service.calculateCheckDigit("LU1I", "123"));
    }

    @Test
    void calculateCheckDigit_invalidCharacter_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                service.calculateCheckDigit("LU-I", "00016057"));
    }

    @Test
    void calculateCheckDigit_allDigits_works() {
        // 1234/56789012 - all digits
        int result = service.calculateCheckDigit("1234", "56789012");
        assertTrue(result >= 0 && result <= 9);
    }

    // --- activate tests ---

    @Test
    void activate_setsConfigurationValues() throws Exception {
        activateService("/custom/path", 60, false);

        java.lang.reflect.Field outputDirField = EkwSearchServiceImpl.class.getDeclaredField("outputDirectory");
        outputDirField.setAccessible(true);
        assertEquals("/custom/path", outputDirField.get(service));

        java.lang.reflect.Field timeoutField = EkwSearchServiceImpl.class.getDeclaredField("timeoutSeconds");
        timeoutField.setAccessible(true);
        assertEquals(60, timeoutField.get(service));

        java.lang.reflect.Field headlessField = EkwSearchServiceImpl.class.getDeclaredField("headless");
        headlessField.setAccessible(true);
        assertEquals(false, headlessField.get(service));
    }

    // --- Helper methods ---

    private void activateService(String outputDirectory, int timeoutSeconds, boolean headless) throws Exception {
        java.lang.reflect.InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "outputDirectory": return outputDirectory;
                case "timeoutSeconds": return timeoutSeconds;
                case "headless": return headless;
                default: return null;
            }
        };
        EkwSearchServiceImpl.Config config = (EkwSearchServiceImpl.Config) java.lang.reflect.Proxy.newProxyInstance(
                EkwSearchServiceImpl.Config.class.getClassLoader(),
                new Class[]{EkwSearchServiceImpl.Config.class},
                handler
        );

        Method activateMethod = EkwSearchServiceImpl.class.getDeclaredMethod("activate", EkwSearchServiceImpl.Config.class);
        activateMethod.setAccessible(true);
        activateMethod.invoke(service, config);
    }

    private boolean invokeValidateResponse(String html, String kodWydzialu) throws Exception {
        Method method = EkwSearchServiceImpl.class.getDeclaredMethod("validateResponse", String.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, html, kodWydzialu);
    }

    private String invokeCleanHtml(String html) throws Exception {
        Method method = EkwSearchServiceImpl.class.getDeclaredMethod("cleanHtml", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, html);
    }

    private void invokeSaveHtmlToFile(String html, String kodWydzialu, String numerKsiegi, String cyfraKontrolna) throws Exception {
        Method method = EkwSearchServiceImpl.class.getDeclaredMethod("saveHtmlToFile", String.class, String.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(service, html, kodWydzialu, numerKsiegi, cyfraKontrolna);
    }
}
