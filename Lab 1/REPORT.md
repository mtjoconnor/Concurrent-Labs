# Practical 1 Report (Sorting Networks)

## Implemented Tasks
- Q1 (required): comparator process (`comparator`) that accepts inputs in either order and emits outputs in either order.
- Q2 (required): 4-input sorting network (`sort4`) with five comparators.
- Q3 (required): recursive insertion network (`insert`) with the circuit structure from the sheet.
- Q5 (required): recursive insertion-sort network (`insertionSort`) built from `insert`.

## Optional Tasks
- Q4 (`O(log n)` insertion network): not implemented.
- Q6/Q7 (bitonic merge/sort): not implemented.

## Design Notes
- Each component is modelled as a `ThreadGroup`, and composition follows the circuit topology.
- The comparator uses `alt` both when receiving and sending so it can synchronise in either order.
- Recursive circuits are connected with internal `SyncChan[Int]` channels.
- Channel closure (`Closed`) is used to terminate comparator loops cleanly after one-shot tests finish.

## Testing Summary
- `SortingNetworksTest` runs randomized checks for:
  - `sort4`: sortedness + permutation.
  - `insert`: sortedness + permutation of `x :: xs` where `xs` is sorted.
  - `insertionSort`: sortedness + permutation for random lists.
- Edge and negative behavior:
  - Preconditions are enforced with `require` in each exported function.
