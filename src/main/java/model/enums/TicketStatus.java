package model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum TicketStatus {

    OPEN("open"),
    IN_PROGRESS("in progress"),
    REOPENED("reopened"),
    ON_HOLD("on hold"),
    BLOCKED("blocked"),
    DUPLICATE("duplicate"),
    VERIFIED("verified"),
    INVESTIGATED("investigated"),
    ASSIGNED("assigned"),
    FIXED("fixed"),
    RESOLVED("resolved"),
    CLOSED("closed"),
    OTHER("other");

    @Getter
    private final String status;

    public static TicketStatus fromString(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Status cannot be null or empty");
        }

        for (TicketStatus status : TicketStatus.values()) {
            if (status.getStatus().equalsIgnoreCase(text)) {
                return status;
            }
        }
        return OTHER;
    }

}
