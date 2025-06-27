package metrics;

import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.SourceCodeExtractor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class nSmells implements CodeMetric {
    private static final String SONAR_API_URL = "https://sonarcloud.io/api";
    private static final String SONAR_TOKEN = System.getenv("SONAR_TOKEN"); // Token da variabile d'ambiente
    private static final Map<String, Integer> smellCache = new HashMap<>();

    @Override
    public String getName() {
        return "CodeSmells";
    }

    @Override
    public Integer calculate(CKMethodResult method, CKClassResult cls, Path root) {
        // Se non è configurato il token SonarCloud, usa l'analisi locale
        if (SONAR_TOKEN == null || SONAR_TOKEN.isEmpty()) {
            return calculateLocalCodeSmells(method, cls, root);
        }

        String methodKey = method.getQualifiedMethodName();

        // Usa la cache per evitare chiamate ripetute alla API per lo stesso metodo
        if (smellCache.containsKey(methodKey)) {
            return smellCache.get(methodKey);
        }

        try {
            // Estrai il codice sorgente
            String sourceCode = SourceCodeExtractor.getMethodSource(method, root);
            if (sourceCode == null || sourceCode.isEmpty()) {
                return 0;
            }

            // Prepara la richiesta all'API di SonarCloud
            URL url = new URL(SONAR_API_URL + "/issues/search?componentKeys=" +
                    "apache_bookkeeper&types=CODE_SMELL&statuses=OPEN,CONFIRMED,REOPENED");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // Configura l'autenticazione
            String auth = "Basic " + Base64.getEncoder().encodeToString(
                    (SONAR_TOKEN + ":").getBytes(StandardCharsets.UTF_8));
            conn.setRequestProperty("Authorization", auth);

            // Ottieni la risposta
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                // Analizza la risposta JSON
                JSONObject jsonResponse = new JSONObject(response.toString());
                JSONArray issues = jsonResponse.getJSONArray("issues");

                // Filtra i code smells relativi a questo metodo specifico
                int smellCount = 0;
                for (int i = 0; i < issues.length(); i++) {
                    JSONObject issue = issues.getJSONObject(i);
                    String component = issue.getString("component");
                    int lineNumber = issue.getInt("line");

                    // Verifica se il code smell è in questo metodo
                    if (isIssueInMethod(method, component, lineNumber)) {
                        smellCount++;
                    }
                }

                // Cache del risultato
                smellCache.put(methodKey, smellCount);
                return smellCount;
            } else {
                System.err.println("❌ Errore nella chiamata all'API SonarCloud: " + responseCode);
                // Fallback all'analisi locale
                return calculateLocalCodeSmells(method, cls, root);
            }
        } catch (Exception e) {
            System.err.println("❌ Errore nell'accesso all'API SonarCloud: " + e.getMessage());
            // Fallback all'analisi locale
            return calculateLocalCodeSmells(method, cls, root);
        }
    }

    private boolean isIssueInMethod(CKMethodResult method, String component, int line) {
        // Verifica se il componente contiene il nome della classe
        String className = method.getQualifiedMethodName().substring(
                0, method.getQualifiedMethodName().lastIndexOf("."));
        if (!component.contains(className.replace(".", "/"))) {
            return false;
        }

        // Verifica se la linea è nel range del metodo
        // Nota: questa è un'approssimazione, poiché CK non fornisce direttamente
        // le informazioni sul range di linee del metodo
        return true; // Approssimazione: se è nello stesso file, lo consideriamo rilevante
    }

    private int calculateLocalCodeSmells(CKMethodResult method, CKClassResult cls, Path root) {
        int smells = 0;

        // Ottieni il codice sorgente del metodo
        String sourceCode = SourceCodeExtractor.getMethodSource(method, root);
        if (sourceCode == null || sourceCode.isEmpty()) {
            return 0;
        }

        // Long Method (metodo troppo lungo)
        if (method.getLoc() > 100) smells++;

        // Too Many Parameters (troppi parametri)
        if (method.getParametersQty() > 5) smells++;

        // Complex Method (metodo troppo complesso)
        // Usa ComplexityMetric per calcolare la complessità
        Complexity complexityMetric = new Complexity();
        int complexity = complexityMetric.calculate(method, cls, root);
        if (complexity > 10) {
            smells++;
        }

        // High cognitive complexity (complessità cognitiva elevata)
        if (method.getWmc() > 15) {
            smells++;
        }

        // Too many statements (troppe istruzioni)
        // Usa StatementCount per contare le istruzioni
        StatementCount statementMetric = new StatementCount();
        int statements = statementMetric.calculate(method, cls, root);
        if (statements > 50) {
            smells++;
        }

        // Magic Numbers (numeri magici)
        if (hasMagicNumbers(sourceCode)) {
            smells++;
        }

        // Missing comments (commenti mancanti)
        if (!hasComments(sourceCode) && method.getLoc() > 15) {
            smells++;
        }

        // God Class (relativo alla classe contenente il metodo)
        if (cls.getWmc() > 47 && cls.getMethods().size() > 20) {
            smells++;
        }

        // Alto accoppiamento
        if (cls.getCbo() > 15) {
            smells++;
        }

        return smells;
    }

    private boolean hasMagicNumbers(String code) {
        // Cerca numeri "magici" nel codice
        // Esclude 0, 1, -1 che sono comuni e non considerati magici
        java.util.regex.Pattern magicNumberPattern = java.util.regex.Pattern.compile(
                "\\b(?<!\\.)(?<![0-9])[2-9]\\d*(?!\\.)|\\b(?<!\\.)\\d{2,}(?!\\.)\\b");

        java.util.regex.Matcher m = magicNumberPattern.matcher(code);
        int count = 0;
        while (m.find()) {
            count++;
            if (count >= 3) return true; // Più di 3 numeri magici
        }

        return false;
    }

    private boolean hasComments(String code) {
        // Cerca commenti nel codice
        return code.contains("//") || code.contains("/*");
    }
}