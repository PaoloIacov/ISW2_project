package metrics;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.*;
import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import utils.SourceCodeExtractor;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Complexity implements CodeMetric {
    @Override
    public String getName() {
        return "Complexity";
    }

    @Override
    public Integer calculate(CKMethodResult method, CKClassResult cls, Path root) {
        // Ottieni il codice sorgente del metodo
        String sourceCode = SourceCodeExtractor.getMethodSource(method, root);
        if (sourceCode == null || sourceCode.isEmpty()) {
            return 1; // Complessità minima di default
        }

        try {
            // Prova ad utilizzare JavaParser per un'analisi più precisa
            CompilationUnit cu = StaticJavaParser.parse("class Temp { " + sourceCode + " }");

            // Inizia con 1 (complessità base)
            int complexity = 1;

            // Conta if statement
            complexity += cu.findAll(IfStmt.class).size();

            // Conta switch cases
            for (SwitchStmt switchStmt : cu.findAll(SwitchStmt.class)) {
                complexity += switchStmt.getEntries().size();
            }

            // Conta for, while e do-while loops
            complexity += cu.findAll(ForStmt.class).size();
            complexity += cu.findAll(ForEachStmt.class).size();
            complexity += cu.findAll(WhileStmt.class).size();
            complexity += cu.findAll(DoStmt.class).size();

            // Conta catch blocks
            complexity += cu.findAll(CatchClause.class).size();

            // Conta gli operatori condizionali && e ||
            complexity += countLogicalOperators(sourceCode);

            return complexity;

        } catch (Exception e) {
            // Fallback al metodo basato su regex
            return calculateUsingRegex(sourceCode);
        }
    }

    private int countLogicalOperators(String code) {
        int count = 0;

        // Conta gli operatori logici && e ||
        Pattern pattern = Pattern.compile("(\\&\\&|\\|\\|)");
        Matcher matcher = pattern.matcher(code);

        while (matcher.find()) {
            count++;
        }

        // Conta gli operatori ternari
        Pattern ternaryPattern = Pattern.compile("\\?");
        Matcher ternaryMatcher = ternaryPattern.matcher(code);

        while (ternaryMatcher.find()) {
            count++;
        }

        return count;
    }

    private int calculateUsingRegex(String sourceCode) {
        int complexity = 1; // Complessità base

        // Espressioni regolari per individuare strutture di controllo ed operatori logici
        String[] patterns = {
            "\\bif\\s*\\(",                  // if statements
            "\\belse\\s+if\\s*\\(",          // else if statements
            "\\bfor\\s*\\(",                 // for loops
            "\\bwhile\\s*\\(",               // while loops
            "\\bdo\\s*\\{",                  // do-while loops
            "\\bcase\\s+[^:]+:",             // case in switch
            "\\bcatch\\s*\\(",               // catch blocks
            "\\&\\&",                        // Operatore AND
            "\\|\\|",                        // Operatore OR
            "\\?"                            // Operatore ternario
        };

        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(sourceCode);

            while (matcher.find()) {
                complexity++;
            }
        }

        return complexity;
    }
}

