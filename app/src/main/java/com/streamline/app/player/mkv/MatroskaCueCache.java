package com.streamline.app.player.mkv;

import android.util.SparseArray;
import androidx.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MatroskaCueCache {

    private static final int VERSION = 1;

    private final File file;
    private boolean loaded;

    public MatroskaCueCache(File file) {
        this.file = file;
    }

    public synchronized boolean hasData() {
        return file.isFile() && file.length() > 8;
    }

    public synchronized @Nullable Map<Integer, List<long[]>> read() {
        if (loaded) return null;
        loaded = true;
        if (!hasData()) return null;
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            if (input.readInt() != VERSION) return null;
            int trackCount = input.readInt();
            if (trackCount <= 0 || trackCount > 64) return null;
            Map<Integer, List<long[]>> tracks = new LinkedHashMap<>();
            for (int i = 0; i < trackCount; i++) {
                int track = input.readInt();
                int pointCount = input.readInt();
                if (pointCount <= 0 || pointCount > 2_000_000) return null;
                List<long[]> points = new ArrayList<>(pointCount);
                for (int p = 0; p < pointCount; p++) {
                    points.add(new long[]{input.readLong(), input.readLong(), input.readLong()});
                }
                tracks.put(track, points);
            }
            return tracks.isEmpty() ? null : tracks;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    public synchronized void write(Map<Integer, List<long[]>> tracks) {
        if (tracks.isEmpty()) return;
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return;
        File temp = new File(file.getPath() + ".tmp");
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(temp)))) {
            output.writeInt(VERSION);
            output.writeInt(tracks.size());
            for (Map.Entry<Integer, List<long[]>> entry : tracks.entrySet()) {
                output.writeInt(entry.getKey());
                output.writeInt(entry.getValue().size());
                for (long[] point : entry.getValue()) {
                    output.writeLong(point[0]);
                    output.writeLong(point[1]);
                    output.writeLong(point[2]);
                }
            }
        } catch (IOException ignored) {
            temp.delete();
            return;
        }
        if (!temp.renameTo(file)) {
            file.delete();
            temp.renameTo(file);
        }
    }
}
