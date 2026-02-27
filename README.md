# Concurrent Programming Labs (Scala 2.13 + SCL)

This repository contains solutions for four Concurrent Programming practical labs.

## Environment Setup

These labs use:
- Scala 2.13
- SCL (`ox` package) available in this repository at `./ox`

Lecture 1 setup guidance applied:
1. Install Scala 2.13.
   - Example on macOS: `brew install scala@2.13`
2. Ensure classpath includes the local `ox` folder.
   - One-off: use `-classpath .` when running from repository root.
   - Optional shell config:
     - `export CLASSPATH=.:/absolute/path/to/Concurrent Programming:$CLASSPATH`

The local environment used for validation:
- `scala 2.13.17`
- `scalac 2.13.17`

## Repository Layout
- `Lab 1/`: Sorting networks (comparator, sort4, insertion-based network sort).
- `Lab 2/`: Dining philosophers variants (right-handed, butler, timeout).
- `Lab 3/Distribution/`: Concurrent depth-first Sudoku search.
- `Lab 4/`: Sleeping tutor monitor/semaphore protocols.

Each lab folder includes:
- implementation code,
- `REPORT.md`,
- `TEST_COMMANDS.txt`.

## Validation Entry Points
- Lab 1: `scala -classpath . SortingNetworksTest`
- Lab 2: `scala -classpath . DiningPhilosophersTest`
- Lab 3: `cd "Lab 3/Distribution" && scala -classpath .:../.. Sudoku test1.sud --conc`
- Lab 4: `scala -classpath . SleepingTutorTest`

## Public Interface Changes
No public interface changes.
