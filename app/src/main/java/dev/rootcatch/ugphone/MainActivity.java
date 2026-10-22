package dev.rootcatch.ugphone;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import dev.rootcatch.R;
import dev.rootcatch.automation.AutomationConfig;
import dev.rootcatch.automation.BerryOptions;
import dev.rootcatch.automation.SessionLimits;
import dev.rootcatch.automation.ThrowQuality;
import dev.rootcatch.automation.PokemonSelection;
import dev.rootcatch.nativeengine.EnginePolicy;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int
        BG      = 0xFF141618,
        SURFACE = 0xFF1E2228,
        BORDER  = 0xFF2A3040,
        TEXT    = 0xFFE3E6EA,
        MUTED   = 0xFF8A9099,
        DIM     = 0xFF545C6B,
        ACCENT  = 0xFF4F9DE8,
        GREEN   = 0xFF52D67A,
        RED     = 0xFFF87171,
        HEADER  = 0xFF0D1117;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<View> settings = new ArrayList<>();
    private SharedPreferences prefs;
    private Switch capture, spin, spinGyms, transfer, clean, useBerries, showIvNames, extendedManualTransfer, fastGiftOpening, hideTrainerName;
    private CheckBox keepShiny, keepHundo, keepNundo, keepDynamax, keepHatched, keepBackground, keepShinyBackground, keepFieldResearch, resolveError12, removeBuddy;
    private EditText maxCatches, maxSpins, spinDelay, catchDelay, trainerName;
    private LinearLayout transferOptions, cleanOptions, berryOptions;
    private TextView status, elapsed, caught, spun, transferred, discarded, xp, stardust;
    private Button start, export, clearStatistics, items, throwSelector, berrySelector, pokemonSelector;
    private ThrowQuality throwQuality;
    private String limits;
    private String berryOrder;
    private String onlyCatch;
    private boolean starting;
    private boolean visible;
    private long startingAt;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { renderState(); openArmedGame(); handler.postDelayed(this, 250); }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(AutomationConfig.PREFS, MODE_PRIVATE);
        throwQuality = ThrowQuality.fromSavedValue(prefs.getString("throw_quality", "EXCELLENT"));
        limits = prefs.getString("discard_limits", EnginePolicy.DEFAULT_LIMITS);
        berryOrder = BerryOptions.fromSavedValue(prefs.getString("berry_order", BerryOptions.DEFAULT_ORDER));
        onlyCatch = PokemonSelection.encode(PokemonSelection.parse(prefs.getString(PokemonSelection.KEY, "")));
        FarmService.restoreLast(this);

        LinearLayout screen = column();
        screen.setBackgroundColor(BG);
        screen.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                screen.setPadding(bars.left, bars.top, bars.right, 0);
            } else {
                screen.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), 0);
            }
            return insets;
        });

        // ── Header ────────────────────────────────────────────────────────────
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(HEADER);
        header.setPadding(dp(18), dp(14), dp(18), dp(14));
        TextView appTitle = text("KecleonMod", 18, TEXT);
        appTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(appTitle, new LinearLayout.LayoutParams(0, -2, 1));
        screen.addView(header);

        // ── Status strip ──────────────────────────────────────────────────────
        LinearLayout statusStrip = new LinearLayout(this);
        statusStrip.setOrientation(LinearLayout.HORIZONTAL);
        statusStrip.setGravity(Gravity.CENTER_VERTICAL);
        statusStrip.setBackgroundColor(0xFF111318);
        statusStrip.setPadding(dp(18), dp(7), dp(18), dp(7));
        status = text("Ready", 12, DIM);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        statusStrip.addView(status, new LinearLayout.LayoutParams(-1, -2));
        screen.addView(statusStrip);

        // ── Scrollable content ────────────────────────────────────────────────
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout page = column();
        page.setPadding(dp(14), dp(16), dp(14), dp(20));
        scroll.addView(page);

        // ── Elapsed + stats tiles ─────────────────────────────────────────────
        elapsed = text("00:00:00", 28, TEXT);
        elapsed.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        elapsed.setPadding(dp(2), 0, 0, dp(12));
        page.addView(elapsed);

        LinearLayout statsRow1 = row();
        caught      = statTile(statsRow1, "Catches");
        spun        = statTile(statsRow1, "Spins");
        page.addView(statsRow1);
        space(page, 6);
        LinearLayout statsRow2 = row();
        transferred = statTile(statsRow2, "Transferred");
        discarded   = statTile(statsRow2, "Items deleted");
        page.addView(statsRow2);
        space(page, 6);
        LinearLayout statsRow3 = row();
        xp          = statTile(statsRow3, "XP");
        stardust    = statTile(statsRow3, "Stardust");
        page.addView(statsRow3);
        space(page, 10);

        clearStatistics = ghostButton("Clear statistics");
        clearStatistics.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        page.addView(clearStatistics);
        clearStatistics.setOnClickListener(view -> {
            if (!starting && FarmService.clearStatistics(this)) renderState();
        });

        // ── Features ──────────────────────────────────────────────────────────
        space(page, 22);
        sectionLabel(page, "FEATURES");
        space(page, 8);
        LinearLayout features = card();
        page.addView(features);

        capture = toggleRow(features, "Catches", prefs.getBoolean("catch", true));

        pokemonSelector = rowButton(features, "");
        settings.add(pokemonSelector);
        updatePokemonLabel();
        pokemonSelector.setOnClickListener(view -> {
            if (FarmService.isBusy() || starting) return;
            new PokemonPickerDialog(this, onlyCatch).show(selected -> {
                onlyCatch = selected;
                prefs.edit().putString(PokemonSelection.KEY, selected).apply();
                updatePokemonLabel();
            });
        });

        throwSelector = rowButton(features, getString(R.string.throw_quality_selector, throwQuality.label));
        settings.add(throwSelector);
        throwSelector.setOnClickListener(view -> {
            if (FarmService.isBusy() || starting) return;
            new SettingsDialogs(this).throwsDialog(throwQuality, selected -> {
                throwQuality = selected;
                prefs.edit().putString("throw_quality", selected.name()).apply();
                throwSelector.setText(getString(R.string.throw_quality_selector, selected.label));
            });
        });

        divider(features);
        useBerries = toggleRow(features, getString(R.string.use_berries), prefs.getBoolean("use_berries", true));
        berryOptions = column();
        features.addView(berryOptions);
        berrySelector = rowButton(berryOptions, "");
        settings.add(berrySelector);
        updateBerryLabel();
        berrySelector.setOnClickListener(view -> {
            if (FarmService.isBusy() || starting) return;
            new SettingsDialogs(this).berriesDialog(berryOrder, selected -> {
                berryOrder = selected;
                prefs.edit().putString("berry_order", selected).apply();
                updateBerryLabel();
            });
        });
        useBerries.setOnCheckedChangeListener((view, checked) -> berryOptions.setVisibility(checked ? View.VISIBLE : View.GONE));
        berryOptions.setVisibility(useBerries.isChecked() ? View.VISIBLE : View.GONE);

        divider(features);
        spin = toggleRow(features, "Spin PokéStops", prefs.getBoolean("spin", true));
        spinGyms = toggleRow(features, "Spin Gyms  (experimental)", prefs.getBoolean("spin_gyms", false));

        divider(features);
        transfer = toggleRow(features, "Transfer new catches", prefs.getBoolean("transfer", false));
        transferOptions = column();
        transferOptions.setPadding(dp(14), dp(4), dp(14), dp(8));
        features.addView(transferOptions);
        keepShiny           = subCheck(transferOptions, "Keep shiny",                     prefs.getBoolean("keep_shiny", true));
        keepHundo           = subCheck(transferOptions, "Keep 15/15/15",                  prefs.getBoolean("keep_hundo", true));
        keepDynamax         = subCheck(transferOptions, "Keep Dynamax",                   prefs.getBoolean("keep_dynamax", true));
        keepHatched         = subCheck(transferOptions, "Keep hatched",                   prefs.getBoolean("keep_hatched", true));
        keepBackground      = subCheck(transferOptions, "Keep background",                prefs.getBoolean("keep_background", true));
        keepShinyBackground = subCheck(transferOptions, "Keep shiny + background",        prefs.getBoolean("keep_shiny_background", false));
        keepFieldResearch   = subCheck(transferOptions, "Keep field research encounters", prefs.getBoolean("keep_field_research", true));
        keepNundo           = subCheck(transferOptions, "Keep 0/0/0 (nundo)",               prefs.getBoolean("keep_nundo", false));

        divider(features);
        clean = toggleRow(features, "Clean item bag", prefs.getBoolean("clean", false));
        cleanOptions = column();
        cleanOptions.setPadding(dp(14), dp(4), dp(14), dp(8));
        features.addView(cleanOptions);
        items = rowButton(cleanOptions, "");
        settings.add(items);
        updateItemLabel();
        items.setOnClickListener(view -> itemDialog());

        transfer.setOnCheckedChangeListener((view, selected) -> transferOptions.setVisibility(selected ? View.VISIBLE : View.GONE));
        clean.setOnCheckedChangeListener((view, selected) -> cleanOptions.setVisibility(selected ? View.VISIBLE : View.GONE));
        transferOptions.setVisibility(transfer.isChecked() ? View.VISIBLE : View.GONE);
        cleanOptions.setVisibility(clean.isChecked() ? View.VISIBLE : View.GONE);

        // ── Inventory tools ───────────────────────────────────────────────────
        space(page, 22);
        sectionLabel(page, "INVENTORY TOOLS");
        space(page, 8);
        LinearLayout inventoryTools = card();
        page.addView(inventoryTools);
        showIvNames            = toggleRow(inventoryTools, "Show IV names",            prefs.getBoolean("show_iv_names", true));
        divider(inventoryTools);
        extendedManualTransfer = toggleRow(inventoryTools, "Extended manual transfer", prefs.getBoolean("extended_manual_transfer", false));

        // ── Game tools ────────────────────────────────────────────────────────
        space(page, 22);
        sectionLabel(page, "GAME TOOLS");
        space(page, 8);
        LinearLayout gameTools = card();
        page.addView(gameTools);
        fastGiftOpening = toggleRow(gameTools, "Fast gift opening", prefs.getBoolean("fast_gift_opening", true));
        divider(gameTools);
        hideTrainerName = toggleRow(gameTools, "Hide trainer name", prefs.getBoolean("hide_trainer_name", true));

        // ── Settings ─────────────────────────────────────────────────────────
        space(page, 22);
        sectionLabel(page, "SETTINGS");
        space(page, 8);
        LinearLayout settingsCard = card();
        page.addView(settingsCard);

        resolveError12 = checkRow(settingsCard, "Fix error 12", prefs.getBoolean("resolve_error_12", false));
        divider(settingsCard);
        removeBuddy = checkRow(settingsCard, getString(R.string.remove_buddy), prefs.getBoolean("remove_buddy", false));
        divider(settingsCard);
        trainerName = textRow(settingsCard, "Trainer name", prefs.getString("trainer_name", "KecleonBot"));
        divider(settingsCard);
        maxCatches = numericRow(settingsCard, getString(R.string.maximum_catches),
                SessionLimits.savedLimit(prefs.getInt("max_catches", SessionLimits.DEFAULT_CATCHES), SessionLimits.DEFAULT_CATCHES));
        divider(settingsCard);
        maxSpins = numericRow(settingsCard, getString(R.string.maximum_spins),
                SessionLimits.savedLimit(prefs.getInt("max_spins", SessionLimits.DEFAULT_SPINS), SessionLimits.DEFAULT_SPINS));
        divider(settingsCard);
        spinDelay = numericRow(settingsCard, "Spin delay (ms)",
                Math.max(100, Math.min(3000, prefs.getInt("spin_delay", 150))));
        divider(settingsCard);
        catchDelay = numericRow(settingsCard, "Catch delay (ms)",
                Math.max(100, Math.min(3000, prefs.getInt("catch_delay", 150))));

        // ── Bottom action bar ─────────────────────────────────────────────────
        LinearLayout actionBar = column();
        actionBar.setBackgroundColor(HEADER);
        actionBar.setPadding(dp(14), dp(10), dp(14), dp(16));
        screen.addView(actionBar);

        start = primaryButton("Start");
        actionBar.addView(start);
        start.setOnClickListener(view -> {
            if (FarmService.isRunning()) { startService(new Intent(this, FarmService.class).setAction(FarmService.STOP)); return; }
            if (FarmService.isBusy() || starting) return;
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 601); return;
            }
            begin();
        });
        space(actionBar, 8);
        export = ghostButton(getString(R.string.export_logs));
        actionBar.addView(export);
        export.setOnClickListener(view -> exportLogs());

        setContentView(screen);
        screen.requestApplyInsets();
    }

    @Override protected void onResume() { super.onResume(); visible = true; handler.removeCallbacks(refresh); refresh.run(); }
    @Override protected void onPause() { visible = false; saveDraft(); handler.removeCallbacks(refresh); super.onPause(); }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 601 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) begin();
        else if (request == 601) {
            status.setText(R.string.enable_notifications_start);
            Toast.makeText(this, R.string.enable_notifications_start, Toast.LENGTH_LONG).show();
        }
    }

    private void begin() {
        if (FarmService.isBusy() || starting || !visible) return;
        try {
            saveSettings();
            String block = startBlock();
            if (!block.isEmpty()) { status.setText(block); return; }
            if (!FarmService.notificationsVisible(this)) {
                status.setText(R.string.enable_notifications_service);
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                return;
            }
            PackageInfo game = getPackageManager().getPackageInfo(EnginePolicy.GAME, 0);
            if (!EnginePolicy.supports(EnginePolicy.GAME, game.versionName, game.getLongVersionCode()))
                throw new IllegalArgumentException("Supported version: GO " + EnginePolicy.VERSION + " / " + EnginePolicy.VERSION_CODE);
            Intent service = new Intent(this, FarmService.class).setAction(FarmService.START)
                    .putExtra("resolveError12", resolveError12.isChecked())
                    .putExtra("userApprovedGameStop", true)
                    .putExtra("startupAuthorization", "USER_START_BUTTON")
                    .putExtra("transfer", transfer.isChecked()).putExtra("keepShiny", keepShiny.isChecked())
                    .putExtra("keepHundo", keepHundo.isChecked()).putExtra("keepDynamax", keepDynamax.isChecked())
                    .putExtra("keepHatched", keepHatched.isChecked()).putExtra("keepBackground", keepBackground.isChecked())
                    .putExtra("keepShinyBackground", keepShinyBackground.isChecked())
                    .putExtra("keepFieldResearch", keepFieldResearch.isChecked())
                    .putExtra("keepNundo", keepNundo.isChecked());
            starting = true; startingAt = android.os.SystemClock.elapsedRealtime(); renderState();
            startForegroundService(service);
        } catch (Exception error) { starting = false; Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void saveSettings() {
        FarmPolicy.validate(capture.isChecked(), spin.isChecked(), clean.isChecked(), transfer.isChecked(), limits);
        if (capture.isChecked() && useBerries.isChecked() && BerryOptions.parse(berryOrder).isEmpty())
            throw new IllegalArgumentException(getString(R.string.select_berries));
        String name = trainerName.getText().toString().trim();
        if (name.isEmpty()) name = "KecleonBot";
        SharedPreferences.Editor edit = prefs.edit().putString("package", EnginePolicy.GAME).putBoolean("internal_engine", true)
                .putString("throw_quality", throwQuality.name())
                .putString(PokemonSelection.KEY, onlyCatch)
                .putBoolean("use_berries", useBerries.isChecked()).putString("berry_order", berryOrder)
                .putBoolean("catch", capture.isChecked()).putBoolean("spin", spin.isChecked()).putBoolean("spin_gyms", spinGyms.isChecked()).putBoolean("clean", clean.isChecked())
                .putBoolean("resolve_error_12", resolveError12.isChecked())
                .putBoolean("remove_buddy", removeBuddy.isChecked())
                .putBoolean("transfer", transfer.isChecked()).putBoolean("keep_shiny", keepShiny.isChecked()).putBoolean("keep_hundo", keepHundo.isChecked())
                .putBoolean("keep_dynamax", keepDynamax.isChecked()).putBoolean("keep_hatched", keepHatched.isChecked())
                .putBoolean("keep_background", keepBackground.isChecked()).putBoolean("keep_shiny_background", keepShinyBackground.isChecked())
                .putBoolean("keep_field_research", keepFieldResearch.isChecked())
                .putBoolean("keep_nundo", keepNundo.isChecked())
                .putBoolean("show_iv_names", showIvNames.isChecked())
                .putBoolean("extended_manual_transfer", extendedManualTransfer.isChecked())
                .putBoolean("fast_gift_opening", fastGiftOpening.isChecked())
                .putBoolean("hide_trainer_name", hideTrainerName.isChecked())
                .putString("trainer_name", name)
                .putInt("max_transfers", 0).putString("discard_limits", EnginePolicy.limitsKey(limits))
                .putInt("max_discarded_items", 0).putInt("max_throws", 0);
        edit.putInt("max_catches", read(maxCatches, 1, Integer.MAX_VALUE, getString(R.string.maximum_catches)))
                .putInt("max_spins", read(maxSpins, 1, Integer.MAX_VALUE, getString(R.string.maximum_spins)))
                .putInt("spin_delay", read(spinDelay, 100, 3000, "Spin delay"))
                .putInt("catch_delay", read(catchDelay, 100, 3000, "Catch delay"));
        edit.apply();
    }

    private void saveDraft() {
        if (!FarmService.isBusy() && !starting && prefs != null && capture != null) {
            try { saveSettings(); } catch (IllegalArgumentException ignored) { }
            try {
                prefs.edit()
                        .putInt("max_catches", read(maxCatches, 1, Integer.MAX_VALUE, "Capture limit"))
                        .putInt("max_spins", read(maxSpins, 1, Integer.MAX_VALUE, "Spin limit"))
                        .putInt("spin_delay", read(spinDelay, 100, 3000, "Spin delay"))
                        .putInt("catch_delay", read(catchDelay, 100, 3000, "Catch delay")).apply();
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private void updatePokemonLabel() {
        int count = PokemonSelection.parse(onlyCatch).size();
        pokemonSelector.setText(count == 0 ? getString(R.string.only_catch_all)
                : getResources().getQuantityString(R.plurals.only_catch_count, count, count));
    }

    private static int read(EditText input, int min, int max, String label) {
        return FarmPolicy.integer(input.getText().toString(), min, max, label);
    }

    private void openArmedGame() {
        if (!visible || !FarmService.isRunning() || FarmService.isClosing()) return;
        String ticket = FarmService.consumeLaunchTicket();
        if (ticket.isEmpty()) return;
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(EnginePolicy.GAME);
            if (launch == null) throw new IllegalStateException("Could not open Pokémon GO");
            FarmService.noteGameLaunch(ticket);
            startActivity(launch);
        } catch (Exception error) {
            startService(new Intent(this, FarmService.class).setAction(FarmService.STOP)
                    .putExtra("reason", "Could not open the game: " + error.getMessage()));
        }
    }

    private void renderState() {
        if (FarmService.isRunning() || (starting && android.os.SystemClock.elapsedRealtime() - startingAt > 3000)) starting = false;
        SessionCounters values = FarmService.counters();
        caught.setText(String.valueOf(values.caught));
        spun.setText(String.valueOf(values.spun));
        transferred.setText(String.valueOf(values.transferred));
        discarded.setText(String.valueOf(values.discarded));
        xp.setText(values.rewardText(values.xp));
        stardust.setText(values.rewardText(values.stardust));
        elapsed.setText(SessionCounters.elapsed(FarmService.elapsed()));

        boolean running = FarmService.isRunning(), busy = FarmService.isBusy() || starting, closing = FarmService.isClosing();
        String block = busy ? "" : startBlock();
        String statusText = !block.isEmpty() ? block : (starting ? "Preparing…" : FarmService.status());
        status.setText(statusText);
        status.setTextColor(!block.isEmpty() ? Color.rgb(255, 170, 80) : running ? GREEN : DIM);

        for (View setting : settings) { setting.setEnabled(!busy); setting.setAlpha(busy ? 0.5f : 1); }

        if (closing) {
            start.setText("Saving logs…");
            start.setBackground(shape(SURFACE, 14));
            start.setTextColor(MUTED);
        } else if (running) {
            start.setText("Stop");
            start.setBackground(shape(RED, 14));
            start.setTextColor(BG);
        } else {
            start.setText("Start");
            start.setBackground(shape(GREEN, 14));
            start.setTextColor(BG);
        }
        start.setEnabled(!closing && !starting && (!busy || running) && block.isEmpty());
        export.setEnabled(!busy);
        clearStatistics.setEnabled(!busy);
        clearStatistics.setAlpha(!busy ? 1 : 0.5f);
        start.setAlpha(start.isEnabled() ? 1 : 0.5f);
        export.setAlpha(export.isEnabled() ? 1 : 0.5f);
    }

    private String startBlock() {
        try {
            SessionCounters totals = FarmService.counters();
            return SessionLimits.startBlock(totals.caught, totals.spun,
                    read(maxCatches, 1, Integer.MAX_VALUE, "Capture limit"),
                    read(maxSpins, 1, Integer.MAX_VALUE, "Spin limit"));
        } catch (IllegalArgumentException error) { return "Set valid limits"; }
    }

    private void exportLogs() {
        if (!FarmService.beginExport()) return;
        renderState(); export.setText(R.string.exporting);
        new Thread(() -> {
            String result;
            try { result = LogExporter.export(getApplicationContext()); }
            catch (Exception error) { result = "Logs: " + error.getMessage(); }
            finally { FarmService.endExport(); }
            String message = result;
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                export.setText(R.string.export_logs); renderState();
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            });
        }, "rootcatch-export").start();
    }

    private void itemDialog() {
        if (FarmService.isBusy() || starting) return;
        new SettingsDialogs(this).itemsDialog(limits, selected -> {
            limits = selected;
            prefs.edit().putString("discard_limits", selected).apply();
            updateItemLabel();
        });
    }

    private void updateItemLabel() {
        int count = EnginePolicy.parseLimits(limits).size();
        items.setText(getResources().getQuantityString(R.plurals.item_types, count, count));
    }

    private void updateBerryLabel() {
        int count = BerryOptions.parse(berryOrder).size();
        berrySelector.setText(getResources().getQuantityString(R.plurals.berry_types, count, count));
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private Switch toggleRow(LinearLayout parent, String label, boolean value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        row.setPadding(dp(14), dp(10), dp(10), dp(10));
        row.addView(text(label, 15, TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(value);
        sw.setThumbTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{ACCENT, Color.rgb(100, 110, 130)}));
        sw.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Color.argb(80, 79, 157, 232), Color.rgb(45, 52, 65)}));
        row.addView(sw);
        parent.addView(row);
        settings.add(sw);
        return sw;
    }

    private CheckBox checkRow(LinearLayout parent, String label, boolean value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        CheckBox cb = new CheckBox(this);
        cb.setChecked(value);
        cb.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        row.addView(cb);
        TextView lbl = text(label, 15, TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMarginStart(dp(8));
        row.addView(lbl, lp);
        parent.addView(row);
        settings.add(cb);
        return cb;
    }

    private CheckBox subCheck(LinearLayout parent, String label, boolean value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(44));
        CheckBox cb = new CheckBox(this);
        cb.setChecked(value);
        cb.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        row.addView(cb);
        TextView lbl = text(label, 14, MUTED);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMarginStart(dp(4));
        row.addView(lbl, lp);
        parent.addView(row);
        settings.add(cb);
        return cb;
    }

    private Button rowButton(LinearLayout parent, String label) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(13);
        btn.setTextColor(ACCENT);
        btn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        btn.setBackground(shape(Color.argb(18, 79, 157, 232), 0));
        btn.setPadding(dp(18), dp(10), dp(18), dp(10));
        btn.setMinHeight(dp(44));
        btn.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        parent.addView(btn);
        return btn;
    }

    private EditText numericRow(LinearLayout parent, String label, int value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.addView(text(label, 14, TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setTextSize(14);
        input.setText(String.valueOf(value));
        input.setContentDescription(label);
        input.setBackground(shape(Color.argb(30, 255, 255, 255), 8));
        input.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        input.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(100), dp(40));
        lp.setMarginStart(dp(10));
        row.addView(input, lp);
        parent.addView(row);
        settings.add(input);
        return input;
    }

    private EditText textRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.addView(text(label, 14, TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setTextSize(14);
        input.setText(value);
        input.setHint("KecleonBot");
        input.setHintTextColor(DIM);
        input.setContentDescription(label);
        input.setBackground(shape(Color.argb(30, 255, 255, 255), 8));
        input.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(140), dp(40));
        lp.setMarginStart(dp(10));
        row.addView(input, lp);
        parent.addView(row);
        settings.add(input);
        return input;
    }

    private TextView statTile(LinearLayout parent, String label) {
        LinearLayout tile = column();
        tile.setBackground(shape(SURFACE, 12));
        tile.setPadding(dp(12), dp(10), dp(12), dp(10));
        TextView value = text("0", 22, TEXT);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tile.addView(value);
        tile.addView(text(label, 11, MUTED));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        if (parent.getChildCount() > 0) params.setMarginStart(dp(6));
        parent.addView(tile, params);
        return value;
    }

    private Button primaryButton(String label) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(16);
        btn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        btn.setTextColor(BG);
        btn.setBackground(shape(GREEN, 14));
        btn.setMinHeight(dp(52));
        btn.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        btn.setPadding(dp(14), dp(12), dp(14), dp(12));
        return btn;
    }

    private Button ghostButton(String label) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(14);
        btn.setTextColor(MUTED);
        btn.setBackground(shape(SURFACE, 12));
        btn.setMinHeight(dp(46));
        btn.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        btn.setPadding(dp(14), dp(10), dp(14), dp(10));
        return btn;
    }

    private void sectionLabel(LinearLayout parent, String label) {
        TextView tv = text(label, 11, DIM);
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setPadding(dp(2), 0, 0, 0);
        parent.addView(tv);
    }

    private void divider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(BORDER);
        parent.addView(line, new LinearLayout.LayoutParams(-1, 1));
    }

    private TextView text(String label, float size, int color) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout card() {
        LinearLayout view = column();
        view.setBackground(shape(SURFACE, 14));
        view.setClipToOutline(true);
        return view;
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout row() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private void space(LinearLayout parent, int height) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
