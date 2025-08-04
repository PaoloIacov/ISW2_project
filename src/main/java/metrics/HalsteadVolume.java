package metrics;

import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Classe che implementa il calcolo della metrica HalsteadVolume.
 * Il volume di Halstead è calcolato come V = N * log2(n), dove:
 * - n è il vocabolario del programma (operatori unici + operandi unici)
 * - N è la lunghezza del programma (totale operatori + totale operandi)
 */
public class HalsteadVolume implements CodeMetric {

    private final Map<String, Double> cache = new HashMap<>();

    @Override
    public String getName() {
        return "HalsteadVolume";
    }

    @Override
    public Double calculate(CKMethodResult method, CKClassResult cls, Path root) {
        // Uso di getParameters() invece di getParameterCount()
        String methodKey = cls.getClassName() + "#" + method.getMethodName() + method.getParametersQty();

        // Usa la cache se il metodo è già stato analizzato
        if (cache.containsKey(methodKey)) {
            return cache.get(methodKey);
        }

        try {
            // Trova il file sorgente
            String filePath = cls.getFile();
            if (filePath == null) {
                return 0.0;
            }

            // Analizza il file e trova la dichiarazione del metodo
            CompilationUnit cu = StaticJavaParser.parse(Files.readString(Path.of(filePath)));
            Optional<MethodDeclaration> methodDecl = findMethod(cu, method);

            if (methodDecl.isPresent()) {
                // Calcola le metriche di Halstead
                HalsteadCounter counter = new HalsteadCounter();
                methodDecl.get().accept(counter, null);

                int distinctOperators = counter.getDistinctOperators().size();
                int distinctOperands = counter.getDistinctOperands().size();
                int totalOperators = counter.getTotalOperators();
                int totalOperands = counter.getTotalOperands();

                // Calcola il volume di Halstead
                int vocabulary = distinctOperators + distinctOperands;
                int length = totalOperators + totalOperands;

                double volume = 0.0;
                if (vocabulary > 0 && length > 0) {
                    volume = length * (Math.log(vocabulary) / Math.log(2));
                }

                cache.put(methodKey, volume);
                return volume;
            }
        } catch (IOException e) {
            System.err.println("Errore nell'analisi del metodo " + methodKey + ": " + e.getMessage());
        }

        return 0.0;
    }

    /**
     * Trova la dichiarazione del metodo nell'albero AST
     */
    private Optional<MethodDeclaration> findMethod(CompilationUnit cu, CKMethodResult method) {
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(md -> md.getNameAsString().equals(method.getMethodName())
                        // Controllo più generico sui parametri
                        && md.getParameters().size() == method.getParametersQty())
                .findFirst();
    }

    /**
     * Visitor per contare operatori e operandi
     */
    private static class HalsteadCounter extends com.github.javaparser.ast.visitor.VoidVisitorAdapter<Void> {
        private final Set<String> distinctOperators = new HashSet<>();
        private final Set<String> distinctOperands = new HashSet<>();
        private int totalOperators = 0;
        private int totalOperands = 0;

        public Set<String> getDistinctOperators() {
            return distinctOperators;
        }

        public Set<String> getDistinctOperands() {
            return distinctOperands;
        }

        public int getTotalOperators() {
            return totalOperators;
        }

        public int getTotalOperands() {
            return totalOperands;
        }

        // Operatori binari (+=, +, -, *, etc.)
        @Override
        public void visit(BinaryExpr n, Void arg) {
            String operator = n.getOperator().asString();
            distinctOperators.add(operator);
            totalOperators++;
            super.visit(n, arg);
        }

        // Operatori unari (++, --, !, etc.)
        @Override
        public void visit(UnaryExpr n, Void arg) {
            String operator = n.getOperator().asString();
            distinctOperators.add(operator);
            totalOperators++;
            super.visit(n, arg);
        }

        // Variabili e nomi
        @Override
        public void visit(NameExpr n, Void arg) {
            String name = n.getNameAsString();
            distinctOperands.add(name);
            totalOperands++;
            super.visit(n, arg);
        }

        // Letterali (numeri, stringhe, etc.)
        // Rimuoviamo @Override poiché non è effettivamente un metodo sovrascritto
        public void visit(LiteralExpr n, Void arg) {
            String literal = n.toString();
            distinctOperands.add(literal);
            totalOperands++;
            // Per gestire tutti i tipi di LiteralExpr
            if (n instanceof IntegerLiteralExpr || n instanceof DoubleLiteralExpr ||
                    n instanceof StringLiteralExpr || n instanceof BooleanLiteralExpr ||
                    n instanceof CharLiteralExpr) {
                // Già conteggiato sopra
            }
        }

        // Chiamate di metodo
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            String methodName = n.getNameAsString();
            distinctOperators.add("call");
            distinctOperands.add(methodName);
            totalOperators++;
            totalOperands++;
            super.visit(n, arg);
        }

        // Controllo di flusso (if, for, while, etc.)
        @Override
        public void visit(IfStmt n, Void arg) {
            distinctOperators.add("if");
            totalOperators++;
            super.visit(n, arg);
        }

        @Override
        public void visit(ForStmt n, Void arg) {
            distinctOperators.add("for");
            totalOperators++;
            super.visit(n, arg);
        }

        @Override
        public void visit(WhileStmt n, Void arg) {
            distinctOperators.add("while");
            totalOperators++;
            super.visit(n, arg);
        }

        @Override
        public void visit(DoStmt n, Void arg) {
            distinctOperators.add("do");
            totalOperators++;
            super.visit(n, arg);
        }

        @Override
        public void visit(SwitchStmt n, Void arg) {
            distinctOperators.add("switch");
            totalOperators++;
            super.visit(n, arg);
        }

        // Altri costrutti
        @Override
        public void visit(AssignExpr n, Void arg) {
            String operator = n.getOperator().asString();
            distinctOperators.add(operator);
            totalOperators++;
            super.visit(n, arg);
        }
    }
}