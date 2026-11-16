package com.custom.steamdeckphone;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private LinearLayout pinnedContainer;
    private GridLayout appsGrid;
    private TextView statusText;
    private SharedPreferences prefs;
    private int cardWidth;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());

    private final Runnable statusUpdater = new Runnable() {
        @Override
        public void run() {
            updateStatusPill();
            clockHandler.postDelayed(this, 15000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("phone_launcher_prefs", Context.MODE_PRIVATE);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenWidth = dm.widthPixels;
        cardWidth = (screenWidth - 140) / 4;

        ScrollView rootScroll = new ScrollView(this);
        rootScroll.setBackgroundColor(Color.parseColor("#0E141B"));

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(36, 65, 36, 60);

        // --- TOP STEAM DECK HEADER ---
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, 28);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_steam_deck);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(76, 76);
        logoParams.setMargins(0, 0, 18, 0);
        logo.setLayoutParams(logoParams);

        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);
        titleStack.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("STEAM DECK");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(null, Typeface.BOLD);

        statusText = new TextView(this);
        statusText.setTextColor(Color.parseColor("#66C0F4"));
        statusText.setTextSize(12);

        titleStack.addView(title);
        titleStack.addView(statusText);

        Button btnManage = createActionButton("+ Apps");
        btnManage.setOnClickListener(v -> showAppPickerDialog());

        Button btnSysSettings = createActionButton("⚙");
        btnSysSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_SETTINGS)));

        header.addView(logo);
        header.addView(titleStack);
        header.addView(btnManage);
        header.addView(btnSysSettings);
        mainLayout.addView(header);

        // --- PINNED DOCK ROW ---
        TextView pinnedHeader = createSectionTitle("PINNED DOCK (HOLD APP TO PIN)");
        mainLayout.addView(pinnedHeader);

        HorizontalScrollView pinnedScroll = new HorizontalScrollView(this);
        pinnedScroll.setHorizontalScrollBarEnabled(false);
        pinnedContainer = new LinearLayout(this);
        pinnedContainer.setOrientation(LinearLayout.HORIZONTAL);
        pinnedContainer.setPadding(0, 10, 0, 32);
        pinnedScroll.addView(pinnedContainer);
        mainLayout.addView(pinnedScroll);

        // --- ALL APPS GRID ---
        TextView appsHeader = createSectionTitle("LIBRARY");
        mainLayout.addView(appsHeader);

        appsGrid = new GridLayout(this);
        appsGrid.setColumnCount(4);
        appsGrid.setPadding(0, 10, 0, 20);
        mainLayout.addView(appsGrid);

        rootScroll.addView(mainLayout);
        setContentView(rootScroll);

        updateStatusPill();
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        clockHandler.post(statusUpdater);
        refreshAll();
    }

    @Override
    protected void onPause() {
        super.onPause();
        clockHandler.removeCallbacks(statusUpdater);
    }

    private void updateStatusPill() {
        String timeStr = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int pct = -1;
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) pct = (int) ((level / (float) scale) * 100);
        }
        statusText.setText(pct >= 0 ? timeStr + "  •  BAT " + pct + "%" : timeStr);
    }

    private void refreshAll() {
        loadPinnedApps();
        loadSelectedApps();
    }

    private void loadPinnedApps() {
        pinnedContainer.removeAllViews();
        PackageManager pm = getPackageManager();
        Set<String> pinned = prefs.getStringSet("pinned_apps", new HashSet<>());

        if (pinned.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText("Long-press any app below to pin it here");
            hint.setTextColor(Color.parseColor("#8F98A0"));
            hint.setTextSize(13);
            hint.setPadding(8, 24, 8, 24);
            pinnedContainer.addView(hint);
            return;
        }

        for (String pkgName : pinned) {
            try {
                CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkgName, 0));
                Drawable icon = pm.getApplicationIcon(pkgName);
                LinearLayout card = createAppCard(label.toString(), icon, cardWidth + 20, true);
                card.setOnClickListener(v -> {
                    Intent launch = pm.getLaunchIntentForPackage(pkgName);
                    if (launch != null) startActivity(launch);
                });
                card.setOnLongClickListener(v -> {
                    togglePinApp(pkgName, label.toString());
                    return true;
                });
                pinnedContainer.addView(card);
            } catch (PackageManager.NameNotFoundException ignored) {
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
        Collections.sort(pkgAppsList, new ResolveInfo.DisplayNameComparator(pm));

        for (ResolveInfo info : pkgAppsList) {
            String pkgName = info.activityInfo.packageName;
            if (pkgName.equals(getPackageName())) continue;
            if (allowedPackages != null && !allowedPackages.contains(pkgName)) continue;

            String appName = info.loadLabel(pm).toString();
            Drawable icon = info.loadIcon(pm);

            LinearLayout appCard = createAppCard(appName, icon, cardWidth, false);
            appCard.setOnClickListener(v -> {
                Intent launch = pm.getLaunchIntentForPackage(pkgName);
                if (launch != null) startActivity(launch);
            });
            appCard.setOnLongClickListener(v -> {
                showAppOptionsDialog(pkgName, appName);
                return true;
            });
            appsGrid.addView(appCard);
        }
    }

    private void showAppOptionsDialog(String pkgName, String appName) {
        Set<String> pinned = prefs.getStringSet("pinned_apps", new HashSet<>());
        boolean isPinned = pinned.contains(pkgName);
        String[] options = new String[]{
                isPinned ? "Unpin from Dock" : "Pin to Top Dock",
                "App Info"
        };

        new AlertDialog.Builder(this)
                .setTitle(appName)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        togglePinApp(pkgName, appName);
                    } else {
                        Intent infoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        infoIntent.setData(Uri.parse("package:" + pkgName));
                        startActivity(infoIntent);
                    }
                })
                .show();
    }

    private void togglePinApp(String pkgName, String appName) {
        Set<String> updated = new HashSet<>(prefs.getStringSet("pinned_apps", new HashSet<>()));
        if (updated.contains(pkgName)) {
            updated.remove(pkgName);
Toast.makeText(this, "Unpinned " + appName, Toast.LENGTH_SHORT).show();
} else {
updated.add(pkgName);
Toast.makeText(this, "Pinned " + appName + " to Dock", Toast.LENGTH_SHORT).show();
}
prefs.edit().putStringSet("pinned_apps", updated).apply();
loadPinnedApps();
}
private void showAppPickerDialog() {
PackageManager pm = getPackageManager();
Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
List allApps = pm.queryIntentActivities(mainIntent, 0);
Collections.sort(allApps, new ResolveInfo.DisplayNameComparator(pm));
List names = new ArrayList<>();
List pkgs = new ArrayList<>();
Set currentAllowed = prefs.getStringSet("allowed_apps", null);
for (ResolveInfo info : allApps) {
if (!info.activityInfo.packageName.equals(getPackageName())) {
names.add(info.loadLabel(pm).toString());
pkgs.add(info.activityInfo.packageName);
}
}
String[] nameArray = names.toArray(new String[names.size()]);
boolean[] checkedItems = new boolean[pkgs.size()];
for (int i = 0; i < pkgs.size(); i++) {
checkedItems[i] = (currentAllowed == null) || currentAllowed.contains(pkgs.get(i));
}
new AlertDialog.Builder(this)
.setTitle("Select Visible Apps")
.setMultiChoiceItems(nameArray, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked)
.setPositiveButton("Save", (dialog, which) -> {
Set newAllowed = new HashSet<>();
for (int i = 0; i < pkgs.size(); i++) {
if (checkedItems[i]) newAllowed.add(pkgs.get(i));
}
prefs.edit().putStringSet("allowed_apps", newAllowed).apply();
loadSelectedApps();
})
.setNegativeButton("Cancel", null)
.show();
}
private LinearLayout createAppCard(String labelText, Drawable iconDrawable, int width, boolean isPinned) {
LinearLayout card = new LinearLayout(this);
card.setOrientation(LinearLayout.VERTICAL);
card.setGravity(Gravity.CENTER);
LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, width + 36);
params.setMargins(8, 8, 8, 8);
card.setLayoutParams(params);
card.setPadding(12, 16, 12, 12);
GradientDrawable bg = new GradientDrawable();
bg.setColor(Color.parseColor(isPinned ? "#1B2838" : "#171D25"));
bg.setCornerRadius(24f);
bg.setStroke(isPinned ? 3 : 2, Color.parseColor(isPinned ? "#1A9FFF" : "#2A3F5A"));
card.setBackground(bg);
if (iconDrawable != null) {
ImageView iv = new ImageView(this);
iv.setImageDrawable(iconDrawable);
LinearLayout.LayoutParams ivParams = new LinearLayout.LayoutParams(96, 96);
ivParams.setMargins(0, 0, 0, 12);
iv.setLayoutParams(ivParams);
card.addView(iv);
}
TextView tv = new TextView(this);
tv.setText(labelText);
tv.setTextColor(Color.WHITE);
tv.setTextSize(11);
tv.setGravity(Gravity.CENTER);
tv.setMaxLines(1);
card.addView(tv);
return card;
}
private TextView createSectionTitle(String text) {
TextView tv = new TextView(this);
tv.setText(text);
tv.setTextColor(Color.parseColor("#66C0F4"));
tv.setTextSize(12);
tv.setTypeface(null, Typeface.BOLD);
return tv;
}
private Button createActionButton(String text) {
Button btn = new Button(this);
btn.setText(text);
btn.setTextColor(Color.WHITE);
btn.setTextSize(12);
btn.setBackgroundColor(Color.parseColor("#2A475E"));
LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
p.setMargins(12, 0, 0, 0);
btn.setLayoutParams(p);
btn.setPadding(20, 8, 20, 8);
return btn;
}
}
