package metrics;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import utils.SourceCodeExtractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementazione della metrica DuplicationPercent che calcola la percentuale
 * di codice duplicato in un metodo rispetto al resto del progetto.
 */
public class DuplicationPercent implements CodeMetric {

    private static final int MIN_CHUNK_SIZE = 5; // Minimo numero di righe per considerare una duplicazione
    private Map<String, Double> duplicationsCache;
    private List<String[]> allCodeChunks;

    public DuplicationPercent() {
        this.duplicationsCache = new HashMap<>();
        this.allCodeChunks = new ArrayList<>();
    }

    @Override
    public String getName() {
        return "DuplicationPercent";
    }

    @Override
    public Double calculate(CKMethodResult method, CKClassResult cls, Path root) {
        // Se la cache è vuota, inizializza l'analisi
        if (allCodeChunks.isEmpty()) {
            initializeCodeChunks(root);
        }

        // Costruisce la chiave di identificazione del metodo
        String methodKey = cls.getClassName() + "." + method.getMethodName();

        // Se abbiamo già calcolato la duplicazione per questo metodo, restituiscila
        if (duplicationsCache.containsKey(methodKey)) {
            return duplicationsCache.get(methodKey);
        }

        // Ottieni il codice sorgente del metodo
        String methodSource = SourceCodeExtractor.getMethodSource(method, root);
        if (methodSource == null || methodSource.isEmpty()) {
            return 0.0; // Se non riusciamo a ottenere il codice, restituisci 0%
        }

        // Dividi il metodo in righe e rimuovi gli spazi, le righe vuote e i commenti
        String[] methodLines = normalizeCode(methodSource);

        // Se il metodo ha meno righe del minimo per considerare una duplicazione, restituisci 0%
        if (methodLines.length < MIN_CHUNK_SIZE) {
            duplicationsCache.put(methodKey, 0.0);
            return 0.0;
        }

        // Calcola la percentuale di codice duplicato
        double duplicationPercent = calculateDuplication(methodLines);

        // Memorizza il risultato nella cache
        duplicationsCache.put(methodKey, duplicationPercent);

        return duplicationPercent;
    }

    /**
     * Inizializza i chunk di codice da tutto il progetto per il confronto
     */
    private void initializeCodeChunks(Path root) {
        try {
            // Trova tutti i file Java nel progetto
            List<Path> javaFiles = Files.walk(root)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());

            // Per ogni file, estrai i metodi e i loro chunk di codice
            for (Path file : javaFiles) {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(file);

                    // Trova tutti i metodi e estrai i loro chunk di codice
                    cu.findAll(MethodDeclaration.class).forEach(md -> {
                        if (md.getBody().isPresent()) {
                            String[] lines = normalizeCode(md.getBody().get().toString());

                            // Aggiungi chunk di MIN_CHUNK_SIZE righe consecutive
                            if (lines.length >= MIN_CHUNK_SIZE) {
                                for (int i = 0; i <= lines.length - MIN_CHUNK_SIZE; i++) {
                                    String[] chunk = new String[MIN_CHUNK_SIZE];
                                    System.arraycopy(lines, i, chunk, 0, MIN_CHUNK_SIZE);
                                    allCodeChunks.add(chunk);
                                }
                            }
                        }
                    });
                } catch (Exception e) {
                    // Ignora i file che non possono essere analizzati
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Normalizza il codice rimuovendo spazi, commenti e righe vuote
     */
    private String[] normalizeCode(String code) {
        // Rimuovi commenti
        code = code.replaceAll("//.*|/\\*[\\s\\S]*?\\*/", "");

        // Dividi in righe e normalizza
        return code.lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .map(line -> line.replaceAll("\\s+", " ")) // Normalizza spazi multipli
            .toArray(String[]::new);
    }

    /**
     * Calcola la percentuale di duplicazione per le righe del metodo
     */
    private double calculateDuplication(String[] methodLines) {
        if (methodLines.length == 0) {
            return 0.0;
        }

        int totalLines = methodLines.length;
        int duplicatedLines = 0;

        // Per ogni possibile chunk nel metodo, verifica se è presente altrove
        for (int i = 0; i <= methodLines.length - MIN_CHUNK_SIZE; i++) {
            String[] chunk = new String[MIN_CHUNK_SIZE];
            System.arraycopy(methodLines, i, chunk, 0, MIN_CHUNK_SIZE);

            // Verifica se questo chunk è duplicato altrove
            if (isDuplicated(chunk)) {
                duplicatedLines++;
            }
        }

        // Calcola la percentuale, tenendo conto che ogni riga può essere contata più volte
        return Math.min(100.0, (double) duplicatedLines / (totalLines - MIN_CHUNK_SIZE + 1) * 100.0);
    }

    /**
     * Verifica se un chunk di codice è duplicato in altri metodi
     */
    private boolean isDuplicated(String[] chunk) {
        // Controlla se questo chunk appare più di una volta nella collezione di tutti i chunk
        int occurrences = 0;

        for (String[] otherChunk : allCodeChunks) {
            if (areChunksEqual(chunk, otherChunk)) {
                occurrences++;
                if (occurrences > 1) {
                    return true; // Trovata almeno una duplicazione
                }
            }
        }

        return false;
    }

    /**
     * Confronta due chunk di codice per determinare se sono uguali
     */
    private boolean areChunksEqual(String[] chunk1, String[] chunk2) {
        if (chunk1.length != chunk2.length) {
            return false;
        }

        for (int i = 0; i < chunk1.length; i++) {
            if (!chunk1[i].equals(chunk2[i])) {
                return false;
            }
        }

        return true;
    }
}