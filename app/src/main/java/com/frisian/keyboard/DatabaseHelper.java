package com.frisian.keyboard;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class DatabaseHelper extends SQLiteOpenHelper {

    String DB_PATH = null;
    private static String DB_NAME = "dictionary.db";
    private SQLiteDatabase myDataBase;
    private final Context myContext;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, 10);
        this.myContext = context;
        this.DB_PATH = "/data/data/" + context.getPackageName() + "/" + "databases/";
        Log.e("Path 1", DB_PATH);
    }


    public void createDataBase() throws IOException {
        boolean dbExist = checkDataBase();
        if (dbExist) {
        } else {
            this.getReadableDatabase();
            Toast.makeText(myContext, "Now please update your dictionary in order to use the app!", Toast.LENGTH_LONG).show();
            Toast.makeText(myContext, "Now please update your dictionary in order to use the app!", Toast.LENGTH_LONG).show();
        }
    }

    public void deleteDataBase (){
        File fdelete = new File(DB_PATH + DB_NAME);
        if (fdelete.exists()) {
            if (fdelete.delete()) {
                Toast.makeText(myContext, "Deleted "+ DB_PATH + DB_NAME , Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(myContext, "Can't delete "+ DB_PATH + DB_NAME , Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean checkDataBase() {
        SQLiteDatabase checkDB = null;
        try {
            String myPath = DB_PATH + DB_NAME;
            checkDB = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READWRITE);
        } catch (SQLiteException e) {
        }
        if (checkDB != null) {
            checkDB.close();
        }
        return checkDB != null ? true : false;
    }

    private void copyDataBase() throws IOException {
        final ProgressDialog pd = ProgressDialog.show(myContext, "Updating dictionary","Please wait.");
        new Thread()
        {
            public void run()
            {
                try
                {
                    InputStream myInput = myContext.getAssets().open(DB_NAME);
                    String outFileName = DB_PATH + DB_NAME;
                    OutputStream myOutput = new FileOutputStream(outFileName);
                    byte[] buffer = new byte[10];
                    int length;
                    while ((length = myInput.read(buffer)) > 0) {
                        myOutput.write(buffer, 0, length);
                    }
                    myOutput.flush();
                    myOutput.close();
                    myInput.close();
                }
                catch (Exception e)
                {
                    Toast.makeText(myContext,"Error updating database!", Toast.LENGTH_LONG);
                }
                finally {
                    pd.cancel();
                    triggerRebirth(myContext);
                }
            }
        }.start();
    }

    public void openDataBase() throws SQLException {
        String myPath = DB_PATH + DB_NAME;
        myDataBase = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READWRITE);

    }

    @Override
    public synchronized void close() {
        if (myDataBase != null)
            myDataBase.close();
        super.close();
    }


    @Override
    public void onCreate(SQLiteDatabase db) {
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (newVersion > oldVersion)
            try {
                copyDataBase();
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    public void doUpgrade() {
            try {
                copyDataBase();
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    public Cursor rawquery(String sql, String[] args) {
        return myDataBase.rawQuery(sql,args);
    }
    public Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy) {
        return myDataBase.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
    }
    public void execSQL (String query) {
        myDataBase.execSQL(query);
    }

    public void update (String table, ContentValues values, String where, String whereargs[]) {
        myDataBase.update(table, values, where, whereargs);
    }

    public void runpragma () {
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
    public static void triggerRebirth(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(context.getPackageName());
        ComponentName componentName = intent.getComponent();
        Intent mainIntent = Intent.makeRestartActivityTask(componentName);
        context.startActivity(mainIntent);
        Runtime.getRuntime().exit(0);
    }
}