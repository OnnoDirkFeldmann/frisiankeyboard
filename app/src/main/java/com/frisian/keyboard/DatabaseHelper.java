package com.frisian.keyboard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "FrisianKeyboard";
    private static final String DB_NAME = "dictionary.db";
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    private static final String PREFS_NAME = "frisian_keyboard";
    private static final String PREF_LAST_AUTO_SYNC = "last_auto_sync_version";
    private static final String PREF_DICT_DELETED = "dictionary_deleted";

    String DB_PATH = null;
    private SQLiteDatabase myDataBase;
    private final Context myContext;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, 10);
        this.myContext = context;
        this.DB_PATH = context.getDatabasePath(DB_NAME).getParent() + "/";
        Log.d(TAG, "Database path: " + DB_PATH);
    }

    /**
     * @return true if the dictionary database file exists on disk. This only looks at
     *         the file system on purpose: opening a second connection to the
     *         dictionary is not possible while the keyboard holds it open with an
     *         exclusive lock.
     */
    public boolean isDatabasePresent() {
        return new File(DB_PATH + DB_NAME).exists();
    }

    /** @return true if the helper currently holds an open database connection. */
    public boolean isOpen() {
        return myDataBase != null && myDataBase.isOpen();
    }

    /**
     * Reads the version that is stored inside the dictionary (the {@code version} table).
     * Uses the already open connection when there is one, so that no second
     * connection to the dictionary is needed.
     *
     * @return the stored version, or {@code null} when it cannot be read.
     */
    public String getStoredVersion() {
        if (isOpen()) {
            return queryVersion(myDataBase);
        }
        if (!isDatabasePresent()) {
            return null;
        }
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(DB_PATH + DB_NAME, null, SQLiteDatabase.OPEN_READWRITE);
            return queryVersion(db);
        } catch (SQLiteException e) {
            Log.w(TAG, "Unable to read dictionary version", e);
            return null;
        } finally {
            if (db != null) {
                db.close();
            }
        }
    }

    private static String queryVersion(SQLiteDatabase db) {
        Cursor cur = null;
        try {
            cur = db.rawQuery("SELECT version FROM version LIMIT 1", null);
            if (cur.moveToFirst()) {
                return cur.getString(0);
            }
        } catch (SQLiteException e) {
            Log.w(TAG, "Unable to read dictionary version", e);
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        return null;
    }

    /**
     * @return true when the installed dictionary matches the version of the app.
     *         A mismatch means the bundled dictionary has to be copied again.
     */
    public boolean isUpToDate() {
        if (!isDatabasePresent()) {
            return false;
        }
        String stored = getStoredVersion();
        if (stored == null) {
            // The version could not be read (for example because the running
            // keyboard holds the database with an exclusive lock). Do not treat that
            // as "outdated", otherwise the dictionary would be replaced over and
            // over again.
            return true;
        }
        return getAppVersion().equals(stored);
    }

    /** The version of the app, used as the version number of the bundled dictionary. */
    public String getAppVersion() {
        return myContext.getResources().getString(R.string.version);
    }

    /**
     * Decides whether the dictionary has to be copied again.
     *
     * <p>In addition to the version check this remembers the app version of the last
     * automatic sync. That way a mismatch between the app version and the version
     * inside the bundled dictionary can never lead to an endless copy loop (which
     * would replace the 40+ MB dictionary on every keyboard start).
     */
    public boolean needsAutoSync() {
        if (isDictionaryDeleted()) {
            // The user removed the dictionary on purpose, do not put it back.
            return false;
        }
        if (!isDatabasePresent()) {
            return true;
        }
        if (isUpToDate()) {
            return false;
        }
        return !getAppVersion().equals(getLastAutoSyncVersion());
    }

    private boolean isDictionaryDeleted() {
        return myContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_DICT_DELETED, false);
    }

    private void setDictionaryDeleted(boolean deleted) {
        myContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_DICT_DELETED, deleted)
                .apply();
    }

    private String getLastAutoSyncVersion() {
        return myContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_LAST_AUTO_SYNC, "");
    }

    private void setLastAutoSyncVersion(String version) {
        myContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_LAST_AUTO_SYNC, version)
                .apply();
    }

    /**
     * Kept for backwards compatibility. The dictionary is installed automatically by
     * {@link #syncDictionary(SyncCallback)} instead of relying on a manual tap.
     */
    public void createDataBase() {
        // no-op
    }

    public void deleteDataBase() {
        close();
        setDictionaryDeleted(true);
        File fdelete = new File(DB_PATH + DB_NAME);
        if (fdelete.exists() && fdelete.delete()) {
            Log.d(TAG, "Deleted " + DB_PATH + DB_NAME);
        }
    }

    /** Callback used to report the result of an asynchronous dictionary sync. */
    public interface SyncCallback {
        void onSyncDone(boolean success);
    }

    /**
     * Copies the bundled {@code dictionary.db} from the APK assets into the app's
     * private database directory.
     *
     * <p>The copy itself runs on a background thread, but the final swap of the file
     * is done on the main thread: the live database has to be closed <em>before</em>
     * the file is replaced. Renaming or deleting the file while a connection is open
     * makes SQLite mark that connection as read only
     * ({@code SQLITE_READONLY_DBMOVED}), which breaks every later write.
     */
    public void syncDictionary(final SyncCallback callback) {
        new Thread() {
            @Override
            public void run() {
                final boolean copied = copyAssetToTempFile();
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        final boolean success = copied && installTempFile();
                        if (callback != null) {
                            callback.onSyncDone(success);
                        }
                    }
                });
            }
        }.start();
    }

    /** Legacy entry point that is also used by {@link #onUpgrade}. */
    public void doUpgrade() {
        syncDictionary(null);
    }

    /** Background step: stream the bundled dictionary into a temporary file. */
    private boolean copyAssetToTempFile() {
        InputStream myInput = null;
        OutputStream myOutput = null;
        try {
            File dir = new File(DB_PATH);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("Unable to create databases directory");
            }
            myInput = myContext.getAssets().open(DB_NAME);
            myOutput = new FileOutputStream(tempFile());
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int length;
            while ((length = myInput.read(buffer)) > 0) {
                myOutput.write(buffer, 0, length);
            }
            myOutput.flush();
            myOutput.close();
            myInput.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error copying the dictionary", e);
            return false;
        } finally {
            closeQuietly(myInput);
            closeQuietly(myOutput);
        }
    }

    /**
     * Main thread step: close the live database and move the freshly copied file
     * into place. Afterwards the caller reopens the database.
     */
    private boolean installTempFile() {
        File tempFile = tempFile();
        File target = new File(DB_PATH + DB_NAME);
        if (!tempFile.exists()) {
            return false;
        }
        // Close first: an open connection must never see the file disappear.
        close();
        try {
            if (target.exists() && !target.delete()) {
                throw new IOException("Unable to remove the old dictionary");
            }
            if (!tempFile.renameTo(target)) {
                throw new IOException("Unable to install the new dictionary");
            }
            // Remember that we installed a dictionary for this app version, so a
            // version mismatch can never trigger an endless copy loop.
            setLastAutoSyncVersion(getAppVersion());
            setDictionaryDeleted(false);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error updating database", e);
            tempFile.delete();
            return false;
        }
    }

    private File tempFile() {
        return new File(DB_PATH + DB_NAME + ".tmp");
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }

    public synchronized void openDataBase() throws SQLException {
        String myPath = DB_PATH + DB_NAME;
        myDataBase = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READWRITE);
    }

    @Override
    public synchronized void close() {
        if (myDataBase != null) {
            myDataBase.close();
            myDataBase = null;
        }
        super.close();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (newVersion > oldVersion) {
            syncDictionary(null);
        }
    }

    public Cursor rawquery(String sql, String[] args) {
        if (!isOpen()) {
            return null;
        }
        return myDataBase.rawQuery(sql, args);
    }

    public Cursor query(String table, String[] columns, String selection, String[] selectionArgs,
                        String groupBy, String having, String orderBy) {
        if (!isOpen()) {
            return null;
        }
        return myDataBase.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
    }

    public void execSQL(String query) {
        if (!isOpen()) {
            return;
        }
        myDataBase.execSQL(query);
    }

    /** Runs a statement with bind arguments, e.g. an UPDATE or an INSERT. */
    public void execStatement(String sql, String[] args) {
        if (!isOpen()) {
            return;
        }
        myDataBase.execSQL(sql, args);
    }

    public void update(String table, ContentValues values, String where, String[] whereargs) {
        if (!isOpen()) {
            return;
        }
        myDataBase.update(table, values, where, whereargs);
    }

    public void runpragma() {
        if (!isOpen()) {
            return;
        }
        Cursor cur = myDataBase.rawQuery("PRAGMA cache_size = 48829;", null);
        cur.moveToFirst();
        cur = myDataBase.rawQuery("PRAGMA threads = 4;", null);
        cur.moveToFirst();
        cur = myDataBase.rawQuery("PRAGMA locking_mode = exclusive;", null);
        cur.moveToFirst();
        cur = myDataBase.rawQuery("PRAGMA page_size =65535;", null);
        cur.moveToFirst();
        cur = myDataBase.rawQuery("PRAGMA auto_vacuum = FULL;", null);
        cur.moveToFirst();
    }
}
