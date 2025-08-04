package metrics;

import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.StaticJavaParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class Churn implements CodeMetric {

    private final Path historyDir;
    private final Map<String, Integer> churnCache = new HashMap<>();

    public Churn(Path historyDir) {
        this.historyDir = historyDir;
    }

    @Override
    public String getName() {
        return "Churn";
    }

    @Override
    public Integer calculate(CKMethodResult method, CKClassResult cls, Path root) {
        if (churnCache.isEmpty()) {
            calculateChurnForAllMethods(root);
        }
        String methodKey = cls.getClassName() + "#" + method.getMethodName();
        return churnCache.getOrDefault(methodKey, 0);
    }

    private void calculateChurnForAllMethods(Path currentRoot) {
        try {
            List<Path> versionDirs = Files.list(historyDir)
                    .filter(Files::isDirectory)
                    .sorted()
                    .collect(Collectors.toList());
            versionDirs.add(currentRoot);

            Map<String, List<String>> prevMethods = new HashMap<>();
            for (Path versionDir : versionDirs) {
                List<Path> javaFiles = Files.walk(versionDir)
                        .filter(p -> p.toString().endsWith(".java"))
                        .collect(Collectors.toList());

                Map<String, List<String>> currMethods = new HashMap<>();
                for (Path file : javaFiles) {
                    currMethods.putAll(extractMethodsFromFile(file));
                }

                if (!prevMethods.isEmpty()) {
                    for (Map.Entry<String, List<String>> entry : currMethods.entrySet()) {
                        String methodKey = entry.getKey();
                        List<String> currBody = entry.getValue();
                        List<String> prevBody = prevMethods.getOrDefault(methodKey, Collections.emptyList());
                        int churn = computeChurn(prevBody, currBody);
                        if (churn != 0) {
                            churnCache.put(methodKey, churnCache.getOrDefault(methodKey, 0) + churn);
                        }
                    }
                }
                prevMethods = currMethods;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Estrai tutti i metodi dal file e restituisci una mappa <chiaveUnica, corpoMetodo in righe>
     */
    private Map<String, List<String>> extractMethodsFromFile(Path file) {
        Map<String, List<String>> methods = new HashMap<>();
        try {
            StaticJavaParser.parse(file)
                    .findAll(MethodDeclaration.class)
                    .forEach(md -> {
                        String className = md.findCompilationUnit()
                                .flatMap(cu -> cu.getPackageDeclaration().map(pkg -> pkg.getNameAsString() + ".")).orElse("")
                                + md.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                                .map(cls -> cls.getNameAsString()).orElse("UnknownClass");
                        String methodKey = className + "#" + md.getNameAsString();
                        String methodBody = md.getBody().map(Object::toString).orElse("");
                        // Rimuovi parentesi graffe e split per riga
                        methodBody = methodBody.replaceAll("^\\{\\s*|\\s*}$", "");
                        List<String> lines = Arrays.stream(methodBody.split("\\R"))
                                .map(String::trim)
                                .filter(line -> !line.isEmpty())
                                .collect(Collectors.toList());
                        methods.put(methodKey, lines);
                    });
        } catch (Exception ignore) {}
        return methods;
    }

    /**
     * Calcola la differenza (churn) tra due liste di righe
     */
    private int computeChurn(List<String> oldLines, List<String> newLines) {
        Set<String> oldSet = new HashSet<>(oldLines);
        Set<String> newSet = new HashSet<>(newLines);

        int added = 0, deleted = 0;
        for (String line : newSet) {
            if (!oldSet.contains(line)) added++;
        }
        for (String line : oldSet) {
            if (!newSet.contains(line)) deleted++;
        }
        return added - deleted;
    }
}
