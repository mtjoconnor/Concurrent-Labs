# Practical 2 Report (Dining Philosophers)

## Implemented Variants
- Variant 1 (required): one right-handed philosopher (`RightHandedOne`).
- Variant 2 (required): butler process allowing at most `n-1` seated philosophers (`Butler`).
- Variant 3 (optional): timeout-based second-fork acquisition with randomized retry backoff (`Timeouts`).

## Design Notes
- Forks are modelled as processes with `pick` and `put` channels.
- A philosopher acquires a fork by sending on `pick`, and releases with `put`.
- Variant 1 changes fork acquisition order for one philosopher.
- Variant 2 uses a separate butler process controlled by `enter`/`leave` channels.
- Variant 3 uses `sendWithin` on the second fork and randomized delay after timeout to avoid lockstep retries.

## Testing Notes
- `DiningPhilosophersTest` runs all three variants on finite meal counts.
- For each run, test asserts every philosopher reached `mealsPerPhilosopher`.
- Reported vectors show per-philosopher completion counts.
