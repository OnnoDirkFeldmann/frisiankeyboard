package com.frisian.keyboard;
import android.content.Context;

import java.io.File;
import java.util.ArrayList;

public class StorageFinder{

    private final Context mContext;

    public StorageFinder (Context context) {
        mContext = context;
        }
    public String [] getstorages (){
        ArrayList<String> storages = new ArrayList<String>();
        for (File file : mContext.getExternalFilesDirs(null)) {
            storages.add(file.getAbsolutePath());
        }
        String [] storagesarr = storages.toArray(new String[storages.size()]);
        return storagesarr;
    }
}
