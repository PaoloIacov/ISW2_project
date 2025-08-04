package metrics;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Metrica: percentuale di righe duplicate in un metodo rispetto al resto del progetto.
 * Versione migliorata: esclude chunk del metodo stesso, usa hashing per velocità.
 */
public class DuplicationPercent implements CodeMetric {

    private static final int MIN_CHUNK_SIZE = 5; // Minimo numero di righe per considerare una duplicazione
    private Map<String, Double> duplicationsCache = new HashMap<>();
    private Map<String, Integer> chunkFrequency = new HashMap<>();
    private Map<String, Set<String>> methodChunks = new HashMap<>(); // methodKey → set di chunk string

    public DuplicationPercent() {}

    @Override
    public String getName() {
        return "DuplicationPercent";
    }

    @Override
    public Double calculate(CKMethodResult method, CKClassResult cls, Path root) {
        // Popola cache e chunk solo una volta
        if (chunkFrequency.isEmpty()) {
            initializeChunkFrequencies(root);
        }

        String methodKey = cls.getClassName() + "." + method.getMethodName() + "/" + method.getParametersQty();
        if (duplicationsCache.containsKey(methodKey)) {
            return duplicationsCache.get(methodKey);
        }

        Set<String> methodChunkSet = methodChunks.getOrDefault(methodKey, Collections.emptySet());
        int totalChunks = methodChunkSet.size();
        if (totalChunks == 0) {
            duplicationsCache.put(methodKey, 0.0);
            return 0.0;
        }

        int duplicatedChunks = 0;
        for (String chunk : methodChunkSet) {
            if (chunkFrequency.getOrDefault(chunk, 0) > 1) {
                duplicatedChunks++;
            }
        }

        double duplicationPercent = ((double) duplicatedChunks / totalChunks) * 100.0;
        duplicationsCache.put(methodKey, duplicationPercent);
        return duplicationPercent;
    }

    /**
     * Estrae e indicizza tutti i chunk di codice di tutti i metodi del progetto.
     */
    private void initializeChunkFrequencies(Path root) {
        try {
            List<Path> javaFiles = Files.walk(root)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path file : javaFiles) {
                CompilationUnit cu;
                try {
                    cu = StaticJavaParser.parse(file);
                } catch (Exception e) {
                    continue; // ignora file non parsabili
                }
                cu.findAll(MethodDeclaration.class).forEach(md -> {
                    String className = md.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                            .map(c -> c.getNameAsString())
                            .orElse("UnknownClass");
                    int paramQty = md.getParameters().size();
                    String methodKey = className + "." + md.getNameAsString() + "/" + paramQty;

                    if (md.getBody().isPresent()) {
                        String[] lines = normalizeCode(md.getBody().get().toString());
                        if (lines.length >= MIN_CHUNK_SIZE) {
                            Set<String> chunkSet = methodChunks.computeIfAbsent(methodKey, k -> new HashSet<>());
                            for (int i = 0; i <= lines.length - MIN_CHUNK_SIZE; i++) {
                                String chunk = serializeChunk(lines, i, MIN_CHUNK_SIZE);
                                chunkSet.add(chunk);
                                chunkFrequency.put(chunk, chunkFrequency.getOrDefault(chunk, 0) + 1);
                            }
                        }
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Serializza un chunk di codice in una stringa unica
     */
    private String serializeChunk(String[] lines, int start, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < start + length; i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Normalizza il codice rimuovendo commenti, spazi, e righe vuote.
     */
    private String[] normalizeCode(String code) {
        // Rimuove commenti inline e multilinea
        code = code.replaceAll("//.*|/\\*[\\s\\S]*?\\*/", "");
        // Divide in righe, rimuove spazi extra, filtra righe vuote
        return code.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(line -> line.replaceAll("\\s+", " ")) // Normalizza spazi multipli
                .toArray(String[]::new);
    }
}
