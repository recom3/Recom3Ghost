package com.recom3.ghost;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

final class ReferenceStore {
    private static final int FORMAT_VERSION = 1;
    private final File referenceFile;
    private final File backupFile;

    ReferenceStore(File directory) {
        referenceFile = new File(directory, "reference.dat");
        backupFile = new File(directory, "reference.bak");
    }

    Track load() throws IOException {
        File saved = referenceFile.exists() ? referenceFile : backupFile;
        if (!saved.exists()) return null;
        DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(saved)));
        try {
            if (input.readInt() != FORMAT_VERSION) throw new IOException("Unsupported track format");
            int count = input.readInt();
            Track track = new Track();
            for (int i = 0; i < count; i++) {
                Track.Point point = new Track.Point(input.readDouble(), input.readDouble(), input.readLong());
                if (!track.add(point)) throw new IOException("Invalid track point");
            }
            if (!track.usableAsReference()) throw new IOException("Invalid reference track");
            return track;
        } finally {
            input.close();
        }
    }

    void save(Track track) throws IOException {
        File pending = File.createTempFile("reference-", ".tmp", referenceFile.getParentFile());
        try {
            FileOutputStream output = new FileOutputStream(pending);
            try {
                DataOutputStream data = new DataOutputStream(output);
                data.writeInt(FORMAT_VERSION);
                data.writeInt(track.size());
                for (int i = 0; i < track.size(); i++) {
                    Track.Point point = track.point(i);
                    data.writeDouble(point.latitude);
                    data.writeDouble(point.longitude);
                    data.writeLong(point.timeMs);
                }
                data.flush();
                output.getFD().sync();
            } finally {
                output.close();
            }
            // A previous reference remains readable if the app stops between the two renames.
            if (referenceFile.exists()) {
                if (backupFile.exists() && !backupFile.delete()) throw new IOException("Could not clear backup");
                if (!referenceFile.renameTo(backupFile)) throw new IOException("Could not back up reference");
            }
            if (!pending.renameTo(referenceFile)) throw new IOException("Could not replace reference");
            backupFile.delete();
        } finally {
            pending.delete();
        }
    }
}
