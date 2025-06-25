package model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum TicketType {

    BUG("bug"),
    NEW_FEATURE("new feature"),
    OTHER("other");

    @Getter
    private final String type;


    /**
     * Returns the TicketType enum value corresponding to the given string.
     *
     * @param type the string representation of the ticket type
     * @return the corresponding TicketType enum value, or OTHER if no match is found
     */
    public static TicketType from(String type) {
        for (TicketType t : values()) {
            if (t.getType().equalsIgnoreCase(type)) {
                return t;
            }
        }
        return OTHER;
    }
}
