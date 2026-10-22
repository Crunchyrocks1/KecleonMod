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
import android.widget.ImageView;
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
import dev.rootcatch.automation.PokemonSelection;
import dev.rootcatch.nativeengine.EnginePolicy;
import dev.rootcatch.nativeengine.ItemCatalog;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int
        BG      = 0xFF0D1117,
        SURFACE = 0xFF161B22,
        BORDER  = 0xFF30363D,
        TEXT    = 0xFFE6EDF3,
        MUTED   = 0xFF8B949E,
        DIM     = 0xFF484F58,
        ACCENT  = 0xFF4493F8,
        GREEN   = 0xFF3FB950,
        RED     = 0xFFF85149,
        HEADER  = 0xFF010409;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<View> settings = new ArrayList<>();
    private SharedPreferences prefs;
    private Switch capture, spin, spinGyms, transfer, clean, useBerries, showIvNames, extendedManualTransfer, fastGiftOpening, hideTrainerName;
    private CheckBox keepShiny, keepHundo, keepNundo, keepDynamax, keepHatched, keepBackground, keepShinyBackground, keepFieldResearch, resolveError12, removeBuddy;
    private CheckBox throwNormal, throwNice, throwGreat, throwExcellent, curveball, arMode;
    private EditText maxCatches, maxSpins, spinDelay, catchDelay, trainerName;
    private LinearLayout transferOptions, cleanOptions;
    private View statusDot;
    private TextView status, elapsed, caught, spun, transferred, discarded, xp, stardust;
    private Button start, export, clearStatistics, pokemonSelector;
    private CheckBox berryRazz, berryPinap, berryNanab, berryGoldenRazz, berryGoldenPinap;
    private CheckBox goldenRazzForShiny, goldenRazzForHundo, goldenRazzForGalarBirds;
    private Button excludeSelector;
    private String excludePokemon = "";
    private final java.util.LinkedHashMap<String, CheckBox> itemChecks = new java.util.LinkedHashMap<>();
    private final java.util.LinkedHashMap<String, EditText> itemAmounts = new java.util.LinkedHashMap<>();
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
        berryOrder = BerryOptions.fromSavedValue(prefs.getString("berry_order", BerryOptions.DEFAULT_ORDER));
        onlyCatch = PokemonSelection.encode(PokemonSelection.parse(prefs.getString(PokemonSelection.KEY, "")));
        excludePokemon = PokemonSelection.encode(PokemonSelection.parse(prefs.getString("exclude_pokemon", "")));
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
        header.setPadding(dp(14), dp(11), dp(16), dp(11));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.kecleon);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        logoLp.setMarginEnd(dp(10));
        header.addView(logo, logoLp);
        TextView appTitle = text("KecleonMod", 17, TEXT);
        appTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(appTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView versionTag = text("v" + dev.rootcatch.BuildConfig.VERSION_NAME, 11, DIM);
        header.addView(versionTag);
        screen.addView(header);

        // ── Status strip ──────────────────────────────────────────────────────
        LinearLayout statusStrip = new LinearLayout(this);
        statusStrip.setOrientation(LinearLayout.HORIZONTAL);
        statusStrip.setGravity(Gravity.CENTER_VERTICAL);
        statusStrip.setBackgroundColor(Color.argb(0xFF, 1, 10, 20));
        statusStrip.setPadding(dp(16), dp(7), dp(16), dp(7));
        statusDot = new View(this);
        statusDot.setBackground(shape(DIM, 99));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(6), dp(6));
        dotLp.setMarginEnd(dp(8));
        statusStrip.addView(statusDot, dotLp);
        status = text("Ready", 11, DIM);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        statusStrip.addView(status, new LinearLayout.LayoutParams(-1, -2));
        screen.addView(statusStrip);

        // ── Scrollable content ────────────────────────────────────────────────
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout page = column();
        page.setPadding(dp(12), dp(12), dp(12), dp(14));
        scroll.addView(page);

        // ── Elapsed + stats tiles ─────────────────────────────────────────────
        LinearLayout elapsedRow = row();
        elapsedRow.setGravity(Gravity.CENTER_VERTICAL);
        elapsed = text("00:00:00", 24, TEXT);
        elapsed.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        elapsedRow.addView(elapsed, new LinearLayout.LayoutParams(0, -2, 1));
        clearStatistics = ghostButton("Clear");
        clearStatistics.setLayoutParams(new LinearLayout.LayoutParams(-2, dp(34)));
        elapsedRow.addView(clearStatistics);
        page.addView(elapsedRow);
        clearStatistics.setOnClickListener(view -> {
            if (!starting && FarmService.clearStatistics(this)) renderState();
        });
        space(page, 8);

        LinearLayout statsRow1 = row();
        caught      = statTile(statsRow1, "Catches");
        spun        = statTile(statsRow1, "Spins");
        transferred = statTile(statsRow1, "Transferred");
        page.addView(statsRow1);
        space(page, 4);
        LinearLayout statsRow2 = row();
        discarded   = statTile(statsRow2, "Items del");
        xp          = statTile(statsRow2, "XP");
        stardust    = statTile(statsRow2, "Stardust");
        page.addView(statsRow2);

        // ── Accordion sections ────────────────────────────────────────────────
        space(page, 12);

        // ── Catch Settings ────────────────────────────────────────────────────
        LinearLayout catchBody = accordion(page, "Catch Settings", "section_catch");
        capture = toggleRow(catchBody, "Catches", prefs.getBoolean("catch", true));
        LinearLayout captureSubOptions = column();
        catchBody.addView(captureSubOptions);
        pokemonSelector = rowButton(captureSubOptions, "");
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
        excludeSelector = rowButton(captureSubOptions, "");
        settings.add(excludeSelector);
        updateExcludeLabel();
        excludeSelector.setOnClickListener(view -> {
            if (FarmService.isBusy() || starting) return;
            new PokemonPickerDialog(this, excludePokemon, getString(R.string.exclude_pokemon)).show(selected -> {
                excludePokemon = selected;
                prefs.edit().putString("exclude_pokemon", selected).apply();
                updateExcludeLabel();
            });
        });
        LinearLayout throwOptions = column();
        throwOptions.setPadding(dp(14), dp(4), dp(14), dp(8));
        captureSubOptions.addView(throwOptions);
        throwNormal    = subCheck(throwOptions, "Normal throw",    prefs.getBoolean("throw_normal",    false));
        throwNice      = subCheck(throwOptions, "Nice throw",      prefs.getBoolean("throw_nice",      false));
        throwGreat     = subCheck(throwOptions, "Great throw",     prefs.getBoolean("throw_great",     false));
        throwExcellent = subCheck(throwOptions, "Excellent throw", prefs.getBoolean("throw_excellent", true));
        curveball      = subCheck(throwOptions, "Curveball",       prefs.getBoolean("curveball",       true));
        arMode         = subCheck(throwOptions, "AR Mode",         prefs.getBoolean("ar_mode",         false));
        divider(captureSubOptions);
        useBerries = toggleRow(captureSubOptions, getString(R.string.use_berries), prefs.getBoolean("use_berries", true));
        LinearLayout berrySubOptions = column();
        berrySubOptions.setPadding(dp(14), dp(4), dp(14), dp(8));
        captureSubOptions.addView(berrySubOptions);
        List<String> savedBerries = BerryOptions.parse(berryOrder);
        berryRazz        = subCheck(berrySubOptions, "Razz Berry",         savedBerries.contains("RazzBerry"));
        berryPinap       = subCheck(berrySubOptions, "Pinap Berry",        savedBerries.contains("PinapBerry"));
        berryNanab       = subCheck(berrySubOptions, "Nanab Berry",        savedBerries.contains("NanabBerry"));
        berryGoldenRazz  = subCheck(berrySubOptions, "Golden Razz Berry",  savedBerries.contains("GoldenRazzBerry"));
        berryGoldenPinap = subCheck(berrySubOptions, "Golden Pinap Berry", savedBerries.contains("GoldenPinapBerry"));
        useBerries.setOnCheckedChangeListener((view, checked) -> berrySubOptions.setVisibility(checked ? View.VISIBLE : View.GONE));
        berrySubOptions.setVisibility(useBerries.isChecked() ? View.VISIBLE : View.GONE);
        divider(captureSubOptions);
        LinearLayout preConfigOpts = column();
        preConfigOpts.setPadding(dp(14), dp(4), dp(14), dp(8));
        captureSubOptions.addView(preConfigOpts);
        TextView preConfigLabel = text("PRE CONFIGURED ENCOUNTERS", 11, DIM);
        preConfigLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        preConfigLabel.setPadding(dp(2), dp(6), 0, dp(4));
        preConfigOpts.addView(preConfigLabel);
        goldenRazzForShiny       = subCheck(preConfigOpts, "Golden Razz for shinies",    prefs.getBoolean("golden_razz_shiny",        false));
        goldenRazzForHundo       = subCheck(preConfigOpts, "Golden Razz for hundos",     prefs.getBoolean("golden_razz_hundo",        false));
        goldenRazzForGalarBirds  = subCheck(preConfigOpts, "Golden Razz for Galar Birds", prefs.getBoolean("golden_razz_galar_birds", false));
        capture.setOnCheckedChangeListener((v, checked) -> captureSubOptions.setVisibility(checked ? View.VISIBLE : View.GONE));
        captureSubOptions.setVisibility(capture.isChecked() ? View.VISIBLE : View.GONE);
        divider(catchBody);
        catchDelay = numericRow(catchBody, "Catch delay (ms)",
                Math.max(100, Math.min(3000, prefs.getInt("catch_delay", 150))));
        divider(catchBody);
        maxCatches = numericRow(catchBody, getString(R.string.maximum_catches),
                SessionLimits.savedLimit(prefs.getInt("max_catches", SessionLimits.DEFAULT_CATCHES), SessionLimits.DEFAULT_CATCHES));

        space(page, 8);

        // ── Spin Settings ─────────────────────────────────────────────────────
        LinearLayout spinBody = accordion(page, "Spin Settings", "section_spin");
        spin     = toggleRow(spinBody, "Spin PokéStops", prefs.getBoolean("spin",      true));
        spinGyms = toggleRow(spinBody, "Spin Gyms",      prefs.getBoolean("spin_gyms", false));
        divider(spinBody);
        spinDelay = numericRow(spinBody, "Spin delay (ms)",
                Math.max(100, Math.min(3000, prefs.getInt("spin_delay", 150))));
        divider(spinBody);
        maxSpins = numericRow(spinBody, getString(R.string.maximum_spins),
                SessionLimits.savedLimit(prefs.getInt("max_spins", SessionLimits.DEFAULT_SPINS), SessionLimits.DEFAULT_SPINS));

        space(page, 8);

        // ── Transfer Settings ─────────────────────────────────────────────────
        LinearLayout transferBody = accordion(page, "Transfer Settings", "section_transfer");
        transfer = toggleRow(transferBody, "Transfer new catches", prefs.getBoolean("transfer", false));
        transferOptions = column();
        transferOptions.setPadding(dp(14), dp(4), dp(14), dp(8));
        transferBody.addView(transferOptions);
        keepShiny           = subCheck(transferOptions, "Keep shiny",                     prefs.getBoolean("keep_shiny",            true));
        keepHundo           = subCheck(transferOptions, "Keep 15/15/15",                  prefs.getBoolean("keep_hundo",            true));
        keepDynamax         = subCheck(transferOptions, "Keep Dynamax",                   prefs.getBoolean("keep_dynamax",          true));
        keepHatched         = subCheck(transferOptions, "Keep hatched",                   prefs.getBoolean("keep_hatched",          true));
        keepBackground      = subCheck(transferOptions, "Keep background",                prefs.getBoolean("keep_background",       true));
        keepShinyBackground = subCheck(transferOptions, "Keep shiny + background",        prefs.getBoolean("keep_shiny_background", false));
        keepFieldResearch   = subCheck(transferOptions, "Keep field research encounters", prefs.getBoolean("keep_field_research",   true));
        keepNundo           = subCheck(transferOptions, "Keep 0/0/0 (nundo)",             prefs.getBoolean("keep_nundo",            false));
        transfer.setOnCheckedChangeListener((v, checked) -> transferOptions.setVisibility(checked ? View.VISIBLE : View.GONE));
        transferOptions.setVisibility(transfer.isChecked() ? View.VISIBLE : View.GONE);

        space(page, 8);

        // ── Inventory Settings ────────────────────────────────────────────────
        LinearLayout inventoryBody = accordion(page, "Inventory Settings", "section_inventory");
        showIvNames            = toggleRow(inventoryBody, "Show IV names",            prefs.getBoolean("show_iv_names",            true));
        divider(inventoryBody);
        extendedManualTransfer = toggleRow(inventoryBody, "Extended manual transfer", prefs.getBoolean("extended_manual_transfer", false));
        divider(inventoryBody);
        clean = toggleRow(inventoryBody, "Clean item bag", prefs.getBoolean("clean", false));
        cleanOptions = column();
        cleanOptions.setPadding(dp(14), dp(4), dp(14), dp(8));
        inventoryBody.addView(cleanOptions);
        buildItemRows(cleanOptions, EnginePolicy.parseLimits(prefs.getString("discard_limits", EnginePolicy.DEFAULT_LIMITS)));
        clean.setOnCheckedChangeListener((view, checked) -> cleanOptions.setVisibility(checked ? View.VISIBLE : View.GONE));
        cleanOptions.setVisibility(clean.isChecked() ? View.VISIBLE : View.GONE);

        space(page, 8);

        // ── Extra Settings ────────────────────────────────────────────────────
        LinearLayout extraBody = accordion(page, "Extra Settings", "section_extra");
        fastGiftOpening = toggleRow(extraBody, "Fast gift opening", prefs.getBoolean("fast_gift_opening", true));
        divider(extraBody);
        hideTrainerName = toggleRow(extraBody, "Hide trainer name", prefs.getBoolean("hide_trainer_name", true));
        divider(extraBody);
        trainerName = textRow(extraBody, "Trainer name", prefs.getString("trainer_name", "KecleonBot"));
        divider(extraBody);
        resolveError12 = checkRow(extraBody, "Fix error 12",                  prefs.getBoolean("resolve_error_12", false));
        divider(extraBody);
        removeBuddy    = checkRow(extraBody, getString(R.string.remove_buddy), prefs.getBoolean("remove_buddy",     false));

        // ── Bottom action bar ─────────────────────────────────────────────────
        LinearLayout actionBar = column();
        actionBar.setBackgroundColor(HEADER);
        actionBar.setPadding(dp(12), dp(10), dp(12), dp(14));
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
        space(actionBar, 6);
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
        String newLimits = buildLimitsFromUI(false);
        FarmPolicy.validate(capture.isChecked(), spin.isChecked(), spinGyms.isChecked(), clean.isChecked(), transfer.isChecked(), newLimits);
        if (capture.isChecked() && !throwNormal.isChecked() && !throwNice.isChecked() && !throwGreat.isChecked() && !throwExcellent.isChecked())
            throw new IllegalArgumentException("Select at least one throw quality");
        if (capture.isChecked() && useBerries.isChecked() && buildBerryOrderFromUI().isEmpty())
            throw new IllegalArgumentException(getString(R.string.select_berries));
        persistSettings();
    }

    private void persistSettings() {
        String limitsStr;
        try { limitsStr = EnginePolicy.limitsKey(buildLimitsFromUI(true)); }
        catch (IllegalArgumentException ignored) { limitsStr = prefs.getString("discard_limits", EnginePolicy.DEFAULT_LIMITS); }
        String name = trainerName.getText().toString().trim();
        if (name.isEmpty()) name = "KecleonBot";
        SharedPreferences.Editor edit = prefs.edit().putString("package", EnginePolicy.GAME).putBoolean("internal_engine", true)
                .putBoolean("throw_normal", throwNormal.isChecked())
                .putBoolean("throw_nice", throwNice.isChecked())
                .putBoolean("throw_great", throwGreat.isChecked())
                .putBoolean("throw_excellent", throwExcellent.isChecked())
                .putBoolean("curveball", curveball.isChecked())
                .putBoolean("ar_mode", arMode.isChecked())
                .putString(PokemonSelection.KEY, onlyCatch)
                .putBoolean("use_berries", useBerries.isChecked()).putString("berry_order", buildBerryOrderFromUI())
                .putBoolean("golden_razz_shiny",       goldenRazzForShiny      != null && goldenRazzForShiny.isChecked())
                .putBoolean("golden_razz_hundo",       goldenRazzForHundo      != null && goldenRazzForHundo.isChecked())
                .putBoolean("golden_razz_galar_birds", goldenRazzForGalarBirds != null && goldenRazzForGalarBirds.isChecked())
                .putString("exclude_pokemon", excludePokemon)
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
                .putInt("max_transfers", 0).putString("discard_limits", limitsStr)
                .putInt("max_discarded_items", 0).putInt("max_throws", 0);
        try {
            edit.putInt("max_catches", read(maxCatches, 1, Integer.MAX_VALUE, getString(R.string.maximum_catches)))
                    .putInt("max_spins", read(maxSpins, 1, Integer.MAX_VALUE, getString(R.string.maximum_spins)))
                    .putInt("spin_delay", read(spinDelay, 100, 3000, "Spin delay"))
                    .putInt("catch_delay", read(catchDelay, 100, 3000, "Catch delay"));
        } catch (IllegalArgumentException ignored) { }
        edit.apply();
    }

    private void saveDraft() {
        if (!FarmService.isBusy() && !starting && prefs != null && capture != null) persistSettings();
    }

    private void updatePokemonLabel() {
        int count = PokemonSelection.parse(onlyCatch).size();
        pokemonSelector.setText(count == 0 ? getString(R.string.only_catch_all)
                : getResources().getQuantityString(R.plurals.only_catch_count, count, count));
    }

    private void updateExcludeLabel() {
        int count = PokemonSelection.parse(excludePokemon).size();
        excludeSelector.setText(count == 0 ? getString(R.string.exclude_pokemon_none)
                : getResources().getQuantityString(R.plurals.exclude_pokemon_count, count, count));
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
        int statusColor = !block.isEmpty() ? Color.rgb(255, 170, 80) : running ? GREEN : DIM;
        status.setTextColor(statusColor);
        statusDot.setBackground(shape(statusColor, 99));

        for (View setting : settings) { setting.setEnabled(!busy); setting.setAlpha(busy ? 0.5f : 1); }

        if (closing) {
            start.setText("Saving logs…");
            start.setBackground(shape(SURFACE, 14));
            start.setTextColor(MUTED);
        } else if (running) {
            start.setText("Stop");
            start.setBackground(shape(RED, 14));
            start.setTextColor(TEXT);
        } else {
            start.setText("Start");
            start.setBackground(shape(ACCENT, 14));
            start.setTextColor(TEXT);
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

    private void buildItemRows(LinearLayout parent, java.util.Map<String, Integer> current) {
        String lastGroup = null;
        for (ItemCatalog.Entry entry : ItemCatalog.ENTRIES) {
            if (!entry.discardable()) continue;
            if (!entry.group().equals(lastGroup)) {
                lastGroup = entry.group();
                TextView groupLabel = text(entry.group(), 11, DIM);
                groupLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                groupLabel.setPadding(0, dp(6), 0, dp(2));
                parent.addView(groupLabel);
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(40));
            CheckBox cb = new CheckBox(this);
            cb.setChecked(current.containsKey(entry.name()));
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
            row.addView(cb);
            TextView lbl = text(entry.label(), 13, MUTED);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMarginStart(dp(4));
            row.addView(lbl, lp);
            EditText amount = new EditText(this);
            amount.setInputType(InputType.TYPE_CLASS_NUMBER);
            amount.setSingleLine(true);
            amount.setTextColor(TEXT);
            amount.setTextSize(13);
            amount.setText(String.valueOf(current.getOrDefault(entry.name(), 0)));
            amount.setContentDescription("Keep " + entry.label());
            amount.setBackground(shape(Color.argb(30, 255, 255, 255), 8));
            amount.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            amount.setPadding(dp(4), dp(4), dp(4), dp(4));
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(70), dp(36));
            ap.setMarginStart(dp(8));
            row.addView(amount, ap);
            parent.addView(row);
            itemChecks.put(entry.name(), cb);
            itemAmounts.put(entry.name(), amount);
            settings.add(cb);
            settings.add(amount);
            cb.setOnCheckedChangeListener((v, checked) -> { amount.setEnabled(checked); amount.setAlpha(checked ? 1f : 0.4f); });
            amount.setEnabled(cb.isChecked());
            amount.setAlpha(cb.isChecked() ? 1f : 0.4f);
        }
    }

    private String buildLimitsFromUI(boolean lenient) {
        StringBuilder sb = new StringBuilder();
        for (ItemCatalog.Entry entry : ItemCatalog.ENTRIES) {
            if (!entry.discardable()) continue;
            CheckBox cb = itemChecks.get(entry.name());
            EditText et = itemAmounts.get(entry.name());
            if (cb == null || et == null || !cb.isChecked()) continue;
            int amount;
            try { amount = FarmPolicy.integer(et.getText().toString(), 0, 10000, entry.label()); }
            catch (IllegalArgumentException e) { if (!lenient) throw e; amount = 0; }
            if (sb.length() > 0) sb.append(',');
            sb.append(entry.name()).append('=').append(amount);
        }
        return sb.toString();
    }

    private String buildBerryOrderFromUI() {
        List<String> result = new ArrayList<>();
        if (berryRazz        != null && berryRazz.isChecked())        result.add("RazzBerry");
        if (berryPinap       != null && berryPinap.isChecked())       result.add("PinapBerry");
        if (berryNanab       != null && berryNanab.isChecked())       result.add("NanabBerry");
        if (berryGoldenRazz  != null && berryGoldenRazz.isChecked())  result.add("GoldenRazzBerry");
        if (berryGoldenPinap != null && berryGoldenPinap.isChecked()) result.add("GoldenPinapBerry");
        return String.join(",", result);
    }

    // item key → { display label, pref camelCase key }
    // ── UI helpers ────────────────────────────────────────────────────────────

    private LinearLayout accordion(LinearLayout parent, String title, String prefKey) {
        boolean expanded = prefs.getBoolean(prefKey, true);
        LinearLayout wrapper = column();
        wrapper.setBackground(shape(SURFACE, 14));
        wrapper.setClipToOutline(true);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(46));
        header.setPadding(dp(16), dp(10), dp(16), dp(10));
        header.setClickable(true);
        header.setFocusable(true);
        TextView titleView = text(title, 15, TEXT);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chevron = text(expanded ? "▾" : "▸", 16, ACCENT);
        header.addView(chevron);
        wrapper.addView(header);
        View div = new View(this);
        div.setBackgroundColor(BORDER);
        div.setVisibility(expanded ? View.VISIBLE : View.GONE);
        wrapper.addView(div, new LinearLayout.LayoutParams(-1, 1));
        LinearLayout body = column();
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        wrapper.addView(body);
        header.setOnClickListener(v -> {
            boolean open = body.getVisibility() != View.VISIBLE;
            body.setVisibility(open ? View.VISIBLE : View.GONE);
            div.setVisibility(open ? View.VISIBLE : View.GONE);
            chevron.setText(open ? "▾" : "▸");
            prefs.edit().putBoolean(prefKey, open).apply();
        });
        parent.addView(wrapper);
        return body;
    }

    private Switch toggleRow(LinearLayout parent, String label, boolean value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(46));
        row.setPadding(dp(14), dp(8), dp(10), dp(8));
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
        row.setMinimumHeight(dp(46));
        row.setPadding(dp(14), dp(8), dp(14), dp(8));
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
        row.setMinimumHeight(dp(38));
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
        tile.setPadding(dp(10), dp(8), dp(10), dp(8));
        TextView value = text("0", 20, TEXT);
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
        btn.setBackground(shape(ACCENT, 14));
        btn.setMinHeight(dp(50));
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
