package csv;

import com.opencsv.CSVWriter;
import metrics.CodeMetric;
import model.ReleaseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Writer per dati di metriche di codice in formato CSV
 */
public class Writer {
    private static final Logger LOG = LoggerFactory.getLogger(Writer.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Path filePath;
    private final Properties config;
    private final List<String> columns = new ArrayList<>();

    /**
     * Costruttore con path del file e configurazione
     */
    public Writer(Path filePath, Properties config) {
        this.filePath = filePath;
        this.config = config;

        // Inizializza le colonne di base
        initBaseColumns();
    }

    /**
     * Costruttore con solo path del file
     */
    public Writer(Path filePath) {
        this(filePath, new Properties());
    }

    /**
     * Inizializza le colonne di base
     */
    private void initBaseColumns() {
        // Colonne obbligatorie
        columns.add("project");
        columns.add("package");
        columns.add("class");
        columns.add("method");
        columns.add("release");
        columns.add("release_date");
        columns.add("loc");
        columns.add("cc");
    }

    /**
     * Aggiunge colonne per metriche personalizzate
     */
    public void addMetricColumns(List<CodeMetric> metrics) {
        for (CodeMetric metric : metrics) {
            columns.add(metric.getName());
            LOG.debug("Aggiunta colonna per metrica: {}", metric.getName());
        }
    }

    /**
     * Crea l'intestazione del file CSV
     */
    public void createHeader() throws IOException {
        LOG.info("Creazione intestazione CSV in: {}", filePath);

        // Crea le directory necessarie
        Files.createDirectories(filePath.getParent());

        try (CSVWriter writer = new CSVWriter(
                new OutputStreamWriter(
                    Files.newOutputStream(
                        filePath,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING),
                    StandardCharsets.UTF_8))) {

            writer.writeNext(columns.toArray(new String[0]));
            LOG.info("Intestazione CSV creata con {} colonne", columns.size());
        } catch (IOException e) {
            LOG.error("Errore nella creazione dell'intestazione CSV: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Verifica se l'intestazione è già stata creata
     */
    public boolean headerExists() {
        return Files.exists(filePath);
    }

    /**
     * Appende una riga al file CSV
     */
    public void appendRow(List<String> values) throws IOException {
        if (!headerExists()) {
            createHeader();
        }

        try (CSVWriter writer = new CSVWriter(
                new OutputStreamWriter(
                    Files.newOutputStream(filePath, StandardOpenOption.APPEND),
                    StandardCharsets.UTF_8))) {

            writer.writeNext(values.toArray(new String[0]));
        } catch (IOException e) {
            LOG.error("Errore nell'append della riga CSV: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Appende un batch di righe al file CSV
     */
    public void appendRows(List<List<String>> rows) throws IOException {
        if (!headerExists()) {
            createHeader();
        }

        try (CSVWriter writer = new CSVWriter(
                new OutputStreamWriter(
                    Files.newOutputStream(filePath, StandardOpenOption.APPEND),
                    StandardCharsets.UTF_8))) {

            for (List<String> row : rows) {
                writer.writeNext(row.toArray(new String[0]));
            }

            LOG.info("Scritte {} righe nel file CSV", rows.size());
        } catch (IOException e) {
            LOG.error("Errore nell'append di {} righe CSV: {}", rows.size(), e.getMessage());
            throw e;
        }
    }

    /**
     * Helper per creare una riga per metrica di base
     */
    public List<String> createBaseRow(String projectName, String packageName,
                                    String className, String methodName,
                                    ReleaseInfo release, int loc, int cc) {
        List<String> row = new ArrayList<>();

        row.add(projectName);
        row.add(packageName);
        row.add(className);
        row.add(methodName);
        row.add(release.getName());
        row.add(release.getDate().format(DATE_FORMATTER));
        row.add(String.valueOf(loc));
        row.add(String.valueOf(cc));

        return row;
    }

    /**
     * Ottiene il path del file
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * Ottiene la lista delle colonne
     */
    public List<String> getColumns() {
        return Collections.unmodifiableList(columns);
    }
}