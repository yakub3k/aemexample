package com.example.core.servlets;

import com.example.core.services.EkwSearchService;
import com.example.core.services.EkwSearchService.EkwSearchResult;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serial;

/**
 * Servlet bound to the {@code com.example/components/ekwsearch} resource type.
 * Accepts three GET parameters needed to search the land registry (Księga Wieczysta):
 * <ul>
 *   <li>{@code kodWydzialu} – court department code, e.g. "LU1I"</li>
 *   <li>{@code numerKsiegi} – registry number, e.g. "00016057"</li>
 *   <li>{@code cyfraKontrolna} – check digit, e.g. "7"</li>
 * </ul>
 * Delegates the search to {@link EkwSearchService} and renders the result page.
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
        resourceTypes = "com.example/components/ekwsearch",
        methods = HttpConstants.METHOD_GET,
        extensions = "html"
)
@ServiceDescription("EKW Search Servlet – Księgi Wieczyste lookup")
public class EkwSearchServlet extends SlingSafeMethodsServlet {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(EkwSearchServlet.class);

    @Reference
    private transient EkwSearchService ekwSearchService;

    @SuppressWarnings("CQRules:CQBP-44---LogInfoInGetOrHeadRequests")
    @Override
    protected void doGet(final SlingHttpServletRequest request,
                         final SlingHttpServletResponse response) throws ServletException, IOException {

        String kodWydzialu = request.getParameter("kodWydzialu");
        String numerKsiegi = request.getParameter("numerKsiegi");

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter writer = response.getWriter();

        boolean hasAllParams = StringUtils.isNotBlank(kodWydzialu)
                && StringUtils.isNotBlank(numerKsiegi);

        writer.println("<!DOCTYPE html>");
        writer.println("<html lang=\"pl\">");
        writer.println("<head>");
        writer.println("  <meta charset=\"UTF-8\"/>");
        writer.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/>");
        writer.println("  <title>Wyszukiwarka Ksiąg Wieczystych</title>");
        writer.println("  <style>");
        writer.println("    body { font-family: Arial, sans-serif; margin: 2rem; background: #f5f5f5; }");
        writer.println("    .container { max-width: 800px; margin: 0 auto; background: #fff; padding: 2rem; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,.1); }");
        writer.println("    h1 { color: #333; }");
        writer.println("    .search-form { margin-bottom: 2rem; }");
        writer.println("    .search-form label { display: block; margin-top: 1rem; font-weight: bold; }");
        writer.println("    .search-form input { padding: .5rem; width: 100%; box-sizing: border-box; }");
        writer.println("    .search-form button { margin-top: 1rem; padding: .6rem 2rem; background: #0066cc; color: #fff; border: none; border-radius: 4px; cursor: pointer; font-size: 1rem; }");
        writer.println("    .search-form button:hover { background: #004fa3; }");
        writer.println("    .result { margin-top: 2rem; padding: 1rem; border: 1px solid #ddd; border-radius: 4px; }");
        writer.println("    .result.success { border-color: #28a745; background: #eaffea; }");
        writer.println("    .result.error { border-color: #dc3545; background: #fff0f0; }");
        writer.println("    .result-html { margin-top: 1rem; }");
        writer.println("  </style>");
        writer.println("</head>");
        writer.println("<body>");
        writer.println("  <div class=\"container\">");
        writer.println("    <h1>Wyszukiwarka Ksiąg Wieczystych (EKW)</h1>");

        // Search form
        writer.println("    <form class=\"search-form\" method=\"get\">");
        writer.println("      <label for=\"kodWydzialu\">Kod wydziału</label>");
        writer.println("      <input id=\"kodWydzialu\" name=\"kodWydzialu\" type=\"text\" placeholder=\"np. LU1I\" value=\""
                + escapeHtml(kodWydzialu) + "\"/>");
        writer.println("      <label for=\"numerKsiegi\">Numer księgi</label>");
        writer.println("      <input id=\"numerKsiegi\" name=\"numerKsiegi\" type=\"text\" placeholder=\"np. 00016057\" value=\""
                + escapeHtml(numerKsiegi) + "\"/>");
        writer.println("      <button type=\"submit\">Szukaj</button>");
        writer.println("    </form>");

        // Perform search if all parameters are provided
        if (hasAllParams) {
            LOG.info("EKW search requested – kodWydzialu={}, numerKsiegi={}",
                    kodWydzialu, numerKsiegi);

            try {
                EkwSearchResult result = ekwSearchService.searchKsiegaWieczysta(
                        kodWydzialu, numerKsiegi);

                String cssClass = result.isSuccess() ? "success" : "error";

                writer.println("    <div class=\"result " + cssClass + "\">");
                writer.println("      <h2>Wynik wyszukiwania: " + escapeHtml(kodWydzialu) + "/"
                        + escapeHtml(numerKsiegi) + "</h2>");
                writer.println("      <p><strong>Status:</strong> " + (result.isSuccess() ? "Sukces" : "Błąd") + "</p>");

                if (StringUtils.isNotBlank(result.getMessage())) {
                    writer.println("      <p><strong>Komunikat:</strong> " + escapeHtml(result.getMessage()) + "</p>");
                }

                if (StringUtils.isNotBlank(result.getHtml())) {
                    writer.println("      <div class=\"result-html\">");
                    writer.println("        <h3>Treść księgi wieczystej</h3>");
                    writer.println(result.getHtml());
                    writer.println("      </div>");
                }

                writer.println("    </div>");
                // to do fix exception
            } catch (Exception e) {
                LOG.error("Error during EKW search", e);
                writer.println("    <div class=\"result error\">");
                writer.println("      <h2>Błąd wyszukiwania</h2>");
                writer.println("      <p>" + escapeHtml(e.getMessage()) + "</p>");
                writer.println("    </div>");
            }
        }

        writer.println("  </div>");
        writer.println("</body>");
        writer.println("</html>");
    }

    private static String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                     .replace("<", "&lt;")
                     .replace(">", "&gt;")
                     .replace("\"", "&quot;")
                     .replace("'", "&#39;");
    }
}
