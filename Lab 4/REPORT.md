# Practical 4 Report (Sleeping Tutor)

## Implemented Tasks
- Implemented required protocol as monitor: `SleepingTutorMonitor`.
- Implemented optional semaphore version: `SleepingTutorSemaphore`.
- Added tests checking both required properties for multiple cycles.

## Protocol Requirements Covered
1. Tutor starts teaching only after both students have arrived.
2. Students leave only after tutor ends the tutorial.

## Design Notes
- Monitor version uses explicit phase states:
  - `WaitingForStudents`
  - `Teaching`
  - `TeachingEnded`
- Semaphore version uses:
  - a gate semaphore (at most two arrivals per cycle),
  - partner/tutor wake semaphores,
  - completion semaphore released by tutor at tutorial end.
- Both implementations reset safely for repeated tutorial cycles.

## Testing Notes
- `SleepingTutorTest` runs finite-cycle scenarios for both implementations.
- Events are logged and validated per cycle to ensure both requirements hold.
