package manager;

import config.PropertiesManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import model.CommitInfo;
import model.ModifiedMethod;
import model.Ticket;
import model.TicketFilter;
import model.enums.ResolutionType;
import model.enums.TicketStatus;
import model.enums.TicketType;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Slf4j
public class CommitManager {

    @Getter
    private final String projectName;

    private final Repository repository;
    private final Git git;
    private final Pattern ticketPatternWithProjectName;
    private final Pattern ticketPatternWithIssue;
    private final Pattern ticketPatternWithHashtag;
    private final TicketsManager ticketsManager;

    /**
     * Creates a new Git Commit Manager for the specified repository path and project name
     *
     * @throws IOException if the repository can't be accessed
     */
    public CommitManager(TicketsManager ticketsManager) throws IOException {
        this.ticketsManager = ticketsManager;
        this.projectName = PropertiesManager.getInstance().getProperty("info.name");

        // Initialize the repository
        String repoPath = PropertiesManager.getInstance().getProperty("info.repo.path");
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        repository = builder.setGitDir(new File(repoPath + "/.git")).readEnvironment().findGitDir().build();

        git = new Git(repository);

        this.ticketPatternWithProjectName = Pattern.compile("(" + projectName + "-\\d+)");
        this.ticketPatternWithIssue = Pattern.compile("(ISSUE\\s\\d+)");
        this.ticketPatternWithHashtag = Pattern.compile("(#\\d+)");

    }

    /**
     * Retrieves all commits from the repository and associates them with ticket IDs
     */
    public void getCommitsWithTickets() {
        // Retrieves all the tickets from the tickets manager
        TicketFilter filter = new TicketFilter();
        filter.setStatuses(List.of(TicketStatus.CLOSED, TicketStatus.RESOLVED));
        filter.setTypes(List.of(TicketType.BUG));
        filter.setResolutions(List.of(ResolutionType.FIXED));

        ticketsManager.retrieveTickets(filter);
        List<Ticket> tickets = ticketsManager.getTickets();

        try {
            // Executes the `git log` command to retrieve all commits from the repository
            LogCommand logCommand = git.log();
            Iterable<RevCommit> commits = logCommand.call();

            // Iterate through all commits and extract ticket IDs from commit messages
            for (RevCommit commit : commits) {
                String commitMessage = commit.getFullMessage();
                List<String> ticketIds = extractTicketIds(commitMessage);

                if (ticketIds.isEmpty())
                    log.debug("No ticket IDs found in commit {}. Message: {}", commit.getId(), commitMessage);

                CommitInfo commitInfo = new CommitInfo(commit.getName(), commit.getAuthorIdent().getName(), commit.getAuthorIdent().getEmailAddress(), LocalDate.ofInstant(Instant.ofEpochSecond(commit.getCommitTime()), ZoneId.systemDefault()), commitMessage);

                // For each ticket ID found in the commit message
                String finalTicketId;
                String standardTicketPrefix = projectName.toUpperCase() + "-";
                for (String ticketId : ticketIds) {
                    // Check if the ticket ID is prefixed with the project name.
                    // If not, the current prefix is substituted with the standard one
                    finalTicketId = ticketId;
                    if (ticketPatternWithIssue.matcher(ticketId).matches()) {
                        finalTicketId = ticketId.replace("ISSUE ", standardTicketPrefix).trim();
                    }

                    if (ticketPatternWithHashtag.matcher(ticketId).matches()) {
                        finalTicketId = ticketId.replace("#", standardTicketPrefix).trim();
                    }

                    // Check if any ticket in the ticket list has a matching key
                    for (Ticket ticket : tickets) {
                        if (ticket.getKey().equalsIgnoreCase(finalTicketId)) {
                            ticket.addCommit(commitInfo);
                            break;
                        }
                    }
                    log.debug("No ticket of type \"BUG\" found matching any of the following patterns: {}.", finalTicketId);
                }
            }

            // After gaining all ticket commits, infers the fix version from these (when not available)
            this.ticketsManager.setFixVersionToTickets();
        } catch (GitAPIException e) {
            log.error("Error accessing Git repository: {}", e.getMessage(), e);
        }
    }

    /**
     * Extracts ticket IDs from a commit message
     *
     * @param commitMessage the commit message to search
     * @return list of ticket IDs found in the commit message
     */
    private List<String> extractTicketIds(String commitMessage) {
        List<String> ticketIds = new ArrayList<>();

        // Using the uppercase commit message to count all occurrences of the ticket IDs
        Matcher matcher = ticketPatternWithProjectName.matcher(commitMessage.toUpperCase());
        while (matcher.find()) if (!ticketIds.contains(matcher.group())) ticketIds.add(matcher.group());

        matcher = ticketPatternWithIssue.matcher(commitMessage.toUpperCase());
        while (matcher.find()) if (!ticketIds.contains(matcher.group())) ticketIds.add(matcher.group());

        matcher = ticketPatternWithHashtag.matcher(commitMessage.toUpperCase());
        while (matcher.find()) if (!ticketIds.contains(matcher.group())) ticketIds.add(matcher.group());

        return ticketIds;
    }

    /**
     * Closes the Git repository
     */
    public void close() {
        git.close();
        repository.close();
    }

    /**
     * Analyzes a commit to find all Java methods that were modified in that commit.
     *
     * @param commitId The ID of the commit to analyze
     * @return A list of modified Java methods with their file paths
     * @throws IOException     If there's an error accessing the Git repository
     * @throws GitAPIException If there's an error executing Git commands
     */
    public List<ModifiedMethod> getModifiedJavaMethods(String commitId) throws IOException, GitAPIException {
        List<ModifiedMethod> modifiedMethods = new ArrayList<>();

        // Get the commit object
        RevCommit commit = repository.parseCommit(repository.resolve(commitId));

        // If it's the first commit, we don't have a parent to compare with
        if (commit.getParentCount() == 0) {
            // For the first commit, get all files added
            try (Git git = new Git(repository)) {
                RevTree tree = commit.getTree();
                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(tree);
                    treeWalk.setRecursive(true);
                    treeWalk.setFilter(PathFilter.create(".java"));

                    while (treeWalk.next()) {
                        String path = treeWalk.getPathString();
                        if (path.endsWith(".java")) {
                            // Get file content
                            ObjectId objectId = treeWalk.getObjectId(0);
                            try (ObjectReader reader = repository.newObjectReader()) {
                                ObjectLoader loader = reader.open(objectId);
                                byte[] bytes = loader.getBytes();
                                String content = new String(bytes, StandardCharsets.UTF_8);

                                // Extract all methods from the file
                                List<String> methods = extractJavaMethods(content);
                                for (String method : methods) {
                                    modifiedMethods.add(new ModifiedMethod(path, method, ModificationType.ADDED));
                                }
                            }
                        }
                    }
                }
            }
            return modifiedMethods;
        }

        // For non-first commits, compare with parent
        RevCommit parentCommit = commit.getParent(0);
        ObjectReader reader = repository.newObjectReader();

        // Get the diff between this commit and its parent
        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            df.setRepository(repository);
            df.setDiffComparator(RawTextComparator.DEFAULT);
            df.setDetectRenames(true);

            List<DiffEntry> diffs = df.scan(parentCommit.getTree(), commit.getTree());

            for (DiffEntry diff : diffs) {
                // Only consider Java files
                if (!diff.getNewPath().endsWith(".java") && !diff.getOldPath().endsWith(".java")) {
                    continue;
                }

                // Get the edit list for this file
                EditList editList = df.toFileHeader(diff).toEditList();

                // Get old and new file content
                String oldContent = "";
                if (diff.getChangeType() != DiffEntry.ChangeType.ADD) {
                    oldContent = getFileContent(parentCommit, diff.getOldPath());
                }

                String newContent = "";
                if (diff.getChangeType() != DiffEntry.ChangeType.DELETE) {
                    newContent = getFileContent(commit, diff.getNewPath());
                }

                // Find modified methods based on the type of change
                switch (diff.getChangeType()) {
                    case ADD:
                        // New file - all methods are added
                        List<String> addedMethods = extractJavaMethods(newContent);
                        for (String method : addedMethods) {
                            modifiedMethods.add(new ModifiedMethod(diff.getNewPath(), method, ModificationType.ADDED));
                        }
                        break;

                    case DELETE:
                        // Deleted file - all methods are deleted
                        List<String> deletedMethods = extractJavaMethods(oldContent);
                        for (String method : deletedMethods) {
                            modifiedMethods.add(new ModifiedMethod(diff.getOldPath(), method, ModificationType.DELETED));
                        }
                        break;

                    case MODIFY:
                    case RENAME:
                    case COPY:
                        // For modified/renamed/copied files, we need to identify which methods were changed
                        Map<String, String> oldMethods = extractJavaMethodsWithSignatures(oldContent);
                        Map<String, String> newMethods = extractJavaMethodsWithSignatures(newContent);

                        // Methods in an old file but not in the new file were deleted
                        for (Map.Entry<String, String> entry : oldMethods.entrySet()) {
                            if (!newMethods.containsKey(entry.getKey())) {
                                modifiedMethods.add(new ModifiedMethod(diff.getOldPath(), entry.getValue(), ModificationType.DELETED));
                            }
                        }

                        // Methods in new file but not in old file were added
                        for (Map.Entry<String, String> entry : newMethods.entrySet()) {
                            if (!oldMethods.containsKey(entry.getKey())) {
                                modifiedMethods.add(new ModifiedMethod(diff.getNewPath(), entry.getValue(), ModificationType.ADDED));
                            } else if (!oldMethods.get(entry.getKey()).equals(entry.getValue())) {
                                // Method exists in both but content is different - modified
                                modifiedMethods.add(new ModifiedMethod(diff.getNewPath(), entry.getValue(), ModificationType.MODIFIED));
                            }
                        }
                        break;
                }
            }
        }

        return modifiedMethods;
    }

    /**
     * Get the content of a file in a specific commit
     */
    private String getFileContent(RevCommit commit, String path) throws IOException {
        try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, commit.getTree())) {
            if (treeWalk == null) {
                return "";
            }

            ObjectId objectId = treeWalk.getObjectId(0);
            ObjectLoader loader = repository.open(objectId);
            byte[] bytes = loader.getBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /**
     * Extracts method names from Java source code.
     * Note: This is a simplified implementation that may not handle all Java syntax edge cases.
     */
    private List<String> extractJavaMethods(String javaContent) {
        List<String> methods = new ArrayList<>();

        // Simple regex pattern to match method declarations
        // This is a simplified approach and won't handle all possible Java syntax
        Pattern methodPattern = Pattern.compile("(?:public|protected|private|static|\\s) +(?:[\\w<>\\[\\]]+\\s+)+(\\w+) *\\([^)]*\\) *(\\{?|[^;])");

        Matcher matcher = methodPattern.matcher(javaContent);

        while (matcher.find()) {
            // Extract the method with its body using brace matching
            int startPos = matcher.start();
            if (startPos >= 0 && matcher.group().contains("{")) {
                int openBraces = 1;
                int pos = javaContent.indexOf('{', startPos) + 1;

                while (openBraces > 0 && pos < javaContent.length()) {
                    char c = javaContent.charAt(pos);
                    if (c == '{') openBraces++;
                    else if (c == '}') openBraces--;
                    pos++;
                }

                if (pos <= javaContent.length()) {
                    String method = javaContent.substring(startPos, pos).trim();
                    methods.add(method);
                }
            }
        }

        return methods;
    }

    /**
     * Extracts Java methods with their signatures as keys for comparison
     */
    private Map<String, String> extractJavaMethodsWithSignatures(String javaContent) {
        Map<String, String> methodMap = new HashMap<>();

        // Pattern to match method signatures
        Pattern methodPattern = Pattern.compile("(?:public|protected|private|static|\\s) +(?:[\\w<>\\[\\]]+\\s+)+(\\w+) *\\([^)]*\\) *(\\{?|[^;])");

        Matcher matcher = methodPattern.matcher(javaContent);

        while (matcher.find()) {
            int startPos = matcher.start();
            String methodSignature = matcher.group().trim();

            // Extract method name and parameters for the key
            String methodName = methodSignature.substring(methodSignature.lastIndexOf(' ') + 1, methodSignature.indexOf('('));
            String parameters = methodSignature.substring(methodSignature.indexOf('('), methodSignature.lastIndexOf(')') + 1);
            String key = methodName + parameters;

            if (startPos >= 0 && methodSignature.contains("{")) {
                int openBraces = 1;
                int pos = javaContent.indexOf('{', startPos) + 1;

                while (openBraces > 0 && pos < javaContent.length()) {
                    char c = javaContent.charAt(pos);
                    if (c == '{') openBraces++;
                    else if (c == '}') openBraces--;
                    pos++;
                }

                if (pos <= javaContent.length()) {
                    String method = javaContent.substring(startPos, pos).trim();
                    methodMap.put(key, method);
                }
            }
        }

        return methodMap;
    }

    /**
     * Analyzes all commits to find all Java methods that were modified across the repository history
     *
     * @return A map of commit IDs to lists of modified Java methods
     * @throws IOException     If there's an error accessing the Git repository
     * @throws GitAPIException If there's an error executing Git commands
     */
    public Map<String, List<ModifiedMethod>> getAllCommitsModifiedMethods() throws IOException, GitAPIException {
        Map<String, List<ModifiedMethod>> commitsWithModifiedMethods = new HashMap<>();

        try {
            LogCommand logCommand = git.log();
            Iterable<RevCommit> commits = logCommand.call();

            for (RevCommit commit : commits) {
                String commitId = commit.getName();
                List<ModifiedMethod> modifiedMethods = getModifiedJavaMethods(commitId);

                if (!modifiedMethods.isEmpty()) {
                    commitsWithModifiedMethods.put(commitId, modifiedMethods);
                }
            }
        } catch (GitAPIException e) {
            log.error("Error accessing Git repository: {}", e.getMessage(), e);
            throw e;
        }

        return commitsWithModifiedMethods;
    }

    /**
     * Type of modification to a method
     */
    public enum ModificationType {
        ADDED, MODIFIED, DELETED
    }

    public void testForTicketIdInCommitMessage(String ticketKey) {
        Pattern pattern = Pattern.compile(ticketKey.replace("BOOKKEEPER-", ""));

        try {
            // Executes the `git log` command to retrieve all commits from the repository
            LogCommand logCommand = git.log();
            Iterable<RevCommit> commits = logCommand.call();

            // Iterate through all commits and extract ticket IDs from commit messages
            for (RevCommit commit : commits) {
                String commitMessage = commit.getFullMessage();

                List<String> ticketIds = new ArrayList<>();
                Matcher matcher = pattern.matcher(commitMessage);

                while(matcher.matches()) {
                    ticketIds.add(matcher.group());
                }

                if (!ticketIds.isEmpty())
                    log.debug("Ticket {} found in commit {}. Message: {}", ticketKey, commit.getId(), commitMessage);
            }
        } catch (GitAPIException e) {
            log.error("Error accessing Git repository: {}", e.getMessage(), e);
        }
    }
}