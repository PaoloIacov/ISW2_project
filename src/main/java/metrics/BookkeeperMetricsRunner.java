package metrics;

import com.github.mauricioaniche.ck.CK;
import com.github.mauricioaniche.ck.CKNotifier;
import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import model.ReleaseInfo;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Esegue CK sul codice sorgente di BookKeeper e
 * aggiunge i dati di metodi delle release al CSV esistente.
 * Utilizza OpenCSV per una gestione robusta dei file CSV.
 */
public class BookkeeperMetricsRunner {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static void main(String[] args) {
        /* ------------ configurazione -------------- */
        String projectDir = args.length > 0 ? args[0] : "/Users/iacov/ISW2_Project_Falessi/ck/bookkeeper";    // clone locale
        String subDir     = args.length > 1 ? args[1] : "";                 // lasciarlo vuoto => tutto il repo
        String outputCsvPath = args.length > 2 ? args[2] : "bookkeeper_metrics.csv";
        String inputCsvPath = args.length > 3 ? args[3] : "/Users/iacov/Library/Mobile Documents/com~apple~CloudDocs/BOOKKEEPERVersionInfo.csv";
        /* ------------------------------------------ */

        try {
            // Verifica che il repository git esista e contiene i tag
            ensureRepositoryExists(projectDir);

            // Debug: mostra alcuni tag disponibili
            System.out.println("📋 Tag disponibili nel repository:");
            listAvailableTags(projectDir);

            // Leggi le release dal file di informazioni sulle release
            List<ReleaseInfo> releases = readReleasesFromCsv(inputCsvPath);

            if (releases.isEmpty()) {
                System.out.println("❌ Nessuna release trovata in " + inputCsvPath);
                return;
            }

            // Usa tutte le release presenti nel CSV
            List<ReleaseInfo> selectedReleases = new ArrayList<>(releases);
            System.out.println("📊 Analisi delle seguenti release (" + selectedReleases.size() + "/" + releases.size() + "):");
            for (ReleaseInfo release : selectedReleases) {
                System.out.println("- " + release.getName() + " (" + release.getDate() + ")");
            }

            // Prepara il file di output
            File csvFile = new File(outputCsvPath);
            boolean fileExists = csvFile.exists();

            int successfulReleases = 0;
            // Per ogni release selezionata, estrai le metriche
            for (ReleaseInfo release : selectedReleases) {
                System.out.println("\n🔄 Elaborazione release: " + release.getName());

                // Checkout alla versione richiesta
                try {
                    gitCheckout(projectDir, release.getName());
                    successfulReleases++;
                } catch (Exception e) {
                    System.err.println("❌ Errore durante il checkout della release " + release.getName() + ": " + e.getMessage());
                    continue;
                }

                // Analizza la release corrente
                try {
                    processRelease(release, projectDir, subDir, outputCsvPath, fileExists);
                    // Se è la prima release elaborata, il file ora esiste
                    fileExists = true;
                } catch (Exception e) {
                    System.err.println("❌ Errore durante l'elaborazione della release " + release.getName() + ": " + e.getMessage());
                }
            }

            System.out.println("\n✅ Elaborazione completata per " + successfulReleases + " release su " + selectedReleases.size());

        } catch (Exception e) {
            System.err.println("❌ Errore durante l'esecuzione: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void ensureRepositoryExists(String projectDir) throws IOException, InterruptedException {
        Path path = Path.of(projectDir);

        if (!Files.exists(path)) {
            System.out.println("🔄 Il repository BookKeeper non esiste, lo clono da GitHub...");
            Files.createDirectories(path.getParent());

            ProcessBuilder pbClone = new ProcessBuilder(
                    "git", "clone", "https://github.com/apache/bookkeeper.git", projectDir
            );
            pbClone.inheritIO();
            Process process = pbClone.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new IOException("Errore nel clonare il repository (codice: " + exitCode + ")");
            }

            System.out.println("✅ Repository clonato correttamente");
        }

        // Assicurati di avere tutti i tag
        System.out.println("🔄 Aggiorno il repository e scarico tutti i tag...");

        ProcessBuilder pbFetch = new ProcessBuilder(
                "git", "fetch", "--all", "--tags"
        );
        pbFetch.directory(new File(projectDir));
        pbFetch.inheritIO();
        Process process = pbFetch.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            System.out.println("⚠️ Avviso: problemi nell'aggiornare il repository (codice: " + exitCode + ")");
        } else {
            System.out.println("✅ Repository aggiornato");
        }
    }

    private static List<String> listAvailableTags(String projectDir) {
        List<String> tags = new ArrayList<>();
        try {
            // Prova a usare git ls-remote per vedere i tag remoti
            ProcessBuilder pb = new ProcessBuilder(
                    "git", "ls-remote", "--tags", "origin");
            pb.directory(new File(projectDir));
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Il formato è: hash    refs/tags/nome-tag
                    if (line.contains("refs/tags/") && !line.contains("^{}")) {
                        String tag = line.split("refs/tags/")[1].trim();
                        tags.add(tag);
                    }
                }
            }

            process.waitFor();

            // Se non ha funzionato, prova con il metodo locale
            if (tags.isEmpty()) {
                System.out.println("  Provo con git tag locale...");
                pb = new ProcessBuilder("git", "tag", "-l");
                pb.directory(new File(projectDir));
                process = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        tags.add(line.trim());
                    }
                }

                process.waitFor();
            }

            if (tags.isEmpty()) {
                System.out.println("  Nessun tag trovato");
            } else {
                List<String> displayTags = tags.size() > 5 ? tags.subList(0, 5) : tags;
                System.out.println("  " + String.join(", ", displayTags) +
                        (tags.size() > 5 ? "... (" + tags.size() + " tag totali)" : ""));
            }
        } catch (Exception e) {
            System.err.println("  Impossibile leggere i tag: " + e.getMessage());
        }
        return tags;
    }

    private static List<ReleaseInfo> readReleasesFromCsv(String csvPath) throws IOException {
        List<ReleaseInfo> releases = new ArrayList<>();
        File csvFile = new File(csvPath);

        if (!csvFile.exists()) {
            System.err.println("❌ File CSV non trovato: " + csvPath);
            return releases;
        }

        System.out.println("📄 Lettura del file CSV: " + csvPath + " (dimensione: " + csvFile.length() + " bytes)");

        try (CSVReader reader = new CSVReaderBuilder(new FileReader(csvPath))
                .withCSVParser(new CSVParserBuilder()
                        .withSeparator(';')
                        .build())
                .build()) {
            // resto del codice invariato
            String[] header = reader.readNext();
            if (header == null) {
                System.err.println("❌ File CSV vuoto o non valido");
                return releases;
            }

            System.out.println("  Intestazione CSV: " + String.join(";", header));

            // Determina gli indici delle colonne in base all'intestazione
            int idIndex = -1;
            int nameIndex = -1;
            int dateIndex = -1;

            for (int i = 0; i < header.length; i++) {
                String col = header[i].toLowerCase();
                if (col.contains("id")) {
                    idIndex = i;
                } else if (col.contains("version") || col.contains("name")) {
                    nameIndex = i;
                } else if (col.contains("date")) {
                    dateIndex = i;
                }
            }

            // Se non troviamo le colonne, usa dei valori predefiniti
            if (idIndex == -1) idIndex = 0;
            if (nameIndex == -1) nameIndex = 1;
            if (dateIndex == -1) dateIndex = 2;

            System.out.println("  Utilizzo colonne: ID=" + idIndex + ", Name=" + nameIndex + ", Date=" + dateIndex);

            String[] line;
            int lineNumber = 1;
            while ((line = reader.readNext()) != null) {
                lineNumber++;

                // Controlla che ci siano abbastanza colonne
                if (line.length <= Math.max(Math.max(idIndex, nameIndex), dateIndex)) {
                    System.out.println("  Riga " + lineNumber + ": formato non valido (ignorata)");
                    continue;
                }

                String idRaw = line[idIndex].trim();
                String name = line[nameIndex].trim();
                String dateIso = line[dateIndex].trim();

                // Verifica che la stringa della data non sia vuota
                if (dateIso.isEmpty()) {
                    System.out.println("  Riga " + lineNumber + ": data mancante (ignorata)");
                    continue;
                }

                try {
                    LocalDate date;
                    if (dateIso.contains("T")) {
                        // Formato ISO con 'T' (es. 2011-12-07T00:00)
                        date = LocalDate.parse(dateIso.split("T")[0]);
                    } else {
                        // Formato semplice YYYY-MM-DD
                        date = LocalDate.parse(dateIso);
                    }

                    releases.add(new ReleaseInfo(idRaw, name, date));
                    System.out.println("  Riga " + lineNumber + ": aggiunta release " + name + " (" + date + ")");
                } catch (Exception e) {
                    System.out.println("  Riga " + lineNumber + ": errore parsing data '" + dateIso + "' - " + e.getMessage());
                }
            }
        } catch (CsvValidationException e) {
            System.err.println("❌ Errore durante la validazione del CSV: " + e.getMessage());
        }

        // ordina cronologicamente
        releases.sort(Comparator.comparing(ReleaseInfo::getDate));
        return releases;
    }

    private static void gitCheckout(String projectDir, String version) throws IOException, InterruptedException {
        // Possibili formati del tag
        String[] possibleVersions = {
                version,                 // es. 4.0.0
                "v" + version,           // es. v4.0.0
                "release-" + version,    // es. release-4.0.0
                "bookkeeper-" + version  // es. bookkeeper-4.0.0
        };

        // Ottieni i tag disponibili riutilizzando il metodo esistente
        List<String> availableTags = listAvailableTags(projectDir);

        // Mostra i primi 5 tag (se ce ne sono)
        if (!availableTags.isEmpty()) {
            List<String> displayTags = availableTags.size() > 5 ? availableTags.subList(0, 5) : availableTags;
            System.out.println("  Tag disponibili (primi 5): " +
                    String.join(", ", displayTags) +
                    (availableTags.size() > 5 ? "... (" + availableTags.size() + " tag totali)" : ""));
        } else {
            System.out.println("  Nessun tag disponibile nel repository");
        }

        // Cerca un tag corrispondente
        String tagToUse = null;
        for (String possibleVersion : possibleVersions) {
            for (String tag : availableTags) {
                if (tag.equals(possibleVersion) || tag.contains(possibleVersion)) {
                    tagToUse = tag;
                    break;
                }
            }
            if (tagToUse != null) break;
        }

        // Verifica se è stato trovato un tag
        if (tagToUse == null) {
            System.out.println("⚠️ Nessun tag trovato per " + version + ", controlla manualmente i tag disponibili");
            throw new IOException("Nessun tag corrispondente a " + version + " trovato nel repository");
        } else {
            System.out.println("✓ Tag trovato: " + tagToUse);
        }

        // Esegui il checkout
        ProcessBuilder pb = new ProcessBuilder("git", "checkout", tagToUse);
        pb.directory(new File(projectDir));
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("  Git: " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Errore nel checkout del tag " + tagToUse + " (codice: " + exitCode + ")");
        } else {
            System.out.println("✅ Checkout eseguito al tag: " + tagToUse);

            // Attendi un momento per dare tempo al filesystem di aggiornarsi
            Thread.sleep(500);
        }
    }

    /**
     * Modello per le metriche di ogni metodo
     */
    private static class MetricRecord {
        private final String project;
        private final String methodQualName;
        private final String release;
        private final int loc;
        private final int cbo;
        private final int wmc;
        private final int rfc;
        private final float lcom;
        private final int dit;
        private final int noc;
        private final boolean isLongMethod;
        private final boolean isGodClass;

        public MetricRecord(String project, String methodQualName, String release, int loc, int cbo, int wmc,
                            int rfc, float lcom, int dit, int noc, boolean isLongMethod, boolean isGodClass) {
            this.project = project;
            this.methodQualName = methodQualName;
            this.release = release;
            this.loc = loc;
            this.cbo = cbo;
            this.wmc = wmc;
            this.rfc = rfc;
            this.lcom = lcom;
            this.dit = dit;
            this.noc = noc;
            this.isLongMethod = isLongMethod;
            this.isGodClass = isGodClass;
        }

        public String[] toStringArray() {
            return new String[] {
                    project,
                    methodQualName,
                    release,
                    String.valueOf(loc),
                    String.valueOf(cbo),
                    String.valueOf(wmc),
                    String.valueOf(rfc),
                    String.valueOf(lcom),
                    String.valueOf(dit),
                    String.valueOf(noc),
                    isLongMethod ? "1" : "0",
                    isGodClass ? "1" : "0"
            };
        }
    }

    private static void processRelease(ReleaseInfo release, String projectDir, String subDir, String csvPath,
                                       boolean fileExists) throws IOException {
        Path root = Path.of(projectDir).resolve(subDir).normalize();

        System.out.println("🧮 Calcolo metriche per la release " + release.getName() + " in " + root);

        // Prepara il writer CSV per scrivere con OpenCSV
        try (CSVWriter writer = new CSVWriter(new FileWriter(csvPath, fileExists),
                ',',
                CSVWriter.DEFAULT_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)) {

            // Se il file non esiste, scriviamo l'intestazione
            if (!fileExists) {
                String[] header = {
                        "project",
                        "method_qual_name",
                        "release",
                        "loc",
                        "cbo",
                        "wmc",
                        "rfc",
                        "lcom",
                        "dit",
                        "noc",
                        "is_long_method",
                        "is_god_class"
                };
                writer.writeNext(header);
                System.out.println("📝 File CSV creato con intestazione.");
            }

            // Lista temporanea di metriche da scrivere
            List<MetricRecord> metrics = new ArrayList<>();

            // CK runner per la versione corrente
            CK ck = new CK();
            final String currentRelease = release.getName();
            final int[] methodCount = {0}; // Contatore per i metodi analizzati

            ck.calculate(root.toString(), new CKNotifier() {
                @Override
                public void notify(CKClassResult cls) {
                    for (CKMethodResult m : cls.getMethods()) {
                        boolean isLongMethod = m.getLoc() > 100;
                        boolean isGodClass = cls.getWmc() > 47 && cls.getLcom() > 0.8;

                        // Crea un record per ogni metodo
                        MetricRecord record = new MetricRecord(
                                "BOOKKEEPER",
                                m.getMethodName(),
                                currentRelease,
                                m.getLoc(),
                                cls.getCbo(),
                                cls.getWmc(),
                                cls.getRfc(),
                                cls.getLcom(),
                                cls.getDit(),
                                cls.getNoc(),
                                isLongMethod,
                                isGodClass
                        );

                        metrics.add(record);
                        methodCount[0]++;
                    }
                }

                @Override
                public void notifyError(String sourceFilePath, Exception ex) {
                    System.err.println("⚠️ CK error su " + sourceFilePath + " → " + ex.getMessage());
                }
            });

            // Valida i dati prima della scrittura
            List<MetricRecord> validatedMetrics = validateMetrics(metrics, currentRelease);

            // Scrive tutte le metriche in un'unica operazione
            for (MetricRecord metric : validatedMetrics) {
                writer.writeNext(metric.toStringArray());
            }

            System.out.println("✅ Analizzati " + methodCount[0] + " metodi per la release " + release.getName());
            System.out.println("📊 Metriche per la release " + release.getName() + " aggiunte al file " + csvPath);
        } catch (Exception e) {
            System.err.println("❌ Errore durante il calcolo delle metriche: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Convalida le metriche prima della scrittura
     * @param metrics Lista di metriche da validare
     * @param expectedRelease La release attesa
     * @return Lista di metriche valide
     */
    private static List<MetricRecord> validateMetrics(List<MetricRecord> metrics, String expectedRelease) {
        List<MetricRecord> validatedMetrics = new ArrayList<>();

        for (MetricRecord metric : metrics) {
            // Verifica che la release sia valida
            if (!metric.release.equals(expectedRelease)) {
                continue;
            }

            // Verifica che il nome del metodo non sia vuoto o nullo
            if (metric.methodQualName == null || metric.methodQualName.isEmpty()) {
                continue;
            }

            // Verifica che non ci siano valori negativi nelle metriche numeriche
            if (metric.loc < 0 || metric.cbo < 0 || metric.wmc < 0 ||
                    metric.rfc < 0 || metric.dit < 0 || metric.noc < 0) {
                continue;
            }

            validatedMetrics.add(metric);
        }

        System.out.println("ℹ️ Validazione: " + validatedMetrics.size() + " metriche valide su " + metrics.size() + " totali");
        return validatedMetrics;
    }
}