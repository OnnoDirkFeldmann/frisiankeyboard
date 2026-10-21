package com.frisian.keyboard;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.util.List;

/**
 * Small helper that takes care of the two setup steps a user normally has to
 * perform by hand in the system settings:
 *
 * <ol>
 *   <li>enabling this input method</li>
 *   <li>selecting it as the default keyboard</li>
 * </ol>
 *
 * <p>Since Android 11 a regular app is no longer allowed to enable an IME or
 * make itself the default keyboard. That requires the {@code WRITE_SECURE_SETTINGS}
 * permission, which is a system/development permission and is therefore only
 * granted if the user (or a developer) explicitly runs:
 *
 * <pre>adb shell pm grant com.frisian.keyboard android.permission.WRITE_SECURE_SETTINGS</pre>
 *
 * <p>Even with that permission there is a catch: since Android 14
 * ({@code ENABLED_INPUT_METHODS} is annotated with
 * {@code @Readable(maxTargetSdk = TIRAMISU)}) the list of enabled input methods
 * can no longer be read by apps that target API 34 or higher. Without reading
 * that list the enabled keyboards cannot be extended without destroying the
 * keyboard configuration of the user, so the app does not even try it on
 * Android 14 and newer. Selecting the default keyboard still works there,
 * because {@code DEFAULT_INPUT_METHOD} stays readable.
 *
 * <p>The language that is preselected is {@link #DEFAULT_LOCALE}. The keyboard
 * declares itself as a non-system keyboard ({@code android:isDefault="false"}),
 * so Android never swaps it for the keyboard of the system language - the
 * "use system languages" option does not apply to it.
 */
public final class ImeSetup {

    public static final String SECURE_SETTINGS_PERMISSION = "android.permission.WRITE_SECURE_SETTINGS";

    /** The language the keyboard starts with. */
    public static final String DEFAULT_LOCALE = "en_GB";

    private static final String PREFS_NAME = "frisian_keyboard";
    private static final String PREF_LANGUAGE_PRESELECTED = "language_preselected";

    /** API level from which on ENABLED_INPUT_METHODS is not readable anymore. */
    private static final int API_LEVEL_RESTRICTED_ENABLED_LIST = 34;

    private ImeSetup() {
    }

    /** Component name of the input method service, e.g. {@code com.frisian.keyboard/.SoftKeyboard}. */
    public static ComponentName getServiceComponentName(Context context) {
        return new ComponentName(context.getPackageName(), SoftKeyboard.class.getName());
    }

    /** The string the system uses to identify this IME in the secure settings. */
    public static String getImeId(Context context) {
        // InputMethodInfo#getId() is documented to be ComponentName#flattenToShortString().
        return getServiceComponentName(context).flattenToShortString();
    }

    /** @return true if this IME is already enabled in the system settings. */
    public static boolean isEnabled(Context context) {
        InputMethodManager imm =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) {
            return false;
        }
        ComponentName mine = getServiceComponentName(context);
        for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
            if (mine.equals(info.getComponent())) {
                return true;
            }
        }
        return false;
    }

    /** @return true if this IME is the currently selected default keyboard. */
    public static boolean isDefault(Context context) {
        String current = null;
        try {
            current = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        } catch (SecurityException e) {
            return false;
        }
        if (current == null) {
            return false;
        }
        ComponentName cn = ComponentName.unflattenFromString(current);
        return getServiceComponentName(context).equals(cn);
    }

    /**
     * @return true if the privileged {@code WRITE_SECURE_SETTINGS} permission was
     *         granted to this app (normally only possible via adb).
     */
    public static boolean hasSecureSettingsPermission(Context context) {
        return context.checkSelfPermission(SECURE_SETTINGS_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * @return true if the app is able to enable the keyboard on its own. This is only
     *         possible with the privileged permission and only on Android versions
     *         where the list of enabled keyboards can still be read.
     */
    public static boolean canEnableAutomatically(Context context) {
        return hasSecureSettingsPermission(context)
                && Build.VERSION.SDK_INT < API_LEVEL_RESTRICTED_ENABLED_LIST;
    }

    /**
     * Tries to enable this IME without any user interaction.
     *
     * @return true if the IME is enabled afterwards.
     */
    public static boolean enableIme(Context context) {
        if (isEnabled(context)) {
            return true;
        }
        if (!canEnableAutomatically(context)) {
            return false;
        }
        try {
            String id = getImeId(context);
            String current = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS);
            if (current == null) {
                // The list could not be read. Appending blindly would drop every
                // other keyboard of the user, so we do not touch it.
                return false;
            }
            if (!current.contains(id)) {
                current = current.isEmpty() ? id : current + ":" + id;
            }
            Settings.Secure.putString(
                    context.getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS, current);
            return isEnabled(context);
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * Tries to make this IME the default keyboard without any user interaction.
     * The keyboard has to be enabled already, otherwise the system ignores the
     * request.
     *
     * @return true if this IME is the default keyboard afterwards.
     */
    public static boolean setDefaultIme(Context context) {
        if (isDefault(context)) {
            return true;
        }
        if (!hasSecureSettingsPermission(context) || !isEnabled(context)) {
            return false;
        }
        try {
            Settings.Secure.putString(
                    context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD, getImeId(context));
            if (!isDefault(context)) {
                return false;
            }
            // The default keyboard starts with the preselected language.
            preselectDefaultLanguage(context);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    /** @return the InputMethodInfo of this app, or null if it is not installed. */
    public static InputMethodInfo findOwnInputMethod(Context context) {
        InputMethodManager imm =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) {
            return null;
        }
        ComponentName mine = getServiceComponentName(context);
        List<InputMethodInfo> list = imm.getInputMethodList();
        if (list == null) {
            return null;
        }
        for (InputMethodInfo info : list) {
            if (mine.equals(info.getComponent())) {
                return info;
            }
        }
        return null;
    }

    /**
     * @return the subtype of this keyboard for the given locale, or null if the
     *         keyboard does not offer that language.
     */
    public static InputMethodSubtype findSubtype(Context context, String locale) {
        InputMethodInfo imi = findOwnInputMethod(context);
        if (imi == null) {
            return null;
        }
        for (int i = 0; i < imi.getSubtypeCount(); i++) {
            InputMethodSubtype subtype = imi.getSubtypeAt(i);
            if (subtype == null) {
                continue;
            }
            if (locale.equals(subtype.getLocale()) || locale.equals(subtype.getLanguageTag())) {
                return subtype;
            }
        }
        return null;
    }

    /**
     * @return true if the language of the keyboard was already preselected. The
     *         keyboard only does that once, so a language the user picked
     *         afterwards is never overwritten.
     */
    public static boolean isLanguagePreselected(Context context) {
        return getPrefs(context).getBoolean(PREF_LANGUAGE_PRESELECTED, false);
    }

    static void markLanguagePreselected(Context context) {
        getPrefs(context).edit().putBoolean(PREF_LANGUAGE_PRESELECTED, true).apply();
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Preselects the default language of the keyboard. This is the privileged
     * variant that is used by the settings screen; the keyboard itself uses
     * {@code InputMethodService#switchInputMethod} which needs no permission.
     *
     * @return true if the language was preselected.
     */
    public static boolean preselectDefaultLanguage(Context context) {
        if (isLanguagePreselected(context)) {
            return false;
        }
        if (!hasSecureSettingsPermission(context)) {
            return false;
        }
        InputMethodSubtype subtype = findSubtype(context, DEFAULT_LOCALE);
        if (subtype == null) {
            return false;
        }
        try {
            // This is exactly what InputMethodManager#setCurrentInputMethodSubtype
            // does internally: the setting holds the hash code of the subtype.
            Settings.Secure.putInt(context.getContentResolver(),
                    Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE, subtype.hashCode());
            markLanguagePreselected(context);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    /** Opens the system "Manage keyboards" screen where the IME can be enabled. */
    public static void openInputMethodSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Opens the system screen where the languages of this keyboard can be picked.
     * This is the "language picker" of the input method.
     */
    public static void openSubtypeSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS);
        intent.putExtra(Settings.EXTRA_INPUT_METHOD_ID, getImeId(context));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /** All keyboards that are currently enabled, as they are stored in the system. */
    public static List<InputMethodInfo> getEnabledInputMethods(Context context) {
        InputMethodManager imm =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) {
            return null;
        }
        return imm.getEnabledInputMethodList();
    }
}
