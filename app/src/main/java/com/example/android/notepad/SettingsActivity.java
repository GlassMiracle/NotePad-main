package com.example.android.notepad;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;
import androidx.appcompat.app.AppCompatDelegate;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        MaterialToolbar toolbar = findViewById(R.id.top_app_bar_settings);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
            toolbar.setNavigationOnClickListener(v -> finish());
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            PreferenceManager pm = getPreferenceManager();
            pm.setSharedPreferencesName("settings");

            // Theme mode
            ListPreference theme = new ListPreference(requireContext());
            theme.setKey("theme_mode");
            theme.setTitle(getString(R.string.pref_title_theme));
            theme.setEntries(new CharSequence[]{
                    getString(R.string.pref_theme_system),
                    getString(R.string.pref_theme_light),
                    getString(R.string.pref_theme_dark)
            });
            theme.setEntryValues(new CharSequence[]{"system", "light", "dark"});
            theme.setDefaultValue("system");
            theme.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

            // Relative time
            SwitchPreferenceCompat relative = new SwitchPreferenceCompat(requireContext());
            relative.setKey("pref_relative_time");
            relative.setTitle(getString(R.string.pref_title_relative_time));
            relative.setDefaultValue(true);

            // Show preview
            SwitchPreferenceCompat preview = new SwitchPreferenceCompat(requireContext());
            preview.setKey("pref_show_preview");
            preview.setTitle(getString(R.string.pref_title_show_preview));
            preview.setDefaultValue(true);

            // Default color
            ListPreference color = new ListPreference(requireContext());
            color.setKey("pref_default_color");
            color.setTitle(getString(R.string.pref_title_default_color));
            color.setEntries(new CharSequence[]{
                    getString(R.string.pref_color_default),
                    getString(R.string.pref_color_yellow),
                    getString(R.string.pref_color_green),
                    getString(R.string.pref_color_blue),
                    getString(R.string.pref_color_red)
            });
            color.setEntryValues(new CharSequence[]{"0", "1", "2", "3", "4"});
            color.setDefaultValue("0");
            color.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

            // Build the screen
            androidx.preference.PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
            screen.addPreference(theme);
            screen.addPreference(relative);
            screen.addPreference(preview);
            screen.addPreference(color);
            setPreferenceScreen(screen);

            // 主题变更立即生效（全局夜间模式）
            theme.setOnPreferenceChangeListener((Preference p, Object newValue) -> {
                int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                String v = String.valueOf(newValue);
                if ("light".equals(v)) mode = AppCompatDelegate.MODE_NIGHT_NO;
                else if ("dark".equals(v)) mode = AppCompatDelegate.MODE_NIGHT_YES;
                AppCompatDelegate.setDefaultNightMode(mode);
                return true;
            });
        }
    }
}
