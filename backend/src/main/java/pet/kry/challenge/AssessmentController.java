package pet.kry.challenge;

import pet.kry.challenge.exception.BookingNotFoundException;
import pet.kry.challenge.exception.SlotUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AssessmentController {
    private final SchedulingService scheduling;
    public AssessmentController(SchedulingService scheduling) { this.scheduling = scheduling; }

    public record AssessmentRequest(List<Integer> answers) {}
    public record AssessmentResponse(int score, String recommendation, List<OffsetDateTime> availableSlots) {}
    public record BookingRequest(OffsetDateTime slot, String recommendation) {}
    public record BookingResponse(String confirmationId, OffsetDateTime slot, String recommendation) {}

    @PostMapping("/assessment")
    public AssessmentResponse assess(@RequestBody AssessmentRequest request) {
        if (request == null || request.answers() == null || request.answers().size() != 5 ||
            request.answers().stream().anyMatch(x -> x == null || x < 1 || x > 3)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide five answers scored 1–3");
        }
        int score = request.answers().stream().mapToInt(Integer::intValue).sum();
        String recommendation = score <= 7 ? "Chat" : score <= 11 ? "Nurse" : "Doctor";
        return new AssessmentResponse(score, recommendation, scheduling.availableSlots());
    }

    @PostMapping("/booking")
    public BookingResponse book(@RequestBody BookingRequest request) {
        if (request == null || request.slot() == null || request.recommendation() == null ||
            !List.of("Chat", "Nurse", "Doctor").contains(request.recommendation())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid booking");
        }
        try {
            return new BookingResponse(scheduling.book(request.slot()), request.slot(), request.recommendation());
        } catch (SlotUnavailableException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "This appointment is no longer available. Please choose another time.", e);
        }
    }

    @DeleteMapping("/booking/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable("id") String id) {
        try {
            scheduling.cancelBooking(id);
        } catch (BookingNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No booking found with that reference.", e);
        }
    }
}
