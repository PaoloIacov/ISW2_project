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
import lombok.Setter;
import model.ReleaseInfo;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runner per l'estrazione delle metriche del codice dal progetto Bookkeeper.
 * Utilizza direttamente il repository Git per l'analisi storica.
 */
public class BookkeeperMetricsRunner {

    // Logger configurato per usare direttamente System.err per aggirare eventuali configurazioni
    private static final PrintStream LOG = System.err;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Definizione di una classe interna per memorizzare la storia dei metodi
    private static class MethodHistoryData {
        @Setter
        private LocalDate firstSeen;
        private final Set<String> authors;
        private int churnCount;
        private int locAdded;

        public MethodHistoryData() {
            this.authors = new HashSet<>();
            this.churnCount = 0;
            this.locAdded = 0;
        }

        public LocalDate getFirstSeen() {
            return firstSeen;
        }

        public Set<String> getAuthors() {
            return authors;
        }

        public void addAuthor(String author) {
            this.authors.add(author);
        }

        public int getChurnCount() {
            return churnCount;
        }

        public void incrementChurn() {
            this.churnCount++;
        }

        public int getLocAdded() {
            return locAdded;
        }

        public void addLOC(int loc) {
            this.locAdded += loc;
        }
    }

    private static final Map<String, MethodHistoryData> METHOD_HISTORIES = new ConcurrentHashMap<>();

    // Percorsi
    private static Path repoPath;
    private static Path outputDir;
    private static String outputCsvPath;
    private static Properties properties;
    private static Git git;

    // Metriche
    private static final List<CodeMetric> METRICS = new ArrayList<>();

    public static void main(String[] args) {
        // Test output diretto
        LOG.println("🔍 TEST LOG INIZIALE - Verifica output errori");
        System.out.println("🔍 TEST LOG INIZIALE - Verifica output standard");

        try {
            LOG.println("🚀 AVVIO APPLICAZIONE: Inizio estrazione metriche per Bookkeeper");

            // Carica le proprietà dal file info.properties
            loadProperties();
            LOG.println("✅ Properties caricate con successo");

            // Inizializza i percorsi dai valori delle proprietà
            repoPath = Paths.get(properties.getProperty("info.repo.path"));
            outputDir = Paths.get(properties.getProperty("info.output.path"));
            outputCsvPath = outputDir.resolve("bookkeeper_metrics.csv").toString();
            LOG.println("📁 Percorsi configurati:");
            LOG.println("   - Repository: " + repoPath);
            LOG.println("   - Output: " + outputCsvPath);

            // Verifica se esistono file di log nella directory corrente
            LOG.println("🔍 Ricerca file di log nella directory corrente:");
            try {
                Files.list(Paths.get(""))
                    .filter(p -> p.toString().contains(".log"))
                    .forEach(p -> LOG.println("   - File di log trovato: " + p));
            } catch (Exception e) {
                LOG.println("❌ Errore nella ricerca dei file di log: " + e.getMessage());
            }

            // Inizializza il repository Git
            LOG.println("🔄 Inizializzazione repository Git...");
            initGitRepository();
            LOG.println("✅ Repository Git inizializzato");

            // Inizializza le metriche
            LOG.println("🔄 Inizializzazione metriche...");
            initializeMetrics();
            LOG.println("✅ Metriche inizializzate: " + METRICS.size() + " metriche configurate");

            // Estrai informazioni sulle release
            LOG.println("🔄 Estrazione informazioni sulle release...");
            List<ReleaseInfo> releases = extractReleaseInfo();
            LOG.println("✅ Releases estratte: " + releases.size() + " versioni trovate");
            for (ReleaseInfo release : releases) {
                LOG.println("   - " + release.getName() + " (" + release.getDate() + ")");
            }

            // Crea la directory di output se non esiste
            if (!Files.exists(outputDir)) {
                LOG.println("🔄 Creazione directory di output: " + outputDir);
                Files.createDirectories(outputDir);
                LOG.println("✅ Directory creata");
            }

            // Verifica se il file CSV esiste già
            boolean fileExists = Files.exists(Paths.get(outputCsvPath));
            LOG.println(fileExists ? "ℹ️ File CSV già esistente" : "ℹ️ File CSV non esistente, verrà creato");

            // Crea l'intestazione se il file non esiste
            if (!fileExists) {
                LOG.println("🔄 Creazione intestazione CSV...");
                createCsvHeader();
                LOG.println("✅ Intestazione CSV creata");
            }

            // Per ogni release, esegui l'analisi
            int releaseCounter = 0;
            for (ReleaseInfo release : releases) {
                releaseCounter++;
                LOG.println("\n==================================================");
                LOG.println("🔄 ANALISI RELEASE " + releaseCounter + "/" + releases.size() + ": " + release.getName());
                LOG.println("==================================================");

                try {
                    // Checkout della versione specifica
                    LOG.println("🔄 Checkout della versione: " + release.getName());
                    gitCheckout(release.getName());

                    // Verifica dei file dopo il checkout
                    LOG.println("🔍 Verifica dei file nel repository dopo checkout:");
                    verifyRepositoryFiles();

                    // Analizza la release e aggiungi i risultati al CSV
                    LOG.println("🔄 Analisi della release...");
                    analyzeRelease(release);
                    LOG.println("✅ Analisi della release completata: " + release.getName());
                } catch (Exception e) {
                    LOG.println("❌ ERRORE durante l'analisi della release " + release.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
                LOG.println("--------------------------------------------------");
            }

            // Ritorna al branch principale
            LOG.println("🔄 Ritorno al branch principale...");
            try {
                git.checkout().setName("main").call();
                LOG.println("✅ Checkout a 'main' completato");
            } catch (Exception e) {
                LOG.println("ℹ️ Branch 'main' non trovato, tentativo con 'master'...");
                git.checkout().setName("master").call();
                LOG.println("✅ Checkout a 'master' completato");
            }

            LOG.println("\n🎉 ANALISI COMPLETATA 🎉");
            System.out.println("Analisi completata. I risultati sono stati salvati in: " + outputCsvPath);

        } catch (IOException | GitAPIException e) {
            LOG.println("❌ ERRORE FATALE: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Chiudi il repository Git
            if (git != null) {
                LOG.println("🔄 Chiusura repository Git...");
                git.close();
                LOG.println("✅ Repository Git chiuso");
            }
        }
    }

    /**
     * Verifica i file nel repository dopo un checkout
     */
    private static void verifyRepositoryFiles() {
        LOG.println("🔍 Contenuto della directory root:");
        try {
            Files.list(repoPath)
                .filter(Files::isDirectory)
                .limit(10)
                .forEach(p -> LOG.println("   - [DIR] " + p.getFileName()));
        } catch (IOException e) {
            LOG.println("❌ Errore nella lettura delle directory: " + e.getMessage());
        }

        LOG.println("🔍 Ricerca file Java (max 10):");
        try {
            long javaCount = Files.walk(repoPath, 5)
                .filter(p -> p.toString().endsWith(".java"))
                .limit(10)
                .peek(p -> LOG.println("   - " + p))
                .count();

            LOG.println("   Trovati " + javaCount + "+ file Java (limitato a 10)");
        } catch (IOException e) {
            LOG.println("❌ Errore nella ricerca dei file Java: " + e.getMessage());
        }
    }

    /**
     * Carica le proprietà dal file info.properties
     */
    private static void loadProperties() throws IOException {
        properties = new Properties();
        Path propertiesPath = Paths.get("src/main/resources/info.properties");
        LOG.println("🔍 Caricamento properties da: " + propertiesPath.toAbsolutePath());
        if (!Files.exists(propertiesPath)) {
            LOG.println("⚠️ File properties non trovato in: " + propertiesPath.toAbsolutePath());
            // Cerca il file in altre location possibili
            propertiesPath = Paths.get("info.properties");
            LOG.println("🔍 Tentativo di caricamento da: " + propertiesPath.toAbsolutePath());
        }

        try (InputStream input = new FileInputStream(propertiesPath.toFile())) {
            properties.load(input);
            LOG.println("✅ Properties caricate: " + properties.size() + " proprietà");
            properties.forEach((k, v) -> LOG.println("   - " + k + " = " + v));
        }
    }

    /**
     * Inizializza il repository Git
     */
    private static void initGitRepository() throws IOException {
        LOG.println("🔍 Ricerca directory Git in: " + repoPath.toFile());
        File gitDir = new File(repoPath.toFile(), ".git");
        if (!gitDir.exists()) {
            LOG.println("⚠️ Directory .git non trovata in " + repoPath.toFile());
            LOG.println("🔍 Contenuto della directory:");
            File[] files = repoPath.toFile().listFiles();
            if (files != null) {
                for (File file : files) {
                    LOG.println("   - " + file.getName() + (file.isDirectory() ? "/" : ""));
                }
            }
        }

        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repository = builder
                .setGitDir(gitDir)
                .build();
        git = new Git(repository);
        LOG.println("✅ Repository Git inizializzato: " + repository.getDirectory());
    }

    /**
     * Inizializza le metriche del codice
     */
    private static void initializeMetrics() {
        LOG.println("🔄 Configurazione delle metriche...");

        // Metriche base
        METRICS.add(new Complexity());
        LOG.println("✅ Aggiunta metrica: Complexity");

        METRICS.add(new HalsteadVolume());
        LOG.println("✅ Aggiunta metrica: HalsteadVolume");

        METRICS.add(new FanIn());
        LOG.println("✅ Aggiunta metrica: FanIn");

        METRICS.add(new FanOut());
        LOG.println("✅ Aggiunta metrica: FanOut");

        METRICS.add(new StatementCount());
        LOG.println("✅ Aggiunta metrica: StatementCount");

        METRICS.add(new nSmells());
        LOG.println("✅ Aggiunta metrica: nSmells");

        METRICS.add(new DuplicationPercent());
        LOG.println("✅ Aggiunta metrica: DuplicationPercent");

        // Metriche che utilizzano Git direttamente
        METRICS.add(new Age(repoPath));
        LOG.println("✅ Aggiunta metrica: Age");

        METRICS.add(new Churn(repoPath));
        LOG.println("✅ Aggiunta metrica: Churn");

        METRICS.add(new LOCAdded(repoPath));
        LOG.println("✅ Aggiunta metrica: LOCAdded");

        METRICS.add(new NAuthors(repoPath));
        LOG.println("✅ Aggiunta metrica: NAuthors");

        METRICS.add(new MethodHistory(repoPath));
        LOG.println("✅ Aggiunta metrica: MethodHistory");
    }

    /**
     * Estrae le informazioni sulle release dal file releases.csv o utilizza la versione corrente
     */
    private static List<ReleaseInfo> extractReleaseInfo() throws IOException {
        List<ReleaseInfo> releases = new ArrayList<>();
        Path releasesFile = Paths.get("/Users/iacov/Library/Mobile Documents/com~apple~CloudDocs/BOOKKEEPERVersionInfo.csv");
        LOG.println("🔍 Cerco file delle release in: " + releasesFile);

        if (!Files.exists(releasesFile)) {
            LOG.println("⚠️ File delle release non trovato in: " + releasesFile);
            LOG.println("ℹ️ Utilizzo data corrente e HEAD come versione");

            // Crea una release con la data corrente, usando "HEAD" come ID Git-friendly
            ReleaseInfo currentRelease = new ReleaseInfo("HEAD", "Current Version", LocalDate.now());
            releases.add(currentRelease);
            return releases;
        }

        LOG.println("✅ File delle release trovato, estrazione dati...");

        // Apri il file delle release
        try (CSVReader reader = new CSVReaderBuilder(new FileReader(releasesFile.toFile()))
                .withCSVParser(new CSVParserBuilder().withSeparator(';').build())
                .build()) {

            String[] line;
            // Salta l'intestazione
            reader.readNext();
            LOG.println("ℹ️ Intestazione saltata");

            int count = 0;
            while ((line = reader.readNext()) != null) {
                if (line.length >= 3) {
                    String id = line[0];
                    String name = line[1];
                    LocalDate date = LocalDate.parse(line[2], DATE_FORMATTER);

                    ReleaseInfo release = new ReleaseInfo(id, name, date);
                    releases.add(release);
                    count++;

                    if (count <= 5) {
                        LOG.println("   - Release trovata: " + id + " (" + name + ") del " + date);
                    } else if (count == 6) {
                        LOG.println("   - ... altre release ...");
                    }
                }
            }

            LOG.println("✅ Estratte " + count + " release");
        } catch (CsvValidationException e) {
            LOG.println("❌ Errore di validazione CSV: " + e.getMessage());
            e.printStackTrace();
        }

        // Ordina le release per data
        releases.sort(Comparator.comparing(ReleaseInfo::getDate));
        LOG.println("ℹ️ Release ordinate per data");
        return releases;
    }

    /**
     * Crea l'intestazione del file CSV di output
     */
    private static void createCsvHeader() throws IOException {
        LOG.println("🔄 Creazione file CSV: " + outputCsvPath);
        try (CSVWriter writer = new CSVWriter(new FileWriter(outputCsvPath))) {
            List<String> header = new ArrayList<>();

            // Colonne di base
            header.add("project");
            header.add("package");
            header.add("class");
            header.add("method");
            header.add("release");
            header.add("release_date");
            header.add("loc");
            header.add("cc");
            LOG.println("ℹ️ Aggiunte colonne di base all'intestazione");

            // Aggiungi i nomi delle metriche personalizzate
            for (CodeMetric metric : METRICS) {
                header.add(metric.getName());
                LOG.println("ℹ️ Aggiunta colonna: " + metric.getName());
            }

            LOG.println("🔄 Scrittura intestazione con " + header.size() + " colonne");
            writer.writeNext(header.toArray(new String[0]));
            LOG.println("✅ Intestazione CSV creata: " + outputCsvPath);
        } catch (Exception e) {
            LOG.println("❌ Errore nella creazione dell'intestazione CSV: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Esegue il checkout di una specifica versione nel repository
     */
    private static void gitCheckout(String version) throws GitAPIException {
        LOG.println("🔄 Esecuzione checkout alla versione: " + version);

        try {
            git.checkout().setName(version).call();
            LOG.println("✅ Checkout completato con successo per: " + version);

            // Attendi un momento per dare tempo al filesystem di aggiornarsi
            LOG.println("ℹ️ Attesa per aggiornamento filesystem...");
            Thread.sleep(1000);

            // Verifica che ci siano file Java dopo il checkout
            LOG.println("🔍 Conteggio file Java nel repository...");
            try {
                long javaFiles = Files.walk(repoPath)
                    .filter(p -> p.toString().endsWith(".java"))
                    .count();

                LOG.println("ℹ️ Numero totale di file Java trovati: " + javaFiles);

                if (javaFiles == 0) {
                    LOG.println("⚠️ ATTENZIONE: Nessun file Java trovato dopo il checkout!");
                    LOG.println("🔍 Struttura delle directory principali:");
                    Files.walk(repoPath, 2)
                        .filter(Files::isDirectory)
                        .forEach(p -> LOG.println("   - " + p));
                }
            } catch (IOException e) {
                LOG.println("❌ Errore nel conteggio dei file Java: " + e.getMessage());
            }
        } catch (Exception e) {
            LOG.println("⚠️ Errore durante il checkout a " + version + ": " + e.getMessage());
            e.printStackTrace();

            // Se è un InvalidRefNameException, proviamo con HEAD
            if (e.getMessage() != null && e.getMessage().contains("not allowed")) {
                LOG.println("🔄 Tentativo di checkout con HEAD...");
                git.checkout().setName("HEAD").call();
                LOG.println("✅ Checkout a HEAD completato");
            }
        }
    }

    /**
     * Analizza una release e scrive i risultati nel file CSV
     */
    private static void analyzeRelease(ReleaseInfo release) throws IOException {
        LOG.println("\n🔍 INIZIO ANALISI RELEASE: " + release.getName() + " (" + release.getDate() + ")");
        LOG.println("📁 Path repository: " + repoPath.toAbsolutePath());

        // Cerca la directory del codice sorgente Java
        Path srcPath = findSourceDirectory(repoPath);
        LOG.println("📁 Directory sorgente identificata: " + srcPath);

        // Usa append=true per aggiungere al CSV esistente
        LOG.println("🔄 Apertura file CSV per aggiunta dati: " + outputCsvPath);
        try (CSVWriter writer = new CSVWriter(new FileWriter(outputCsvPath, true))) {
            // Contatore per le classi e i metodi analizzati
            final int[] classCounter = {0};
            final int[] methodCounter = {0};

            // Esegui l'analisi CK sulla directory sorgente
            LOG.println("🔄 Avvio analisi CK su: " + srcPath);
            LOG.println("⏳ Attendere mentre la libreria CK analizza i file...");

            new CK().calculate(srcPath.toString(), new CKNotifier() {
                @Override
                public void notify(CKClassResult cls) {
                    classCounter[0]++;

                    if (classCounter[0] % 10 == 0 || classCounter[0] <= 5) {
                        LOG.println("✅ #" + classCounter[0] + " Classe analizzata: " + cls.getClassName() +
                                  " con " + cls.getMethods().size() + " metodi");
                    }

                    for (CKMethodResult method : cls.getMethods()) {
                        methodCounter[0]++;
                        processMethod(writer, method, cls, release);

                        // Ogni 100 metodi, fai un flush del writer per evitare perdite
                        if (methodCounter[0] % 100 == 0) {
                            try {
                                writer.flush();
                                LOG.println("💾 Salvataggio intermedio dopo " + methodCounter[0] + " metodi");
                            } catch (IOException e) {
                                LOG.println("⚠️ Errore durante il salvataggio intermedio: " + e.getMessage());
                            }
                        }
                    }
                }

                @Override
                public void notifyError(String sourceFilePath, Exception e) {
                    LOG.println("⚠️ Errore nell'analisi di " + sourceFilePath + ": " + e.getMessage());
                }
            });

            LOG.println("✅ ANALISI COMPLETATA: " + classCounter[0] + " classi e " +
                    methodCounter[0] + " metodi elaborati per la release: " + release.getName());

            if (classCounter[0] == 0) {
                LOG.println("⚠️ ATTENZIONE: Nessuna classe trovata! Verifica il percorso e la versione Git.");
                LOG.println("🔍 Path utilizzato per l'analisi: " + srcPath.toAbsolutePath());
            }
        } catch (Exception e) {
            LOG.println("❌ Errore critico durante l'analisi della release: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Errore nell'analisi della release " + release.getName(), e);
        }
    }

    /**
     * Trova la directory del codice sorgente Java all'interno del repository
     */
    private static List<Path> findAllSourceDirectories(Path repoPath) {
        LOG.println("🔍 Ricerca approfondita di tutte le directory con file Java...");
        List<Path> sourceDirs = new ArrayList<>();

        try {
            // Cerca in tutti i sotto-moduli le directory src/main/java tipiche di Maven
            Files.walk(repoPath, 4)
                    .filter(path -> path.toString().endsWith("/src/main/java") && Files.isDirectory(path))
                    .forEach(dir -> {
                        try {
                            // Verifica che contenga file Java
                            boolean hasJavaFiles = Files.walk(dir, 1)
                                    .anyMatch(p -> p.toString().endsWith(".java"));

                            if (hasJavaFiles) {
                                LOG.println("✅ Trovata directory con file Java: " + dir);
                                sourceDirs.add(dir);
                            }
                        } catch (IOException e) {
                            LOG.println("⚠️ Errore nella ricerca di file Java: " + e.getMessage());
                        }
                    });

            if (sourceDirs.isEmpty()) {
                LOG.println("⚠️ Nessuna directory src/main/java trovata, cerco file .java in generale");
                // Ricerca più generica
                Files.walk(repoPath, 10)
                        .filter(path -> path.toString().endsWith(".java"))
                        .map(Path::getParent)
                        .distinct()
                        .forEach(dir -> {
                            LOG.println("✅ Trovata directory con file Java: " + dir);
                            sourceDirs.add(dir);
                        });
            }
        } catch (IOException e) {
            LOG.println("❌ Errore nella ricerca di directory Java: " + e.getMessage());
        }

        if (sourceDirs.isEmpty()) {
            LOG.println("⚠️ Nessuna directory con file Java trovata, utilizzo il percorso base");
            sourceDirs.add(repoPath);
        } else {
            LOG.println("✅ Trovate " + sourceDirs.size() + " directory con file Java");
        }

        return sourceDirs;
    }
    /**
     * Elabora un singolo metodo e scrive i suoi dati nel file CSV
     */
    private static void processMethod(CSVWriter writer, CKMethodResult method, CKClassResult cls, ReleaseInfo release) {
        try {
            // Costruisci la chiave univoca per il metodo
            String methodKey = cls.getClassName() + "." + method.getMethodName();

            // Non loggare ogni metodo per evitare output eccessivo
            // Solo per metodo specifici (ad esempio uno ogni 50) o primi 5
            boolean shouldLog = methodKey.hashCode() % 50 == 0 ||
                              METHOD_HISTORIES.size() < 5;

            if (shouldLog) {
                LOG.println("🔄 Elaborazione metodo: " + methodKey);
            }

            // Inizializza la history del metodo se non esiste
            METHOD_HISTORIES.putIfAbsent(methodKey, new MethodHistoryData());

            // Se questa è la prima volta che vediamo questo metodo, registra la data
            MethodHistoryData history = METHOD_HISTORIES.get(methodKey);
            if (history.getFirstSeen() == null) {
                history.setFirstSeen(release.getDate());
            }

            // Estrai package e nome classe
            String packageName = "";
            String className = cls.getClassName();
            int lastDot = className.lastIndexOf(".");
            if (lastDot > 0) {
                packageName = className.substring(0, lastDot);
                className = className.substring(lastDot + 1);
            }

            // Prepara i valori per il CSV
            List<String> values = new ArrayList<>();

            // Informazioni di base
            String projectName = properties.getProperty("info.name", "bookkeeper");
            values.add(projectName);                           // project
            values.add(packageName);                           // package
            values.add(className);                             // class
            values.add(method.getMethodName());                // method
            values.add(release.getName());                     // release
            values.add(release.getDate().format(DATE_FORMATTER)); // release_date

            // Metriche CK standard
            int loc = method.getLoc();
            int cc = method.getWmc();
            values.add(String.valueOf(loc));                   // loc
            values.add(String.valueOf(cc));                    // cc

            if (shouldLog) {
                LOG.println("📊 Metriche di base: LOC=" + loc + ", CC=" + cc);
            }

            // Calcola e aggiungi le metriche personalizzate
            for (CodeMetric metric : METRICS) {
                try {
                    String metricName = metric.getName();

                    if (shouldLog) {
                        LOG.println("🧮 Calcolo " + metricName + "...");
                    }

                    Object result = metric.calculate(method, cls, repoPath);
                    String resultStr = (result != null) ? result.toString() : "0";

                    if (shouldLog) {
                        LOG.println("✅ " + metricName + " = " + resultStr);
                    }

                    values.add(resultStr);
                } catch (Exception e) {
                    if (shouldLog) {
                        LOG.println("⚠️ Errore nel calcolo della metrica " + metric.getName() + ": " + e.getMessage());
                    }
                    values.add("0"); // Valore di default in caso di errore
                }
            }

            // Scrivi la riga nel CSV
            writer.writeNext(values.toArray(new String[0]));

            if (shouldLog) {
                LOG.println("✅ Riga CSV scritta per: " + methodKey);
            }
        } catch (Exception e) {
            LOG.println("⚠️ ERRORE nell'elaborazione del metodo " +
                      cls.getClassName() + "." + method.getMethodName() + ": " + e.getMessage());
        }
    }
}