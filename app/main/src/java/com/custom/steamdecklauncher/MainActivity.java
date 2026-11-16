package com.custom.steamdeckphone;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.Formatter;
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

    static final String PREFS_NAME = "phone_launcher_prefs";
    private static final String KEY_PINNED_APPS = "pinned_apps";
    private static final String KEY_ALLOWED_APPS = "allowed_apps";
    private static final String KEY_WIDGET_IDS = "hosted_widget_ids";
    static final String KEY_TERMUX_TOOLS_OUTPUT = "termux_tools_output";
    static final String KEY_TERMUX_TOOLS_ERROR = "termux_tools_error";
    static final String ACTION_TERMUX_RESULT = "com.custom.steamdeckphone.TERMUX_RESULT";
    static final String TERMUX_RESULT_BUNDLE = "result";
    static final String TERMUX_RESULT_STDOUT = "stdout";
    static final String TERMUX_RESULT_STDERR = "stderr";
    static final String TERMUX_RESULT_EXIT_CODE = "exitCode";
    static final String TERMUX_RESULT_ERROR = "err";
    static final String TERMUX_RESULT_ERROR_MESSAGE = "errmsg";
    private static final String TERMUX_COMMAND = "com.termux.RUN_COMMAND";
    private static final int APP_WIDGET_HOST_ID = 1729;
    private static final int REQUEST_PICK_WIDGET = 1001;
    private static final int REQUEST_CONFIGURE_WIDGET = 1002;
    private static final int MIN_GRID_COLUMN_COUNT = 2;
    private static final int MAX_GRID_COLUMN_COUNT = 10;
    private static final int MIN_GRID_CELL_WIDTH_DP = 170;
    private static final long STATUS_UPDATE_INTERVAL_MS = 15000L;
    private static final int COLOR_BACKGROUND = Color.rgb(7, 7, 8);
    private static final int COLOR_SURFACE = Color.rgb(18, 18, 20);
    private static final int COLOR_SURFACE_RAISED = Color.rgb(29, 29, 32);
    private static final int COLOR_ACCENT = Color.rgb(255, 59, 48);
    private static final int COLOR_TEXT_SECONDARY = Color.rgb(164, 164, 170);

    private LinearLayout pinnedContainer;
    private LinearLayout widgetContainer;
    private LinearLayout diagnosticsContainer;
    private GridLayout appsGrid;
    private TextView statusText;
    private TextView deviceInfoText;
    private TextView batteryStorageText;
    private TextView networkStatusText;
    private TextView termuxToolsText;
    private SharedPreferences prefs;
    private AppWidgetManager appWidgetManager;
    private AppWidgetHost appWidgetHost;
    private int cardWidth;
    private int gridColumnCount;
    private boolean mobileLayout;
    private boolean compactLayout;
    private boolean termuxReceiverRegistered;
    private int pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver termuxResultsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateTermuxToolsCard();
        }
    };

    private final Runnable statusUpdater = new Runnable() {
        @Override
        public void run() {
            updateStatusPill();
            if (mobileLayout) {
                updateDiagnostics();
            }
            clockHandler.postDelayed(this, STATUS_UPDATE_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            pendingWidgetId = savedInstanceState.getInt(
                    "pending_widget_id",
                    AppWidgetManager.INVALID_APPWIDGET_ID
            );
        }
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetHost = new AppWidgetHost(this, APP_WIDGET_HOST_ID);

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidthDp = (int) (displayMetrics.widthPixels / displayMetrics.density);
        boolean television = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        mobileLayout = !television && getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
        compactLayout = screenWidthDp < 600;
        int horizontalPaddingDp = compactLayout ? 20 : 32;
        int availableWidthDp = screenWidthDp - (horizontalPaddingDp * 2);
        gridColumnCount = Math.max(
                MIN_GRID_COLUMN_COUNT,
                Math.min(MAX_GRID_COLUMN_COUNT, availableWidthDp / MIN_GRID_CELL_WIDTH_DP)
        );
        cardWidth = Math.min(220, (availableWidthDp - (20 * gridColumnCount)) / gridColumnCount);

        ScrollView rootScroll = new ScrollView(this);
        rootScroll.setFillViewport(true);
        rootScroll.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(
                dp(horizontalPaddingDp),
                dp(compactLayout ? 24 : 40),
                dp(horizontalPaddingDp),
                dp(compactLayout ? 32 : 48)
        );

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(compactLayout ? 28 : 36));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher_mark);
        int logoSizeDp = compactLayout ? 48 : 60;
        int logoInsetDp = compactLayout ? 8 : 10;
        logo.setPadding(dp(logoInsetDp), dp(logoInsetDp), dp(logoInsetDp), dp(logoInsetDp));
        GradientDrawable logoBackground = roundedBackground(COLOR_SURFACE_RAISED, 18, 0, COLOR_SURFACE_RAISED);
        logo.setBackground(logoBackground);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(logoSizeDp), dp(logoSizeDp));
        logoParams.setMargins(0, 0, dp(compactLayout ? 12 : 16), 0);
        logo.setLayoutParams(logoParams);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);

        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);
        titleStack.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(mobileLayout ? "Debug Phone" : "TV Debloat");
        title.setTextColor(Color.WHITE);
        title.setTextSize(compactLayout ? 17 : 22);
        title.setSingleLine(true);
        title.setTypeface(null, Typeface.BOLD);

        TextView subtitle = new TextView(this);
        subtitle.setText(mobileLayout ? "LOCAL SYSTEM STATUS" : "A CLEAN HOME FOR YOUR APPS");
        subtitle.setTextColor(COLOR_TEXT_SECONDARY);
        subtitle.setTextSize(9);
        subtitle.setLetterSpacing(0.1f);
        if (compactLayout) {
            subtitle.setVisibility(TextView.GONE);
        }

        statusText = new TextView(this);
        statusText.setTextColor(COLOR_ACCENT);
        statusText.setTextSize(compactLayout ? 10 : 11);
        statusText.setLetterSpacing(0.04f);
        int statusHorizontalPaddingDp = compactLayout ? 8 : 10;
        statusText.setPadding(
                dp(statusHorizontalPaddingDp),
                dp(compactLayout ? 4 : 5),
                dp(statusHorizontalPaddingDp),
                dp(compactLayout ? 4 : 5)
        );
        statusText.setBackground(roundedBackground(COLOR_SURFACE, 20, 0, COLOR_SURFACE));

        titleStack.addView(title);
        titleStack.addView(subtitle);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.setMargins(0, dp(compactLayout ? 5 : 7), 0, 0);
        statusText.setLayoutParams(statusParams);
        titleStack.addView(statusText);

        Button btnManage = createActionButton("+ Apps");
        btnManage.setTextColor(COLOR_BACKGROUND);
        btnManage.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(45, 10, 15, 22)),
                roundedBackground(COLOR_ACCENT, 14, 0, COLOR_ACCENT),
                roundedBackground(Color.WHITE, 14, 0, Color.WHITE)
        ));
        btnManage.setOnClickListener(v -> showAppPickerDialog());

        Button btnSysSettings = createActionButton("⚙");
        btnSysSettings.setContentDescription("System settings");
        btnSysSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_SETTINGS)));

        header.addView(logo);
        header.addView(titleStack);
        header.addView(btnManage);
        header.addView(btnSysSettings);
        mainLayout.addView(header);

        if (mobileLayout) {
            TextView diagnosticsHeader = createSectionTitle("DEVICE CHECK");
            mainLayout.addView(diagnosticsHeader);

            diagnosticsContainer = new LinearLayout(this);
            diagnosticsContainer.setOrientation(LinearLayout.VERTICAL);
            diagnosticsContainer.setPadding(0, dp(10), 0, dp(24));

            deviceInfoText = createDiagnosticValue();
            batteryStorageText = createDiagnosticValue();
            networkStatusText = createDiagnosticValue();
            termuxToolsText = createDiagnosticValue();

            diagnosticsContainer.addView(createDiagnosticCard("DEVICE", deviceInfoText));
            diagnosticsContainer.addView(createDiagnosticCard("POWER & STORAGE", batteryStorageText));
            diagnosticsContainer.addView(createDiagnosticCard("NETWORK", networkStatusText));

            LinearLayout toolsHeader = new LinearLayout(this);
            toolsHeader.setOrientation(LinearLayout.HORIZONTAL);
            toolsHeader.setGravity(Gravity.CENTER_VERTICAL);
            toolsHeader.setPadding(0, dp(8), 0, dp(8));

            TextView toolsTitle = createSectionTitle("TERMUX TOOLS");
            toolsTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            Button scanToolsButton = createActionButton("SCAN");
            scanToolsButton.setOnClickListener(v -> scanTermuxTools());
            toolsHeader.addView(toolsTitle);
            toolsHeader.addView(scanToolsButton);
            diagnosticsContainer.addView(toolsHeader);
            diagnosticsContainer.addView(createDiagnosticCard("AVAILABLE COMMANDS", termuxToolsText));

            mainLayout.addView(diagnosticsContainer);
        }

        LinearLayout widgetSectionHeader = new LinearLayout(this);
        widgetSectionHeader.setOrientation(LinearLayout.HORIZONTAL);
        widgetSectionHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView widgetsTitle = createSectionTitle("WIDGETS");
        widgetsTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button addWidgetButton = createActionButton("+ WIDGET");
        addWidgetButton.setOnClickListener(v -> pickWidget());
        widgetSectionHeader.addView(widgetsTitle);
        widgetSectionHeader.addView(addWidgetButton);
        mainLayout.addView(widgetSectionHeader);

        widgetContainer = new LinearLayout(this);
        widgetContainer.setOrientation(LinearLayout.VERTICAL);
        widgetContainer.setPadding(0, dp(10), 0, dp(28));
        mainLayout.addView(widgetContainer);

        TextView pinnedHeader = createSectionTitle("PINNED");
        mainLayout.addView(pinnedHeader);

        HorizontalScrollView pinnedScroll = new HorizontalScrollView(this);
        pinnedScroll.setHorizontalScrollBarEnabled(false);
        pinnedScroll.setClipToPadding(false);
        pinnedContainer = new LinearLayout(this);
        pinnedContainer.setOrientation(LinearLayout.HORIZONTAL);
        pinnedContainer.setPadding(0, dp(12), 0, dp(32));
        pinnedScroll.addView(pinnedContainer);
        mainLayout.addView(pinnedScroll);

        TextView appsHeader = createSectionTitle("APP LIBRARY");
        mainLayout.addView(appsHeader);

        appsGrid = new GridLayout(this);
        appsGrid.setColumnCount(gridColumnCount);
        appsGrid.setPadding(0, dp(12), 0, dp(20));
        mainLayout.addView(appsGrid);

        rootScroll.addView(mainLayout);
        setContentView(rootScroll);
        getWindow().setStatusBarColor(COLOR_BACKGROUND);
        getWindow().setNavigationBarColor(COLOR_BACKGROUND);

        updateStatusPill();
        refreshAll();
        loadHostedWidgets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        clockHandler.post(statusUpdater);
        refreshAll();
    }

    @Override
    protected void onStart() {
        super.onStart();
        appWidgetHost.startListening();
        loadHostedWidgets();

        if (mobileLayout) {
            IntentFilter resultFilter = new IntentFilter(ACTION_TERMUX_RESULT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(termuxResultsReceiver, resultFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(termuxResultsReceiver, resultFilter);
            }
            termuxReceiverRegistered = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        clockHandler.removeCallbacks(statusUpdater);
    }

    @Override
    protected void onStop() {
        if (termuxReceiverRegistered) {
            unregisterReceiver(termuxResultsReceiver);
            termuxReceiverRegistered = false;
        }
        appWidgetHost.stopListening();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("pending_widget_id", pendingWidgetId);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PICK_WIDGET) {
            handleWidgetPicked(resultCode, data);
        } else if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            int widgetId = data == null
                    ? pendingWidgetId
                    : data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId);
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
            if (resultCode == RESULT_OK && widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                saveWidgetId(widgetId);
            } else if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                appWidgetHost.deleteAppWidgetId(widgetId);
            }
        }
    }

    private void updateStatusPill() {
        String timeText = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int batteryPercentage = -1;

        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) {
                batteryPercentage = (int) ((level / (float) scale) * 100);
            }
        }

        if (batteryPercentage >= 0 && !mobileLayout) {
            timeText += "  •  BAT " + batteryPercentage + "%";
        }
        statusText.setText(timeText);
    }

    private void refreshAll() {
        loadPinnedApps();
        loadSelectedApps();
        if (mobileLayout) {
            updateDiagnostics();
        }
    }

    private TextView createDiagnosticValue() {
        TextView value = new TextView(this);
        value.setTextColor(Color.WHITE);
        value.setTextSize(13);
        value.setTypeface(Typeface.MONOSPACE);
        return value;
    }

    private LinearLayout createDiagnosticCard(String title, TextView value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundedBackground(COLOR_SURFACE, 14, dp(1), Color.rgb(66, 27, 29)));

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(COLOR_ACCENT);
        heading.setTextSize(10);
        heading.setLetterSpacing(0.12f);
        heading.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        card.addView(heading);

        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        valueParams.setMargins(0, dp(8), 0, 0);
        card.addView(value, valueParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(cardParams);
        return card;
    }

    private void updateDiagnostics() {
        if (deviceInfoText == null) {
            return;
        }

        deviceInfoText.setText(Build.MANUFACTURER + " " + Build.MODEL
                + "\nAndroid " + Build.VERSION.RELEASE + "  /  API " + Build.VERSION.SDK_INT);

        Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        String batteryText = "Battery status unavailable";
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) {
                batteryText = "Battery  " + (level * 100 / scale) + "%";
            }
        }

        StatFs storageStats = new StatFs(Environment.getDataDirectory().getPath());
        long availableBytes = storageStats.getAvailableBytes();
        long totalBytes = storageStats.getTotalBytes();
        batteryStorageText.setText(
                batteryText + "\nFree  " + Formatter.formatFileSize(this, availableBytes)
                        + " / " + Formatter.formatFileSize(this, totalBytes)
        );

        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network activeNetwork = connectivityManager == null ? null : connectivityManager.getActiveNetwork();
        NetworkCapabilities capabilities = activeNetwork == null || connectivityManager == null
                ? null
                : connectivityManager.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) {
            networkStatusText.setText("Offline");
        } else {
            String connectionType = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    ? "Wi-Fi"
                    : capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                            ? "Mobile"
                            : capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                                    ? "Ethernet"
                                    : "Connected";
            String internetStatus = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    ? "Internet available"
                    : "No verified internet";
            networkStatusText.setText(connectionType + "\n" + internetStatus);
        }

        updateTermuxToolsCard();
    }

    private void updateTermuxToolsCard() {
        if (termuxToolsText == null) {
            return;
        }

        String error = prefs.getString(KEY_TERMUX_TOOLS_ERROR, "");
        String output = prefs.getString(KEY_TERMUX_TOOLS_OUTPUT, "");
        if (!error.isEmpty()) {
            termuxToolsText.setText("ERROR  " + error);
        } else if (!output.isEmpty()) {
            termuxToolsText.setText(output);
        } else {
            termuxToolsText.setText("Tap SCAN for a read-only check. Requires Termux and its external-app opt-in.");
        }
    }

    private void scanTermuxTools() {
        try {
            getPackageManager().getPackageInfo("com.termux", 0);
        } catch (PackageManager.NameNotFoundException e) {
            prefs.edit()
                    .putString(KEY_TERMUX_TOOLS_ERROR, "Termux is not installed.")
                    .putString(KEY_TERMUX_TOOLS_OUTPUT, "")
                    .apply();
            updateTermuxToolsCard();
            return;
        }

        prefs.edit()
                .putString(KEY_TERMUX_TOOLS_ERROR, "")
                .putString(KEY_TERMUX_TOOLS_OUTPUT, "Checking allowlisted tools...")
                .apply();
        updateTermuxToolsCard();

        String command = "for tool in bash curl git python python3 ssh tar jq top pkg adb; "
                + "do if command -v \"$tool\" >/dev/null 2>&1; then printf '%s\\n' \"$tool\"; fi; done";
        Intent runCommand = new Intent(TERMUX_COMMAND);
        runCommand.setClassName("com.termux", "com.termux.app.RunCommandService");
        runCommand.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
        runCommand.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[] {"-c", command});
        runCommand.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
        runCommand.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        runCommand.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "Read-only tool scan");
        runCommand.putExtra(
                "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION",
                "Checks a fixed list of installed command names without changing device data."
        );

        Intent resultIntent = new Intent(this, TermuxResultReceiver.class);
        resultIntent.setAction(ACTION_TERMUX_RESULT);
        int requestCode = (int) (System.currentTimeMillis() & 0x7fffffff);
        int pendingIntentFlags = PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingIntentFlags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent resultPendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                resultIntent,
                pendingIntentFlags
        );
        runCommand.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", resultPendingIntent);

        try {
            if (startService(runCommand) == null) {
                showTermuxSetupError("Termux command service is unavailable.");
            }
        } catch (SecurityException e) {
            showTermuxSetupError(
                    "Allow 'Run commands in Termux' for this app and enable allow-external-apps in Termux."
            );
        } catch (IllegalStateException | IllegalArgumentException e) {
            showTermuxSetupError("Could not start the Termux command service: " + e.getMessage());
        }
    }

    private void showTermuxSetupError(String message) {
        prefs.edit()
                .putString(KEY_TERMUX_TOOLS_ERROR, message)
                .putString(KEY_TERMUX_TOOLS_OUTPUT, "")
                .apply();
        updateTermuxToolsCard();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void pickWidget() {
        pendingWidgetId = appWidgetHost.allocateAppWidgetId();
        Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId);

        try {
            startActivityForResult(pickIntent, REQUEST_PICK_WIDGET);
        } catch (ActivityNotFoundException | SecurityException e) {
            appWidgetHost.deleteAppWidgetId(pendingWidgetId);
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
            Toast.makeText(this, "Android's widget picker is unavailable.", Toast.LENGTH_LONG).show();
        }
    }

    private void handleWidgetPicked(int resultCode, Intent data) {
        int allocatedWidgetId = pendingWidgetId;
        if (resultCode != RESULT_OK || data == null) {
            if (allocatedWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                appWidgetHost.deleteAppWidgetId(allocatedWidgetId);
            }
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
            return;
        }

        int widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, allocatedWidgetId);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            if (allocatedWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                appWidgetHost.deleteAppWidgetId(allocatedWidgetId);
            }
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
            Toast.makeText(this, "Android did not return a widget selection.", Toast.LENGTH_LONG).show();
            return;
        }
        if (allocatedWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && widgetId != allocatedWidgetId) {
            appWidgetHost.deleteAppWidgetId(allocatedWidgetId);
        }
        pendingWidgetId = widgetId;

        AppWidgetProviderInfo providerInfo = appWidgetManager.getAppWidgetInfo(widgetId);
        if (providerInfo == null) {
            appWidgetHost.deleteAppWidgetId(widgetId);
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
            Toast.makeText(this, "The selected widget is no longer available.", Toast.LENGTH_LONG).show();
            return;
        }

        if (providerInfo.configure == null) {
            saveWidgetId(widgetId);
            return;
        }

        Intent configureIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
        configureIntent.setComponent(providerInfo.configure);
        configureIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        try {
            startActivityForResult(configureIntent, REQUEST_CONFIGURE_WIDGET);
        } catch (ActivityNotFoundException | SecurityException e) {
            appWidgetHost.deleteAppWidgetId(widgetId);
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
            Toast.makeText(this, "The widget's setup screen could not be opened.", Toast.LENGTH_LONG).show();
        }
    }

    private void saveWidgetId(int widgetId) {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return;
        }
        Set<String> widgetIds = new HashSet<>(prefs.getStringSet(KEY_WIDGET_IDS, new HashSet<>()));
        widgetIds.add(Integer.toString(widgetId));
        prefs.edit().putStringSet(KEY_WIDGET_IDS, widgetIds).apply();
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        loadHostedWidgets();
    }

    private void loadHostedWidgets() {
        if (widgetContainer == null) {
            return;
        }
        widgetContainer.removeAllViews();
        Set<String> storedIds = new HashSet<>(prefs.getStringSet(KEY_WIDGET_IDS, new HashSet<>()));
        Set<String> validIds = new HashSet<>();

        for (String idValue : storedIds) {
            int widgetId;
            try {
                widgetId = Integer.parseInt(idValue);
            } catch (NumberFormatException e) {
                continue;
            }
            if (widgetId < 0) {
                continue;
            }

            AppWidgetProviderInfo providerInfo = appWidgetManager.getAppWidgetInfo(widgetId);
            if (providerInfo == null) {
                appWidgetHost.deleteAppWidgetId(widgetId);
                continue;
            }

            validIds.add(idValue);
            LinearLayout widgetCard = new LinearLayout(this);
            widgetCard.setOrientation(LinearLayout.VERTICAL);
            widgetCard.setPadding(dp(12), dp(12), dp(12), dp(12));
            widgetCard.setBackground(roundedBackground(COLOR_SURFACE, 16, dp(1), Color.rgb(58, 31, 33)));

            LinearLayout widgetHeader = new LinearLayout(this);
            widgetHeader.setOrientation(LinearLayout.HORIZONTAL);
            widgetHeader.setGravity(Gravity.CENTER_VERTICAL);
            TextView providerName = new TextView(this);
            providerName.setText(providerInfo.loadLabel(getPackageManager()));
            providerName.setTextColor(Color.WHITE);
            providerName.setTextSize(13);
            providerName.setLayoutParams(new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            Button removeButton = createActionButton("REMOVE");
            removeButton.setTextColor(COLOR_ACCENT);
            removeButton.setOnClickListener(v -> confirmRemoveWidget(widgetId, providerName.getText().toString()));
            widgetHeader.addView(providerName);
            widgetHeader.addView(removeButton);
            widgetCard.addView(widgetHeader);

            AppWidgetHostView hostView = appWidgetHost.createView(this, widgetId, providerInfo);
            LinearLayout.LayoutParams hostParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            hostParams.setMargins(0, dp(10), 0, 0);
            widgetCard.addView(hostView, hostParams);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins(0, 0, 0, dp(10));
            widgetContainer.addView(widgetCard, cardParams);
        }

        if (!validIds.equals(storedIds)) {
            prefs.edit().putStringSet(KEY_WIDGET_IDS, validIds).apply();
        }
        if (widgetContainer.getChildCount() == 0) {
            TextView emptyState = new TextView(this);
            emptyState.setText("Add widgets from your installed apps.");
            emptyState.setTextColor(COLOR_TEXT_SECONDARY);
            emptyState.setTextSize(13);
            emptyState.setPadding(dp(8), dp(8), dp(8), dp(16));
            widgetContainer.addView(emptyState);
        }
    }

    private void confirmRemoveWidget(int widgetId, String providerName) {
        new AlertDialog.Builder(this)
                .setTitle("Remove widget")
                .setMessage("Remove the " + providerName + " widget from this launcher?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    Set<String> widgetIds = new HashSet<>(
                            prefs.getStringSet(KEY_WIDGET_IDS, new HashSet<>())
                    );
                    widgetIds.remove(Integer.toString(widgetId));
                    prefs.edit().putStringSet(KEY_WIDGET_IDS, widgetIds).apply();
                    appWidgetHost.deleteAppWidgetId(widgetId);
                    loadHostedWidgets();
                })
                .show();
    }

    private void loadPinnedApps() {
        pinnedContainer.removeAllViews();
        PackageManager packageManager = getPackageManager();
        Set<String> pinnedPackages = new HashSet<>(prefs.getStringSet(KEY_PINNED_APPS, new HashSet<>()));

        if (pinnedPackages.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText("Long-press an app to add it to your dock");
            hint.setTextColor(COLOR_TEXT_SECONDARY);
            hint.setTextSize(14);
            hint.setPadding(dp(12), dp(24), dp(12), dp(24));
            pinnedContainer.addView(hint);
            return;
        }

        for (String packageName : pinnedPackages) {
            try {
                CharSequence label = packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0));
                Drawable icon = packageManager.getApplicationIcon(packageName);
                LinearLayout card = createAppCard(label.toString(), icon, cardWidth, true);
                card.setOnClickListener(v -> {
                    Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                    }
                });
                card.setOnLongClickListener(v -> {
                    togglePinApp(packageName, label.toString());
                    return true;
                });
                pinnedContainer.addView(card);
            } catch (PackageManager.NameNotFoundException ignored) {
                // App no longer exists; ignore the stale reference.
            }
        }
    }

    private void loadSelectedApps() {
        appsGrid.removeAllViews();
        PackageManager packageManager = getPackageManager();
        Set<String> allowedPackages = prefs.getStringSet(KEY_ALLOWED_APPS, null);

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> packageApps = new ArrayList<>(packageManager.queryIntentActivities(mainIntent, 0));
        Collections.sort(packageApps, new ResolveInfo.DisplayNameComparator(packageManager));

        for (ResolveInfo appInfo : packageApps) {
            String packageName = appInfo.activityInfo.packageName;
            if (packageName.equals(getPackageName())) {
                continue;
            }
            if (allowedPackages != null && !allowedPackages.contains(packageName)) {
                continue;
            }

            String appName = appInfo.loadLabel(packageManager).toString();
            Drawable icon = appInfo.loadIcon(packageManager);

            LinearLayout appCard = createAppCard(appName, icon, cardWidth, false);
            appCard.setOnClickListener(v -> {
                Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
                if (launchIntent != null) {
                    startActivity(launchIntent);
                }
            });
            appCard.setOnLongClickListener(v -> {
                showAppOptionsDialog(packageName, appName);
                return true;
            });
            appsGrid.addView(appCard);
        }
    }

    private void showAppOptionsDialog(String packageName, String appName) {
        Set<String> pinnedPackages = new HashSet<>(prefs.getStringSet(KEY_PINNED_APPS, new HashSet<>()));
        boolean isPinned = pinnedPackages.contains(packageName);
        String[] options = new String[] {
                isPinned ? "Unpin from Dock" : "Pin to Top Dock",
                "App Info"
        };

        new AlertDialog.Builder(this)
                .setTitle(appName)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        togglePinApp(packageName, appName);
                    } else {
                        Intent infoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        infoIntent.setData(Uri.parse("package:" + packageName));
                        startActivity(infoIntent);
                    }
                })
                .show();
    }

    private void togglePinApp(String packageName, String appName) {
        Set<String> updated = new HashSet<>(prefs.getStringSet(KEY_PINNED_APPS, new HashSet<>()));

        if (updated.contains(packageName)) {
            updated.remove(packageName);
            Toast.makeText(this, "Unpinned " + appName, Toast.LENGTH_SHORT).show();
        } else {
            updated.add(packageName);
            Toast.makeText(this, "Pinned " + appName + " to Dock", Toast.LENGTH_SHORT).show();
        }

        prefs.edit().putStringSet(KEY_PINNED_APPS, updated).apply();
        loadPinnedApps();
    }

    private void showAppPickerDialog() {
        PackageManager packageManager = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> allApps = new ArrayList<>(packageManager.queryIntentActivities(mainIntent, 0));
        Collections.sort(allApps, new ResolveInfo.DisplayNameComparator(packageManager));

        List<String> names = new ArrayList<>();
        List<String> packageNames = new ArrayList<>();
        Set<String> currentAllowed = prefs.getStringSet(KEY_ALLOWED_APPS, null);

        for (ResolveInfo appInfo : allApps) {
            String packageName = appInfo.activityInfo.packageName;
            if (!packageName.equals(getPackageName())) {
                names.add(appInfo.loadLabel(packageManager).toString());
                packageNames.add(packageName);
            }
        }

        String[] nameArray = names.toArray(new String[0]);
        boolean[] checkedItems = new boolean[packageNames.size()];

        for (int i = 0; i < packageNames.size(); i++) {
            checkedItems[i] = currentAllowed == null || currentAllowed.contains(packageNames.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Visible Apps")
                .setMultiChoiceItems(nameArray, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked)
                .setPositiveButton("Save", (dialog, which) -> {
                    Set<String> newAllowed = new HashSet<>();
                    for (int i = 0; i < packageNames.size(); i++) {
                        if (checkedItems[i]) {
                            newAllowed.add(packageNames.get(i));
                        }
                    }
                    prefs.edit().putStringSet(KEY_ALLOWED_APPS, newAllowed).apply();
                    loadSelectedApps();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private LinearLayout createAppCard(String labelText, Drawable iconDrawable, int width, boolean isPinned) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(width), dp(width + 42));
        params.setMargins(dp(10), dp(8), dp(10), dp(8));
        card.setLayoutParams(params);
        card.setPadding(dp(10), dp(14), dp(10), dp(12));

        int surfaceColor = isPinned ? COLOR_SURFACE_RAISED : COLOR_SURFACE;
        int borderColor = isPinned ? COLOR_ACCENT : Color.rgb(52, 52, 56);
        GradientDrawable background = roundedBackground(surfaceColor, 20, dp(isPinned ? 2 : 1), borderColor);
        card.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(35, 255, 255, 255)),
                background,
                roundedBackground(Color.WHITE, 20, 0, Color.WHITE)
        ));
        card.setFocusable(true);
        card.setElevation(dp(2));
        card.setOnFocusChangeListener((view, hasFocus) -> {
            view.animate()
                    .scaleX(hasFocus ? 1.04f : 1f)
                    .scaleY(hasFocus ? 1.04f : 1f)
                    .translationZ(hasFocus ? dp(8) : 0)
                    .setDuration(140)
                    .start();
        });

        if (iconDrawable != null) {
            ImageView iconView = new ImageView(this);
            iconView.setImageDrawable(iconDrawable);
            int iconSizeDp = Math.min(84, Math.max(48, Math.round(width * 0.58f)));
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(iconSizeDp), dp(iconSizeDp));
            iconParams.setMargins(0, 0, 0, dp(14));
            iconView.setLayoutParams(iconParams);
            card.addView(iconView);
        }

        TextView labelView = new TextView(this);
        labelView.setText(labelText);
        labelView.setTextColor(Color.rgb(240, 240, 242));
        labelView.setTextSize(width < 145 ? 11 : 12);
        labelView.setGravity(Gravity.CENTER);
        labelView.setMaxLines(1);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(labelView);
        return card;
    }

    private TextView createSectionTitle(String text) {
        TextView sectionTitle = new TextView(this);
        sectionTitle.setText(text);
        sectionTitle.setTextColor(Color.rgb(230, 230, 232));
        sectionTitle.setTextSize(14);
        sectionTitle.setLetterSpacing(0.08f);
        sectionTitle.setTypeface(null, Typeface.BOLD);
        return sectionTitle;
    }

    private Button createActionButton(String text) {
        Button actionButton = new Button(this);
        actionButton.setText(text);
        actionButton.setTextColor(Color.WHITE);
        actionButton.setTextSize(compactLayout ? 12 : 13);
        actionButton.setAllCaps(false);
        actionButton.setMinHeight(dp(48));
        actionButton.setMinWidth(dp(48));
        int horizontalPaddingDp = compactLayout ? 10 : 16;
        actionButton.setPadding(dp(horizontalPaddingDp), 0, dp(horizontalPaddingDp), 0);
        actionButton.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(45, 255, 255, 255)),
                roundedBackground(COLOR_SURFACE_RAISED, 14, dp(1), Color.rgb(62, 62, 66)),
                roundedBackground(Color.WHITE, 14, 0, Color.WHITE)
        ));
        actionButton.setElevation(dp(2));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(dp(compactLayout ? 4 : 8), 0, 0, 0);
        actionButton.setLayoutParams(params);
        return actionButton;
    }

    private GradientDrawable roundedBackground(int color, int cornerRadiusDp, int strokeWidthPx, int strokeColor) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(cornerRadiusDp));
        if (strokeWidthPx > 0) {
            background.setStroke(strokeWidthPx, strokeColor);
        }
        return background;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
