package model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class MethodHistory {
    private String firstRelease;
    private int changes;
    private int churn;
    private int locAdded;
    private Set<String> authors = new HashSet<>();
    private LocalDate firstSeen;

    public MethodHistory(String release) {
        this.firstRelease = release;
        this.changes = 0;
        this.churn = 0;
        this.locAdded = 0;
        this.firstSeen = LocalDate.now();
        this.authors = new HashSet<>();
    }

    public MethodHistory(String firstRelease, int changes, int churn, int locAdded) {
        this.firstRelease = firstRelease;
        this.changes = changes;
        this.churn = churn;
        this.locAdded = locAdded;
        this.firstSeen = LocalDate.now();
        this.authors = new HashSet<>();
    }

    public MethodHistory(String firstRelease, int changes, int churn, int locAdded,
                         Set<String> authors, LocalDate firstSeen) {
        this.firstRelease = firstRelease;
        this.changes = changes;
        this.churn = churn;
        this.locAdded = locAdded;
        this.authors = authors != null ? authors : new HashSet<>();
        this.firstSeen = firstSeen != null ? firstSeen : LocalDate.now();
    }

    public void update(String release) {
        this.changes++;
        // Qui dovresti aggiungere logica per aggiornare churn e locAdded
    }

    public void addAuthor(String author) {
        if (author != null && !author.isEmpty()) {
            this.authors.add(author);
        }
    }

    public int getAuthors() {
        return this.authors.size();
    }
}