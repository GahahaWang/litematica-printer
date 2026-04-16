package me.aleksilassila.litematica.printer.utils;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongRBTreeMap;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.core.BlockPos;

public class PositionCache {

    private final Long2LongOpenHashMap positionCache = new Long2LongOpenHashMap(); // pos -> cacheTime
    private final Long2LongRBTreeMap timeIndex = new Long2LongRBTreeMap(); // cacheTime -> pos list
    private static final int MAX_CACHE_SIZE = 256;
    private static long counter = 0;
    public boolean isPositionCached(BlockPos pos) {
        long key = pos.asLong();
        return positionCache.containsKey(key);
    }

    public void pruneExpiredCacheEntries() {
        long now = System.nanoTime();
        long threshold = now - Configs.PRINTER_CACHE_MS.getIntegerValue()*1000000L;

        while (!timeIndex.isEmpty()) {
            long oldestTime = timeIndex.firstLongKey();
            if (oldestTime > threshold) break;
            long pos = timeIndex.remove(oldestTime);
            positionCache.remove(pos);
        }
        while (positionCache.size() > MAX_CACHE_SIZE) {
            long oldestTime = timeIndex.firstLongKey();
            long pos = timeIndex.remove(oldestTime);
            positionCache.remove(pos);
        }
    }

    public void cachePosition(BlockPos pos) {
        long currentTime = System.nanoTime() + counter++;
        long key = pos.asLong();
        positionCache.put(key, currentTime);
        timeIndex.put(currentTime, key);
    }
}
