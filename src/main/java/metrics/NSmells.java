package metrics;

import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calcola il numero di code smells per ogni metodo tramite API SonarCloud.
 */
public class NSmells implements CodeMetric {
    private static final Logger LOG = LoggerFactory.getLogger(NSmells.class);
    private final String sonarToken;
    private final String projectKey;
    private final HttpClient httpClient;

    // Mappa: file Java -> lista di code smell (posizionati per range di linee)
    private final Map<String, List<CodeSmell>> codeSmellsByFile;

    /**
     * Costruttore NSmells.
     *
     * @param projectKey la chiave del progetto su SonarCloud
     * @param sonarToken il token di autenticazione per SonarCloud API
     */
    public NSmells(String projectKey, String sonarToken) {
        this.projectKey = projectKey;
        this.sonarToken = sonarToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.codeSmellsByFile = new ConcurrentHashMap<>();
    }

    @Override
    public String getName() {
        return "nSmells";
    }

    /**
     * Restituisce il numero di code smells per un metodo.
     */
    @Override
    public Integer calculate(CKMethodResult method, CKClassResult cls, Path root) {
        // Popola la cache solo una volta per run
        if (codeSmellsByFile.isEmpty()) {
            try {
                populateCodeSmells();
            } catch (IOException | InterruptedException e) {
                LOG.error("Errore durante il recupero dei code smells da SonarCloud", e);
                return 0;
            }
        }

        // Recupera i code smell del file di questa classe
        String fileName = getFileNameFromClassName(cls.getClassName());
        List<CodeSmell> smells = codeSmellsByFile.getOrDefault(fileName, Collections.emptyList());

        int methodStart = method.getStartLine();
        int methodEnd = methodStart + method.getLoc();
        int count = 0;

        for (CodeSmell cs : smells) {
            // Se il code smell cade nel range del metodo, conta!
            if (cs.startLine >= methodStart && cs.endLine <= methodEnd) {
                count++;
            }
        }
        return count;
    }

    /**
     * Popola la mappa file->list<CodeSmell> tramite SonarCloud.
     */
    private void populateCodeSmells() throws IOException, InterruptedException {
        int page = 1;
        int pageSize = 500;
        boolean hasMoreResults = true;

        while (hasMoreResults) {
            JSONObject response = fetchIssuesFromSonarCloud(page, pageSize);
            JSONArray issues = response.getJSONArray("issues");

            if (issues.length() == 0) {
                hasMoreResults = false;
            } else {
                processIssues(issues);
                page++;
            }
        }
        LOG.info("Caricati {} file con code smells da SonarCloud", codeSmellsByFile.size());
    }

    /**
     * Chiama le SonarCloud API REST per scaricare le issues CODE_SMELL.
     */
    private JSONObject fetchIssuesFromSonarCloud(int page, int pageSize) throws IOException, InterruptedException {
        String auth = Base64.getEncoder().encodeToString((sonarToken + ":").getBytes());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://sonarcloud.io/api/issues/search?" +
                        "componentKeys=" + projectKey +
                        "&types=CODE_SMELL" +
                        "&p=" + page +
                        "&ps=" + pageSize))
                .header("Authorization", "Basic " + auth)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            LOG.error("Errore nella chiamata API SonarCloud: {}", response.statusCode());
            throw new IOException("SonarCloud API ha restituito status code: " + response.statusCode());
        }

        return new JSONObject(response.body());
    }

    /**
     * Estrae tutti i code smell da una pagina di issues e li aggiunge alla mappa per file.
     */
    private void processIssues(JSONArray issues) {
        for (int i = 0; i < issues.length(); i++) {
            JSONObject issue = issues.getJSONObject(i);

            String component = issue.getString("component"); // es. "projectKey:src/main/java/package/Class.java"
            String fileName = extractFileNameFromComponent(component);

            if (fileName == null) continue;

            if (issue.has("textRange")) {
                JSONObject textRange = issue.getJSONObject("textRange");
                int startLine = textRange.optInt("startLine", -1);
                int endLine = textRange.optInt("endLine", -1);
                if (startLine == -1 || endLine == -1) continue;

                CodeSmell cs = new CodeSmell(startLine, endLine);

                codeSmellsByFile.computeIfAbsent(fileName, k -> new ArrayList<>()).add(cs);
            }
        }
    }

    /**
     * Estrae solo il nome del file (con estensione) dal campo component SonarCloud.
     */
    private String extractFileNameFromComponent(String component) {
        // Esempio component: "org_projectKey:src/main/java/com/example/MyClass.java"
        String[] parts = component.split(":");
        if (parts.length < 2) return null;
        String filePath = parts[1];
        String[] pathParts = filePath.replace("\\", "/").split("/");
        if (pathParts.length == 0) return null;
        return pathParts[pathParts.length - 1];
    }

    /**
     * Dato il nome della classe, estrae il nome del file java (semplice).
     */
    private String getFileNameFromClassName(String className) {
        // Prende solo l'ultima parte se c'è il package
        String[] pathSegments = className.split("\\.");
        String simpleName = pathSegments[pathSegments.length - 1];
        return simpleName + ".java";
    }

    /**
     * Rappresenta un code smell associato a una posizione nel file.
     */
    private static class CodeSmell {
        final int startLine;
        final int endLine;

        CodeSmell(int startLine, int endLine) {
            this.startLine = startLine;
            this.endLine = endLine;
        }
    }
}
