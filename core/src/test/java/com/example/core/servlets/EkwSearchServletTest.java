package com.example.core.servlets;

import com.example.core.services.EkwSearchService;
import com.example.core.services.EkwSearchService.EkwSearchResult;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EkwSearchServletTest {

    @InjectMocks
    private EkwSearchServlet servlet;

    @Mock
    private EkwSearchService ekwSearchService;

    @Mock
    private SlingHttpServletRequest request;

    @Mock
    private SlingHttpServletResponse response;

    private StringWriter stringWriter;

    @BeforeEach
    void setUp() throws Exception {
        stringWriter = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
    }

    @Test
    void doGet_withoutParams_rendersFormOnly() throws Exception {
        when(request.getParameter("kwDepartment")).thenReturn(null);
        when(request.getParameter("kwNumber")).thenReturn(null);

        servlet.doGet(request, response);

        String html = stringWriter.toString();
        verify(response).setContentType("text/html;charset=UTF-8");
        assertTrue(html.contains("Wyszukiwarka Ksiąg Wieczystych"));
        assertTrue(html.contains("<form"));
        assertTrue(html.contains("kwDepartment"));
        assertFalse(html.contains("Wynik wyszukiwania"));
        verifyNoInteractions(ekwSearchService);
    }

    @Test
    void doGet_withAllParams_callsServiceAndRendersResult() throws Exception {
        when(request.getParameter("kwDepartment")).thenReturn("LU1I");
        when(request.getParameter("kwNumber")).thenReturn("00016057");

        EkwSearchResult result = new EkwSearchResult(true, "<p>Dane księgi</p>", "Znaleziono");
        when(ekwSearchService.searchKW("LU1I", "00016057")).thenReturn(result);

        servlet.doGet(request, response);

        String html = stringWriter.toString();
        assertTrue(html.contains("Wynik wyszukiwania: LU1I/00016057"));
        assertTrue(html.contains("Sukces"));
        assertTrue(html.contains("Znaleziono"));
        assertTrue(html.contains("<p>Dane księgi</p>"));
        verify(ekwSearchService).searchKW("LU1I", "00016057");
    }

    @Test
    void doGet_withAllParams_serviceReturnsError() throws Exception {
        when(request.getParameter("kwDepartment")).thenReturn("XX1X");
        when(request.getParameter("kwNumber")).thenReturn("99999999");

        EkwSearchResult result = new EkwSearchResult(false, null, "Nie znaleziono");
        when(ekwSearchService.searchKW("XX1X", "99999999")).thenReturn(result);

        servlet.doGet(request, response);

        String html = stringWriter.toString();
        assertTrue(html.contains("Błąd"));
        assertTrue(html.contains("Nie znaleziono"));
    }

    @Test
    void doGet_withAllParams_serviceThrowsException() throws Exception {
        when(request.getParameter("kwDepartment")).thenReturn("LU1I");
        when(request.getParameter("kwNumber")).thenReturn("00016057");

        when(ekwSearchService.searchKW(anyString(), anyString()))
                .thenThrow(new RuntimeException("Connection timeout"));

        servlet.doGet(request, response);

        String html = stringWriter.toString();
        assertTrue(html.contains("Błąd wyszukiwania"));
        assertTrue(html.contains("Connection timeout"));
    }

    @Test
    void doGet_withPartialParams_doesNotCallService() throws Exception {
        when(request.getParameter("kwDepartment")).thenReturn("LU1I");
        when(request.getParameter("kwNumber")).thenReturn(null);

        servlet.doGet(request, response);

        verifyNoInteractions(ekwSearchService);
    }

    @Test
    void doGet_withBlankParams_doesNotCallService() throws Exception {
        when(request.getParameter("kwDepartment")).thenReturn("LU1I");
        when(request.getParameter("kwNumber")).thenReturn("  ");

        servlet.doGet(request, response);

        verifyNoInteractions(ekwSearchService);
    }

    @Test
    void doGet_escapesHtmlInParameters() throws Exception {
        when(request.getParameter("kwDepartment")).thenReturn("<script>alert(1)</script>");
        when(request.getParameter("kwNumber")).thenReturn("00016057");

        servlet.doGet(request, response);

        String html = stringWriter.toString();
        assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }
}
