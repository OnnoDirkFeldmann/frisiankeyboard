/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frisian.keyboard;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;

import com.android.inputmethodcommon.InputMethodSettingsFragment;

/**
 * Displays the IME preferences inside the input method setting.
 */
public class ImePreferences extends PreferenceActivity {

    @Override
    public Intent getIntent() {
        final Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, IMSettings.class.getName());
        modIntent.putExtra(EXTRA_NO_HEADERS, true);
        return modIntent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected boolean isValidFragment(final String fragmentName) {
        return IMSettings.class.getName().equals(fragmentName);
    }

    public static class IMSettings extends InputMethodSettingsFragment {
        public String installlocale;
        public boolean installlocaleclicked;
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setInputMethodSettingsCategoryTitle(R.string.language_selection_title);
            setSubtypeEnablerTitle(R.string.select_language);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.ime_preferences);

            //BUTTONS
            final Context context = getActivity();
            Preference btn_enableapp = getPreferenceManager().findPreference("enableapp");
            if (btn_enableapp != null) {
                btn_enableapp.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference arg0) {
                        Intent enableIntent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
                        enableIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(enableIntent);
                        return true;
                    }
                });
            }
            Preference btn_standardapp = getPreferenceManager().findPreference("standardapp");
            if (btn_standardapp != null) {
                final InputMethodManager mgr = (InputMethodManager) context.getSystemService(context.INPUT_METHOD_SERVICE);
                btn_standardapp.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference arg0) {
                        if (mgr != null) {
                            mgr.showInputMethodPicker();
                        }
                        return true;
                    }
                });
            }
            Preference btn_updatedics = getPreferenceManager().findPreference("updatedics");
            if (btn_updatedics != null) {
                btn_updatedics.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference arg0) {
                        DatabaseHelper sqlcopy = new DatabaseHelper(getActivity());
                        sqlcopy.doUpgrade();
                        return true;
                    }
                });
            }
            Preference btn_wipedata = getPreferenceManager().findPreference("wipedata");
            if (btn_wipedata != null) {
                btn_wipedata.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference arg0) {
                        DatabaseHelper sqldel = new DatabaseHelper(getActivity());
                        sqldel.deleteDataBase();
                        return true;
                    }
                });
            }
        }
    }

}
