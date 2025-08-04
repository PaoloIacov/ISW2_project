package metrics;

import com.github.mauricioaniche.ck.CK;
import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import com.github.mauricioaniche.ck.CKNotifier;
import csv.Writer;
import model.ReleaseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Classe principale che calcola le metriche di codice per un progetto.
 * Configurazione automatica tramite info.properties.
 */
public class MetricsComputer {
    private static final Logger LOG = LoggerFactory.getLogger(MetricsComputer.class);
    private static final String PROPERTIES_FILE = "src/main/resources/info.properties";

    private final Path projectPath;
    private final String projectName;
    private final ReleaseInfo releaseInfo;
    private final Path outputPath;
    private final List<CodeMetric> metrics;
    private final Writer writer;

    /**
     * Costruttore principale di MetricsComputer
     */
    public MetricsComputer(Path projectPath, String projectName, ReleaseInfo releaseInfo, Path outputPath) {
        this.projectPath = projectPath;
        this.projectName = projectName;
        this.releaseInfo = releaseInfo;
        this.outputPath = outputPath;
        this.metrics = initializeMetrics();
        this.writer = new Writer(outputPath);

        // Aggiungi le colonne per tutte le metriche
        writer.addMetricColumns(metrics);
    }

    /**
     * Inizializza tutte le metriche disponibili
     */
    private List<CodeMetric> initializeMetrics() {
        List<CodeMetric> metricsList = new ArrayList<>();

        // Metriche di base
        metricsList.add(new Complexity());
        metricsList.add(new HalsteadVolume());

        // Metriche che richiedono storico Git
        metricsList.add(new Age(projectPath));
        metricsList.add(new MethodHistory(projectPath));
        metricsList.add(new NAuthors(projectPath));

        // Metriche di evoluzione
        Path historyDir = projectPath.resolve("../history").normalize();
        metricsList.add(new Churn(historyDir));
        metricsList.add(new LOCAdded(historyDir));

        // Metriche di qualità del codice
        metricsList.add(new DuplicationPercent());

        // SonarCloud (se configurato)
        try {
            Properties props = new Properties();
            try (InputStream input = new FileInputStream(PROPERTIES_FILE)) {
                props.load(input);
            }

            String sonarToken = props.getProperty("sonar.token");
            if (sonarToken != null && !sonarToken.isEmpty()) {
                metricsList.add(new NSmells(projectName, sonarToken));
            }
        } catch (Exception e) {
            LOG.warn("Impossibile configurare NSmells: {}", e.getMessage());
        }

        return metricsList;
    }

    /**
     * Esegue il calcolo delle metriche per tutti i metodi nel progetto
     */
    public void compute() throws IOException {
        LOG.info("Avvio calcolo metriche per il progetto {} (release {})", projectName, releaseInfo.getName());

        // Crea l'header del file CSV
        writer.createHeader();

        // Batch di righe da scrivere
        List<List<String>> rows = new ArrayList<>();

        new CK().calculate(projectPath.toString(), new CKNotifier() {
            @Override
            public void notify(CKClassResult result) {
                String packageName = extractPackageName(result.getClassName());
                String className = extractSimpleClassName(result.getClassName());

                for (CKMethodResult method : result.getMethods()) {
                    try {
                        // Crea la riga base con le informazioni del metodo
                        List<String> row = writer.createBaseRow(
                                projectName,
                                packageName,
                                className,
                                method.getMethodName(),
                                releaseInfo,
                                method.getLoc(),
                                method.getWmc()
                        );

                        // Calcola e aggiungi ogni metrica alla riga
                        for (CodeMetric metric : metrics) {
                            try {
                                Object value = metric.calculate(method, result, projectPath);
                                row.add(value != null ? value.toString() : "");
                            } catch (Exception e) {
                                LOG.error("Errore nel calcolo della metrica {} per {}.{}: {}",
                                        metric.getName(), className, method.getMethodName(), e.getMessage());
                                row.add("");
                            }
                        }

                        rows.add(row);

                        // Scrivi a batch per migliorare le prestazioni
                        if (rows.size() >= 100) {
                            writer.appendRows(rows);
                            rows.clear();
                        }
                    } catch (Exception e) {
                        LOG.error("Errore nell'elaborazione del metodo {}.{}: {}",
                                className, method.getMethodName(), e.getMessage());
                    }
                }
            }
        });

        // Scrivi le righe rimanenti
        if (!rows.isEmpty()) {
            writer.appendRows(rows);
        }

        LOG.info("Calcolo metriche completato. Output salvato in: {}", outputPath);
    }

    /**
     * Estrae il nome del package dal nome completo della classe
     */
    private String extractPackageName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        if (lastDot > 0) {
            return fullyQualifiedName.substring(0, lastDot);
        }
        return "";
    }

    /**
     * Estrae il nome semplice della classe dal nome completo
     */
    private String extractSimpleClassName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        if (lastDot > 0) {
            return fullyQualifiedName.substring(lastDot + 1);
        }
        return fullyQualifiedName;
    }

    /**
     * Punto di ingresso per l'esecuzione
     */
    public static void main(String[] args) {
        try {
            // Carica le configurazioni dal file properties
            Properties props = new Properties();
            try (InputStream input = new FileInputStream(PROPERTIES_FILE)) {
                props.load(input);
            }

            // Recupera i valori di configurazione
            String repoPath = props.getProperty("info.repo.path");
            String projectName = props.getProperty("info.name");
            String outputPathStr = props.getProperty("info.output.path", "./metrics_output.csv");

            if (repoPath == null || projectName == null) {
                LOG.error("Proprietà mancanti nel file {}. Sono richiesti info.repo.path e info.name", PROPERTIES_FILE);
                System.exit(1);
            }

            Path projectPath = Paths.get(repoPath);
            Path outputPath = Paths.get(outputPathStr, "metrics_" + projectName + ".csv");

            // Usa la release corrente con la data odierna
            String releaseName = "current";
            LocalDate releaseDate = LocalDate.now();

            // Crea l'oggetto ReleaseInfo
            ReleaseInfo releaseInfo = new ReleaseInfo(projectName, releaseName, releaseDate);

            // Crea e avvia l'elaborazione
            MetricsComputer computer = new MetricsComputer(projectPath, projectName, releaseInfo, outputPath);
            computer.compute();

            System.out.println("Metriche calcolate con successo e salvate in " + outputPath);

        } catch (Exception e) {
            LOG.error("Errore durante l'analisi: {}", e.getMessage(), e);
            System.err.println("Errore durante l'analisi: " + e.getMessage());
            System.exit(1);
        }
    }
}