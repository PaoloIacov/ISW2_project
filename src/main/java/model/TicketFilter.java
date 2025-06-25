package model;

import lombok.Data;
import model.enums.ResolutionType;
import model.enums.TicketStatus;
import model.enums.TicketType;

import java.util.List;

@Data
public class TicketFilter {
    private List<ResolutionType> resolutions;
    private List<TicketStatus> statuses;
    private List<TicketType> types;
    private List<String> fields;
}
