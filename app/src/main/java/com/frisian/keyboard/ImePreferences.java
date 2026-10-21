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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.android.inputmethodcommon.InputMethodSettingsFragment;

/**
 * Displays the IME preferences inside the input method setting.
 *
 * <p>Besides the language selection this screen also guides the user through the
 * one-time setup:
 *
 * <ol>
 *   <li>enable the keyboard</li>
 *   <li>make it the default keyboard</li>
 *   <li>pick the languages of the keyboard</li>
 * </ol>
 *
 * <p>Only the first step needs a tap in the system settings, everything after that
 * is done by the app itself.
 */
public class ImePreferences extends PreferenceActivity {

    private static final String PREFS_NAME = "frisian_keyboard";
    private static final String PREF_SETUP_OFFERED = "setup_offered";

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
        private DatabaseHelper mDb;
        /** true while we wait for the user to come back from the system settings. */
        private boolean mWaitingForEnable;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setInputMethodSettingsCategoryTitle(R.string.language_selection_title);
            setSubtypeEnablerTitle(R.string.select_language);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.ime_preferences);

            final Context context = getActivity();
            if (context != null) {
                mDb = new DatabaseHelper(context);
                setupClickListeners(context);
                autoSync(context);
                offerSetup(context);
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            refreshStatus();
            if (mWaitingForEnable) {
                // The user just came back from the system settings.
                mWaitingForEnable = false;
                continueSetup();
            }
        }

        /**
         * Copies the bundled dictionary automatically when it is missing or outdated,
         * so the user never has to press "Update dictionary" after an install or update.
         */
        private void autoSync(final Context context) {
            if (mDb == null || !mDb.needsAutoSync()) {
                return;
            }
            mDb.syncDictionary(new DatabaseHelper.SyncCallback() {
                @Override
                public void onSyncDone(boolean success) {
                    toast(success ? getString(R.string.dict_updated) : getString(R.string.dict_error),
                            success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG);
                    refreshStatus();
                }
            });
        }

        /**
         * The very first time the app is opened the user is asked whether the
         * keyboard should be set up right away. After that the app takes over as
         * far as Android allows it.
         */
        private void offerSetup(final Context context) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (prefs.getBoolean(PREF_SETUP_OFFERED, false)) {
                return;
            }
            prefs.edit().putBoolean(PREF_SETUP_OFFERED, true).apply();
            if (ImeSetup.isEnabled(context)) {
                return;
            }
            if (ImeSetup.enableIme(context)) {
                // Development setup (the privileged permission is granted): the app
                // enabled itself, so no user interaction is needed at all.
                toast(getString(R.string.ime_enabled));
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        continueSetup();
                    }
                });
                return;
            }
            new AlertDialog.Builder(context)
                    .setTitle(R.string.setup_dialog_title)
                    .setMessage(R.string.setup_dialog_message)
                    .setPositiveButton(R.string.setup_dialog_start,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    mWaitingForEnable = true;
                                    ImeSetup.openInputMethodSettings(context);
                                }
                            })
                    .setNegativeButton(R.string.setup_dialog_later, null)
                    .show();
        }

        /**
         * Continues the setup after the keyboard has been enabled: sets it as the
         * default keyboard (automatically when that is permitted) and opens the
         * language picker of the keyboard.
         */
        private void continueSetup() {
            final Context context = getActivity();
            if (context == null) {
                return;
            }
            if (!ImeSetup.isEnabled(context)) {
                toast(getString(R.string.setup_enable_first));
                refreshStatus();
                return;
            }
            if (!ImeSetup.isDefault(context)) {
                if (ImeSetup.setDefaultIme(context)) {
                    toast(getString(R.string.ime_default_set));
                } else {
                    InputMethodManager mgr = (InputMethodManager)
                            context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (mgr != null) {
                        mgr.showInputMethodPicker();
                        return;
                    }
                }
            }
            // Last step: let the user pick the languages of the keyboard.
            ImeSetup.openSubtypeSettings(context);
        }

        private void setupClickListeners(final Context context) {
            Preference btn_enableapp = findPreference("enableapp");
            if (btn_enableapp != null) {
                btn_enableapp.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (ImeSetup.isEnabled(context)) {
                            toast(getString(R.string.setup_enable_done));
                        } else if (ImeSetup.enableIme(context)) {
                            toast(getString(R.string.ime_enabled));
                            continueSetup();
                        } else {
                            mWaitingForEnable = true;
                            ImeSetup.openInputMethodSettings(context);
                        }
                        refreshStatus();
                        return true;
                    }
                });
            }

            Preference btn_standardapp = findPreference("standardapp");
            if (btn_standardapp != null) {
                btn_standardapp.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (ImeSetup.isDefault(context)) {
                            toast(getString(R.string.setup_default_done));
                        } else if (!ImeSetup.isEnabled(context)) {
                            // The keyboard has to be enabled before it can be picked.
                            mWaitingForEnable = true;
                            ImeSetup.openInputMethodSettings(context);
                        } else if (ImeSetup.setDefaultIme(context)) {
                            toast(getString(R.string.ime_default_set));
                        } else {
                            InputMethodManager mgr = (InputMethodManager)
                                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (mgr != null) {
                                mgr.showInputMethodPicker();
                            }
                        }
                        refreshStatus();
                        return true;
                    }
                });
            }

            Preference btn_languages = findPreference("languagepicker");
            if (btn_languages != null) {
                btn_languages.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (ImeSetup.isEnabled(context)) {
                            ImeSetup.openSubtypeSettings(context);
                        } else {
                            toast(getString(R.string.setup_enable_first));
                        }
                        refreshStatus();
                        return true;
                    }
                });
            }

            Preference btn_updatedics = findPreference("updatedics");
            if (btn_updatedics != null) {
                btn_updatedics.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (mDb != null) {
                            mDb.syncDictionary(new DatabaseHelper.SyncCallback() {
                                @Override
                                public void onSyncDone(boolean success) {
                                    toast(success ? getString(R.string.dict_updated)
                                            : getString(R.string.dict_error),
                                            success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG);
                                    refreshStatus();
                                }
                            });
                        }
                        return true;
                    }
                });
            }

            Preference btn_wipedata = findPreference("wipedata");
            if (btn_wipedata != null) {
                btn_wipedata.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (mDb != null) {
                            mDb.deleteDataBase();
                        }
                        toast(getString(R.string.dict_deleted));
                        refreshStatus();
                        return true;
                    }
                });
            }
        }

        /** Updates the summaries so the user can see the current setup state. */
        private void refreshStatus() {
            final Context context = getActivity();
            if (context == null || mDb == null) {
                return;
            }

            Preference enable = findPreference("enableapp");
            if (enable != null) {
                if (ImeSetup.isEnabled(context)) {
                    enable.setSummary(R.string.setup_enable_done);
                } else if (ImeSetup.canEnableAutomatically(context)) {
                    enable.setSummary(R.string.setup_enable_auto);
                } else {
                    enable.setSummary(R.string.setup_enable_manual);
                }
            }

            Preference standard = findPreference("standardapp");
            if (standard != null) {
                if (ImeSetup.isDefault(context)) {
                    standard.setSummary(R.string.setup_default_done);
                } else if (ImeSetup.hasSecureSettingsPermission(context)) {
                    standard.setSummary(R.string.setup_default_auto);
                } else {
                    standard.setSummary(R.string.setup_default_manual);
                }
            }

            Preference update = findPreference("updatedics");
            if (update != null) {
                if (!mDb.isDatabasePresent()) {
                    update.setSummary(R.string.setup_dict_missing);
                } else if (mDb.isUpToDate()) {
                    update.setSummary(R.string.setup_dict_current);
                } else {
                    update.setSummary(R.string.setup_dict_outdated);
                }
            }

            Preference languages = findPreference("languagepicker");
            if (languages != null) {
                if (!ImeSetup.isEnabled(context)) {
                    languages.setSummary(R.string.setup_enable_first);
                } else {
                    languages.setSummary(R.string.setup_languages_manual);
                }
            }
        }

        private void toast(String message) {
            toast(message, Toast.LENGTH_SHORT);
        }

        private void toast(String message, int duration) {
            Context context = getActivity();
            if (context != null) {
                Toast.makeText(context, message, duration).show();
            }
        }
    }

}
