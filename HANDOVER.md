# Handover

In-memory, single-instance PoC without user accounts. Built with AI assistance; changes reviewed and verified.

## Priorities
1. **Overbooking (business-critical).** In manual testing I could book a slot repeatedly: `book()` never checked capacity. It now re-checks on the server under a lock and returns 409 when full. Times are normalised to Europe/Stockholm (`06:00Z` and `08:00+02:00` were separate slots), and clinicians no longer count at their exact shift end. A clear 409 message explains that the appointment is no longer available; the UI refreshes availability.
2. **All slots shown.** Removed the UI’s 60-slot limit to show today plus the next three days.

## Feature decision
Cancellation (A), the inverse of booking.

Key decision: cancelling decrements the per-slot counter that availability reads, under the booking lock, so the slot reappears automatically. The confirmation UUID identifies a booking and works as an unguessable bearer token. Patients enter it on the start screen, since they usually cancel later.

Rejected: cancellation only from the confirmation page, because the current UI does not preserve that page across sessions.

## Deliberately unfinished
- **Rescheduling.** Not implemented; I focused on cancellation as a useful, complete part of scenario A.
- **Slot picker UX.** The full list (up to 160 slots) needs a redesign, e.g. grouping by day or a date picker.
- **Cancellation UI.** Kept cancellation on the start screen; omitted a separate page and confirmation dialog.

## Verification
`cd backend && ./gradlew test` → BUILD SUCCESSFUL, 12 tests, 0 failures.

Unit tests cover:
- a full slot refused and no longer offered
- equivalent offsets
- shift end
- concurrent requests for booking
- cancellation freeing the slot
- unknown or repeated cancels
- controller status codes

Manual, in two tabs: rebooking 08:00 showed the conflict message, and 08:00 left the list. After cancelling, it reappeared.

Not verified: DST changeover, multiple instances.
