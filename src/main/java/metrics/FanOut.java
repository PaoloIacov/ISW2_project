package metrics;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import utils.SourceCodeExtractor;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FanOut implements CodeMetric {
    @Override
    public String getName() {
        return "FanOut";
    }

    @Override
    public Integer calculate(CKMethodResult method, CKClassResult cls, Path root) {
        // Ottieni il codice sorgente del metodo
        String sourceCode = SourceCodeExtractor.getMethodSource(method, root);
        if (sourceCode == null || sourceCode.isEmpty()) {
            return 0;
        }

        // Nome del metodo che stiamo analizzando
        String methodName = method.getMethodName();

        try {
            // Parsifica il codice sorgente del metodo
            CompilationUnit cu = StaticJavaParser.parse("class Temp { " + sourceCode + " }");

            // Trova tutte le chiamate a metodi all'interno del metodo analizzato
            List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class);

            // Usa un Set per evitare di contare più volte lo stesso metodo chiamato
            Set<String> uniqueMethodCalls = new HashSet<>();

            // Filtra le chiamate ricorsive (al metodo stesso)
            for (MethodCallExpr call : methodCalls) {
                String calledMethodName = call.getNameAsString();
                // Ignora le chiamate al metodo stesso (ricorsione)
                if (!calledMethodName.equals(methodName)) {
                    uniqueMethodCalls.add(calledMethodName);
                }
            }

            // Restituisci il numero di metodi unici chiamati
            return uniqueMethodCalls.size();

        } catch (Exception e) {
            // In caso di errore durante il parsing, tenta un approccio alternativo
            // contando manualmente le chiamate a metodo
            return countMethodCallsManually(sourceCode, methodName);
        }
    }

    private Integer countMethodCallsManually(String sourceCode, String methodName) {
        int fanOut = 0;
        Set<String> uniqueMethodCalls = new HashSet<>();

        // Semplice regex per trovare chiamate a metodi (non perfetta ma funzionale)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b([a-zA-Z][a-zA-Z0-9_]*)\\s*\\(");
        java.util.regex.Matcher matcher = pattern.matcher(sourceCode);

        while (matcher.find()) {
            String calledMethod = matcher.group(1);
            // Ignora le chiamate al metodo stesso (ricorsione)
            if (!calledMethod.equals(methodName)) {
                uniqueMethodCalls.add(calledMethod);
            }
        }

        return uniqueMethodCalls.size();
    }
}