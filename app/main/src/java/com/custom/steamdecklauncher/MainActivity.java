package com.custom.steamdecklauncher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private LinearLayout hdmiContainer;
    private GridLayout appsGrid;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);

        ScrollView rootScroll = new ScrollView(this);
        rootScroll.setBackgroundColor(Color.parseColor("#0E141B"));

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(60, 45, 60, 60);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, 40);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_steam_deck);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(90, 90);
        logoParams.setMargins(0, 0, 24, 0);
        logo.setLayoutParams(logoParams);

        TextView title = new TextView(this);
        title.setText("STEAM DECK // BRAVIA OS");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleParams);

        Button btnManageApps = createActionButton("+ Manage Apps");
        btnManageApps.setOnClickListener(v -> showAppPickerDialog());

        Button btnBootConfig = createActionButton("Startup Config");
        btnBootConfig.setOnClickListener(v -> showBootConfigDialog());

        header.addView(logo);
        header.addView(title);
        header.addView(btnManageApps);
        header.addView(btnBootConfig);
        mainLayout.addView(header);

        TextView hdmiHeader = createSectionTitle("HDMI INPUTS");
        mainLayout.addView(hdmiHeader);

        HorizontalScrollView hdmiScroll = new HorizontalScrollView(this);
        hdmiScroll.setHorizontalScrollBarEnabled(false);
        hdmiContainer = new LinearLayout(this);
        hdmiContainer.setOrientation(LinearLayout.HORIZONTAL);
        hdmiContainer.setPadding(0, 15, 0, 45);
        hdmiScroll.addView(hdmiContainer);
        mainLayout.addView(hdmiScroll);

        TextView appsHeader = createSectionTitle("INSTALLED APPS");
        mainLayout.addView(appsHeader);

        appsGrid = new GridLayout(this);
        appsGrid.setColumnCount(5);
        appsGrid.setPadding(0, 15, 0, 20);
        mainLayout.addView(appsGrid);

        rootScroll.addView(mainLayout);
        setContentView(rootScroll);

        loadHdmiInputs();
        loadSelectedApps();
    }

    private void loadHdmiInputs() {
        hdmiContainer.removeAllViews();
        TvInputManager tvManager = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        String deckInputId = prefs.getString("deck_hdmi_id", "");

        if (tvManager != null) {
            List<TvInputInfo> inputs = tvManager.getTvInputList();
            for (TvInputInfo info : inputs) {
                if (info.isPassthroughInput()) {
                    CharSequence label = info.loadLabel(this);
                    String name = label != null ? label.toString() : info.getId();
                    boolean isSteamDeckPort = info.getId().equals(deckInputId);

                    LinearLayout card = createCard(
                            isSteamDeckPort ? "STEAM DECK (" + name + ")" : name,
                            isSteamDeckPort ? getDrawable(R.drawable.ic_steam_deck) : null,
                            340,
                            180,
                            isSteamDeckPort
                    );

                    card.setOnClickListener(v -> {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.getId()));
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(this, "Cannot switch to " + name, Toast.LENGTH_SHORT).show();
                        }
                    });

                    card.setOnLongClickListener(v -> {
                        prefs.edit().putString("deck_hdmi_id", info.getId()).apply();
                        Toast.makeText(this, name + " set as Steam Deck HDMI!", Toast.LENGTH_SHORT).show();
                        loadHdmiInputs();
                        return true;
                    });

                    hdmiContainer.addView(card);
                }
            }
        }
    }

    private void loadSelectedApps() {
        appsGrid.removeAllViews();
        PackageManager pm = getPackageManager();
        Set<String> allowedPackages = prefs.getStringSet("allowed_apps", null);

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> pkgAppsList = pm.queryIntentActivities(mainIntent, 0);

        for (ResolveInfo info : pkgAppsList) {
            String pkgName = info.activityInfo.packageName;
            if (pkgName.equals(getPackageName())) continue;
            if (allowedPackages != null && !allowedPackages.contains(pkgName)) continue;

            String appName = info.loadLabel(pm).toString();
            Drawable icon = info.loadIcon(pm);

            LinearLayout appCard = createCard(appName, icon, 260, 180, false);
            appCard.setOnClickListener(v -> {
                Intent launch = pm.getLaunchIntentForPackage(pkgName);
                if (launch != null) startActivity(launch);
            });
            appsGrid.addView(appCard);
        }
    }

    private void showAppPickerDialog() {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> allApps = pm.queryIntentActivities(mainIntent, 0);

        List<String> names = new ArrayList<>();
        List<String> pkgs = new ArrayList<>();
        Set<String> currentAllowed = prefs.getStringSet("allowed_apps", null);

        for (ResolveInfo info : allApps) {
            if (!info.activityInfo.packageName.equals(getPackageName())) {
                names.add(info.loadLabel(pm).toString());
                pkgs.add(info.activityInfo.packageName);
            }
        }

        String[] nameArray = names.toArray(new String[0]);
        boolean[] checkedItems = new boolean[pkgs.size()];
        for (int i = 0; i < pkgs.size(); i++) {
            checkedItems[i] = (currentAllowed == null) || currentAllowed.contains(pkgs.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Apps to Show on Home")
                .setMultiChoiceItems(nameArray, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked)
                .setPositiveButton("Save", (dialog, which) -> {
                    Set<String> newAllowed = new HashSet<>();
                    for (int i = 0; i < pkgs.size(); i++) {
                        if (checkedItems[i]) newAllowed.add(pkgs.get(i));
                    }
                    prefs.edit().putStringSet("allowed_apps", newAllowed).apply();
                    loadSelectedApps();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showBootConfigDialog() {
        TvInputManager tvManager = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        List<String> labels = new ArrayList<>();
        List<String> ids = new ArrayList<>();

        labels.add("Custom Steam Deck Home (Default)");
        ids.add("HOME");

        if (tvManager != null) {
            for (TvInputInfo info : tvManager.getTvInputList()) {
                if (info.isPassthroughInput()) {
                    CharSequence l = info.loadLabel(this);
                    labels.add("Boot directly to: " + (l != null ? l.toString() : info.getId()));
                    ids.add(info.getId());
                }
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("What Should Open First on TV Boot?")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    prefs.edit().putString("boot_target", ids.get(which)).apply();
                    Toast.makeText(this, "Startup saved: " + labels.get(which), Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private LinearLayout createCard(String labelText, Drawable iconDrawable, int width, int height, boolean highlight) {
LinearLayout card = new LinearLayout(this);
card.setOrientation(LinearLayout.VERTICAL);
card.setGravity(Gravity.CENTER);
card.setFocusable(true);
card.setFocusableInTouchMode(true);
LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
params.setMargins(14, 14, 14, 14);
card.setLayoutParams(params);
card.setPadding(20, 20, 20, 20);
GradientDrawable normalBg = new GradientDrawable();
normalBg.setColor(Color.parseColor(highlight ? "#1B2838" : "#171D25"));
normalBg.setCornerRadius(16f);
normalBg.setStroke(highlight ? 4 : 2, Color.parseColor(highlight ? "#1A9FFF" : "#2A3F5A"));
GradientDrawable focusBg = new GradientDrawable();
focusBg.setColor(Color.parseColor("#233549"));
focusBg.setCornerRadius(16f);
focusBg.setStroke(6, Color.parseColor("#1A9FFF"));
card.setBackground(normalBg);
card.setOnFocusChangeListener((v, hasFocus) -> {
card.setBackground(hasFocus ? focusBg : normalBg);
card.setScaleX(hasFocus ? 1.06f : 1.0f);
card.setScaleY(hasFocus ? 1.06f : 1.0f);
});
if (iconDrawable != null) {
ImageView iv = new ImageView(this);
iv.setImageDrawable(iconDrawable);
LinearLayout.LayoutParams ivParams = new LinearLayout.LayoutParams(75, 75);
ivParams.setMargins(0, 0, 0, 14);
iv.setLayoutParams(ivParams);
card.addView(iv);
}
TextView tv = new TextView(this);
tv.setText(labelText);
tv.setTextColor(Color.WHITE);
tv.setTextSize(15);
tv.setGravity(Gravity.CENTER);
tv.setMaxLines(2);
card.addView(tv);
return card;
}
private TextView createSectionTitle(String text) {
TextView tv = new TextView(this);
tv.setText(text);
tv.setTextColor(Color.parseColor("#66C0F4"));
tv.setTextSize(16);
tv.setTypeface(null, Typeface.BOLD);
return tv;
}
private Button createActionButton(String text) {
Button btn = new Button(this);
btn.setText(text);
btn.setTextColor(Color.WHITE);
btn.setBackgroundColor(Color.parseColor("#2A475E"));
LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
p.setMargins(16, 0, 0, 0);
btn.setLayoutParams(p);
btn.setPadding(24, 12, 24, 12);
return btn;
}
}