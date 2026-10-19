package pet.kry.challenge;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pet.kry.challenge.exception.BookingNotFoundException;
import pet.kry.challenge.exception.SlotUnavailableException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssessmentControllerTest {
    @Test void availableSlotReturnsConfirmation() throws Exception {
        // Fake service that accepts the booking and returns a known id
        SchedulingService scheduling = mock(SchedulingService.class);
        when(scheduling.book(any())).thenReturn("booking-123");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AssessmentController(scheduling)).build();

        // A valid request for a free slot returns 200 with the service's confirmation id
        mvc.perform(post("/api/booking").contentType(MediaType.APPLICATION_JSON)
                .content("{\"slot\":\"2026-10-12T08:00:00+02:00\",\"recommendation\":\"Nurse\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.confirmationId").value("booking-123"));
    }

    @Test void unavailableSlotReturnsConflict() throws Exception {
        // Fake service that always says "slot taken", so this test only covers how the controller handles it
        SchedulingService scheduling = mock(SchedulingService.class);
        when(scheduling.book(any())).thenThrow(new SlotUnavailableException());
        // Fake HTTP client for this one controller; no server or Spring context is started
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AssessmentController(scheduling)).build();

        // A valid booking request for a slot the service refuses must return 409 Conflict, not 500
        mvc.perform(post("/api/booking").contentType(MediaType.APPLICATION_JSON)
                .content("{\"slot\":\"2026-10-12T08:00:00+02:00\",\"recommendation\":\"Nurse\"}"))
            .andExpect(status().isConflict());
    }

    @Test void cancellingReturnsNoContent() throws Exception {
        // Fake service: cancelBooking() does nothing by default, i.e. succeeds
        SchedulingService scheduling = mock(SchedulingService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AssessmentController(scheduling)).build();

        mvc.perform(delete("/api/booking/booking-123")).andExpect(status().isNoContent());
    }

    @Test void cancellingUnknownBookingReturnsNotFound() throws Exception {
        SchedulingService scheduling = mock(SchedulingService.class);
        doThrow(new BookingNotFoundException()).when(scheduling).cancelBooking("unknown");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AssessmentController(scheduling)).build();

        mvc.perform(delete("/api/booking/unknown")).andExpect(status().isNotFound());
    }
}
