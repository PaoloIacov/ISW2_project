package model;

import lombok.*;
import model.enums.ResolutionType;
import model.enums.TicketStatus;
import model.enums.TicketType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@RequiredArgsConstructor
@NoArgsConstructor
public class Ticket {

    @NonNull
    private String id;

    @NonNull
    private String key;

    @NonNull
    private LocalDate issueDate;

    @NonNull
    private LocalDate closedDate;

    @NonNull
    private TicketType type;

    @NonNull
    private TicketStatus status;

    @NonNull
    private String assignee;

    private ResolutionType resolution;

    private String summary;

    private Release injected;

    private Release fixed;

    private Release opening;

    private List<Release> affectedVersions;

    private List<CommitInfo> associatedCommits;

    public void addCommit(CommitInfo commit) {
        if (this.associatedCommits == null) {
            this.associatedCommits = new ArrayList<>();
        }
        this.associatedCommits.add(commit);
    }

    public CommitInfo getLastCommit() {
        if (this.associatedCommits != null && !this.associatedCommits.isEmpty()) {
            return this.associatedCommits.get(this.associatedCommits.size() - 1);
        }
        return null;
    }
}
