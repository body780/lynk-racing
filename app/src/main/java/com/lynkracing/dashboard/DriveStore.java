package com.lynkracing.dashboard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

final class DriveStore extends SQLiteOpenHelper {
    record Entry(long id, long started, long duration, double distance,
                 float topSpeed, float peakG, long sprint, boolean sprintOnly) {}

    DriveStore(Context context) {
        super(context, "drives.db", null, 1);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE drives (id INTEGER PRIMARY KEY, started INTEGER NOT NULL,"
                + " duration INTEGER NOT NULL, distance REAL NOT NULL, top_speed REAL NOT NULL,"
                + " peak_g REAL NOT NULL, sprint INTEGER NOT NULL, sprint_only INTEGER NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported database version: " + newVersion);
    }

    long add(Entry entry) {
        return getWritableDatabase().insertOrThrow("drives", null, values(entry));
    }

    void update(Entry entry) {
        getWritableDatabase().update("drives", values(entry), "id = ?",
                new String[]{Long.toString(entry.id())});
    }

    private ContentValues values(Entry entry) {
        ContentValues values = new ContentValues();
        values.put("started", entry.started());
        values.put("duration", entry.duration());
        values.put("distance", entry.distance());
        values.put("top_speed", entry.topSpeed());
        values.put("peak_g", entry.peakG());
        values.put("sprint", entry.sprint());
        values.put("sprint_only", entry.sprintOnly() ? 1 : 0);
        return values;
    }

    List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        String[] columns = {"id", "started", "duration", "distance", "top_speed", "peak_g", "sprint", "sprint_only"};
        try (Cursor cursor = getReadableDatabase().query(
                "drives", columns, null, null, null, null, "started DESC, id DESC")) {
            while (cursor.moveToNext()) {
                entries.add(new Entry(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2),
                        cursor.getDouble(3), cursor.getFloat(4), cursor.getFloat(5),
                        cursor.getLong(6), cursor.getInt(7) == 1));
            }
        }
        return entries;
    }
}
