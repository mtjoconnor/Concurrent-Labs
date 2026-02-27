# Practical 3 Report (Concurrent Depth-First Search)

## Implemented Tasks
- Implemented `ConcGraphSearch.apply` in `ConcGraphSearch.scala`.
- Uses a shared concurrent stack and `numWorkers` worker threads.
- Includes safe termination for both cases:
  - a target is found;
  - no reachable target exists (optional requirement handled).

## Design Notes
- The implementation keeps a shared `pending` count:
  - incremented when successors are pushed;
  - decremented when a worker finishes processing a node.
- Workers block when the stack is empty but `pending > 0`, and terminate when:
  - `result` is set, or
  - `pending == 0` and no work remains.
- Successors are pushed in list order onto a LIFO stack, preserving DFS behavior.

## Testing Notes
- Verified with provided Sudoku driver:
  - solvable puzzles return a valid completed board;
  - `impossible.sud` returns `No solution found`.
