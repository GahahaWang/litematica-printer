package me.aleksilassila.litematica.printer.utils;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.Arrays;
import net.minecraft.core.BlockPos;

/**
 * Picks which water blocks get a bucket so that infinite water fills all the others, following
 * {@code FlowingFluid#getNewLiquid}: a block turns into a source once two horizontal neighbors it connects to are
 * sources and the block below is solid or a source.
 *
 * <p>The fewest buckets is a minimum percolating set of 2-neighbor bootstrap percolation: ⌈(width + length) / 2⌉ along a
 * diagonal for a rectangle, hard in general. Buckets go greedily where they make the most blocks fill, which grows
 * filled areas diagonally and finds that diagonal on rectangles.
 */
public final class InfiniteWaterSolver {
    private final long[] cells;
    // 4 per cell: index of the connected water cell on that side, or -1
    private final int[] neighbors;
    // connected sources next to a cell that are not cells themselves (an existing lake), they never change
    private final int[] outsideSources;
    private final int[] below;
    private final int[] above;
    // the block below is not a cell but holds the water up (solid or a source)
    private final boolean[] solidBelow;
    private final int[] possibleNeighbors;
    private final boolean[] filled;
    private final int[] filledNeighbors;
    private final int minY;
    // empty cells per layer, from minY up
    private final int[] emptyInLayer;
    private final int lowerBound;

    private final boolean[] candidate;
    private final IntArrayList candidates = new IntArrayList();
    private final IntArrayList changed = new IntArrayList();
    private final IntArrayFIFOQueue queue = new IntArrayFIFOQueue();

    /**
     * @param cells packed positions of the water blocks
     * @param neighbors 4 per cell, see {@link #neighbors}
     * @param below index of the cell below each cell, or -1
     * @param filled which cells are sources already
     */
    public InfiniteWaterSolver(long[] cells, int[] neighbors, int[] outsideSources, int[] below, boolean[] solidBelow, boolean[] filled) {
        this.cells = cells;
        this.neighbors = neighbors;
        this.outsideSources = outsideSources;
        this.below = below;
        this.solidBelow = solidBelow;
        this.filled = filled;

        int size = cells.length;
        this.above = new int[size];
        Arrays.fill(this.above, -1);
        this.possibleNeighbors = new int[size];
        this.filledNeighbors = new int[size];
        this.candidate = new boolean[size];

        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (long cell : cells) {
            lowest = Math.min(lowest, BlockPos.getY(cell));
            highest = Math.max(highest, BlockPos.getY(cell));
        }
        this.minY = lowest;
        this.emptyInLayer = new int[size == 0 ? 0 : highest - lowest + 1];

        for (int i = 0; i < size; i++) {
            if (!filled[i]) {
                emptyInLayer[layer(i)]++;
            }
            if (below[i] >= 0) {
                above[below[i]] = i;
            }
            possibleNeighbors[i] = outsideSources[i];
            filledNeighbors[i] = outsideSources[i];
            for (int k = 0; k < 4; k++) {
                int j = neighbors[i * 4 + k];
                if (j >= 0) {
                    possibleNeighbors[i]++;
                    if (filled[j]) {
                        filledNeighbors[i]++;
                    }
                }
            }
        }

        this.lowerBound = computeLowerBound();
    }

    /**
     * Fewest buckets any plan needs, or -1 if outside sources make the bound unsound.
     *
     * <p>Within a layer, count the open sides of the filled area: a block that fills by itself touches at least two filled
     * blocks, so it never opens more sides than it closes, while a bucket opens at most 4.
     */
    public int lowerBound() {
        return lowerBound;
    }

    private int computeLowerBound() {
        Int2IntOpenHashMap openSidesToAdd = new Int2IntOpenHashMap();

        for (int i = 0; i < cells.length; i++) {
            if (outsideSources[i] > 0) {
                return -1;
            }
            int y = BlockPos.getY(cells[i]);
            openSidesToAdd.addTo(y, 4 - possibleNeighbors[i]);
            if (filled[i]) {
                openSidesToAdd.addTo(y, -(4 - filledNeighbors[i]));
            }
        }

        int bound = 0;
        for (int openSides : openSidesToAdd.values()) {
            bound += Math.max(0, (openSides + 3) / 4);
        }
        return bound;
    }

    /**
     * Returns the indices of the cells to fill with a bucket.
     */
    public IntArrayList solve() {
        IntArrayList buckets = new IntArrayList();

        for (int i = 0; i < cells.length; i++) {
            if (filled[i]) {
                addCandidatesAround(i);
            } else if (outsideSources[i] > 0) {
                addCandidate(i);
            }
        }
        // whatever already fills by itself
        for (int i = 0; i < cells.length; i++) {
            if (canFill(i)) {
                commit(i);
            }
        }
        // blocks that never fill by themselves
        for (int i = 0; i < cells.length; i++) {
            if (!filled[i] && (possibleNeighbors[i] < 2 || !solidBelow[i] && below[i] < 0)) {
                buckets.add(i);
                commit(i);
            }
        }

        while (true) {
            int next = bestCandidate();
            if (next < 0) {
                next = startCell();
            }
            if (next < 0) {
                return buckets;
            }
            buckets.add(next);
            commit(next);
        }
    }

    private boolean canFill(int i) {
        return !filled[i] && filledNeighbors[i] >= 2 && (solidBelow[i] || below[i] >= 0 && filled[below[i]]);
    }

    /**
     * Fills {@code start} and whatever then fills by itself, recording the changes in {@link #changed}.
     */
    private int fill(int start) {
        int before = changed.size();
        queue.enqueue(start);

        while (!queue.isEmpty()) {
            int i = queue.dequeueInt();
            if (filled[i]) {
                continue;
            }
            filled[i] = true;
            emptyInLayer[layer(i)]--;
            changed.add(i);

            for (int k = 0; k < 4; k++) {
                int j = neighbors[i * 4 + k];
                if (j >= 0) {
                    filledNeighbors[j]++;
                    if (canFill(j)) {
                        queue.enqueue(j);
                    }
                }
            }
            if (above[i] >= 0 && canFill(above[i])) {
                queue.enqueue(above[i]);
            }
        }

        return changed.size() - before;
    }

    private void undo() {
        for (int n = changed.size() - 1; n >= 0; n--) {
            int i = changed.getInt(n);
            filled[i] = false;
            emptyInLayer[layer(i)]++;
            for (int k = 0; k < 4; k++) {
                int j = neighbors[i * 4 + k];
                if (j >= 0) {
                    filledNeighbors[j]--;
                }
            }
        }
        changed.clear();
    }

    private void commit(int start) {
        fill(start);
        for (int n = 0; n < changed.size(); n++) {
            addCandidatesAround(changed.getInt(n));
        }
        changed.clear();
    }

    /**
     * Cells up to two steps from a filled one, where a bucket can join up with it.
     */
    private void addCandidatesAround(int i) {
        for (int k = 0; k < 4; k++) {
            int j = neighbors[i * 4 + k];
            if (j >= 0) {
                addCandidate(j);
                for (int l = 0; l < 4; l++) {
                    int m = neighbors[j * 4 + l];
                    if (m >= 0) {
                        addCandidate(m);
                    }
                }
            }
        }
    }

    private void addCandidate(int i) {
        if (!filled[i] && !candidate[i]) {
            candidate[i] = true;
            candidates.add(i);
        }
    }

    private int layer(int i) {
        return BlockPos.getY(cells[i]) - minY;
    }

    /**
     * The candidate in the lowest layer with empty cells that fills the most blocks, or -1 if there are none.
     * A layer only fills by itself over a filled one, so a bucket higher up would be judged too early.
     */
    private int bestCandidate() {
        int lowestLayer = 0;
        while (lowestLayer < emptyInLayer.length && emptyInLayer[lowestLayer] == 0) {
            lowestLayer++;
        }

        int best = -1;
        int bestGain = 0;
        int kept = 0;

        for (int n = 0; n < candidates.size(); n++) {
            int i = candidates.getInt(n);
            if (filled[i]) {
                candidate[i] = false;
                continue;
            }
            candidates.set(kept++, i);
            if (layer(i) != lowestLayer) {
                continue;
            }

            int gain = fill(i);
            undo();
            if (best < 0 || gain > bestGain || gain == bestGain && isBetterCandidate(i, best)) {
                best = i;
                bestGain = gain;
            }
        }

        candidates.size(kept);
        return best;
    }

    // Equal gain: blocks with fewer neighbors are harder to fill by themselves, so give them the bucket.
    private boolean isBetterCandidate(int i, int best) {
        if (possibleNeighbors[i] != possibleNeighbors[best]) {
            return possibleNeighbors[i] < possibleNeighbors[best];
        }
        return isEarlier(i, best);
    }

    /**
     * Where to start when no candidate joins up with filled water: the lowest layer first, since a layer only fills
     * by itself over the one below, then a corner.
     */
    private int startCell() {
        int start = -1;

        for (int i = 0; i < cells.length; i++) {
            if (filled[i]) {
                continue;
            }
            if (start < 0) {
                start = i;
                continue;
            }
            int y = BlockPos.getY(cells[i]);
            int startY = BlockPos.getY(cells[start]);
            if (y != startY ? y < startY : isBetterCandidate(i, start)) {
                start = i;
            }
        }

        return start;
    }

    private boolean isEarlier(int i, int j) {
        long a = cells[i];
        long b = cells[j];
        if (BlockPos.getY(a) != BlockPos.getY(b)) {
            return BlockPos.getY(a) < BlockPos.getY(b);
        }
        int diagonalA = BlockPos.getX(a) + BlockPos.getZ(a);
        int diagonalB = BlockPos.getX(b) + BlockPos.getZ(b);
        if (diagonalA != diagonalB) {
            return diagonalA < diagonalB;
        }
        return BlockPos.getX(a) < BlockPos.getX(b);
    }
}
