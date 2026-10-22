package dev.rootcatch.automation;

import android.content.Context;
import android.content.SharedPreferences;

public final class AutomationConfig {
    public static final String PREFS = "rootcatch";

    public final String gamePackage;
    public final boolean autoCatch;
    public final java.util.List<Integer> onlyCatch;
    public final boolean autoSpin;
    public final boolean spinGyms;
    public final boolean removeBuddy;
    public final boolean cleanBag;
    public final int actionDelayMs;
    public final int spinDelayMs;
    public final int catchDelayMs;
    public final int maxCatches;
    public final int maxSpins;
    public final int maxThrows;
    public final java.util.List<String> throwQualities;
    public final boolean curveball;
    public final boolean arMode;
    public final boolean keepNundo;
    public final boolean useBerries;
    public final java.util.List<String> berryOrder;
    public final boolean skipInterruptions;
    public final boolean fieldResearch;
    public final boolean discardUnsupportedResearch;
    public final boolean showIvNames;
    public final boolean extendedManualTransfer;
    public final boolean fastGiftOpening;
    public final boolean hideTrainerName;
    public final String trainerLabel;
    public final boolean internalEngine;
    public final String discardLimits;
    public final int maxDiscardedItems;

    private AutomationConfig(String gamePackage,
                             boolean autoCatch, java.util.List<Integer> onlyCatch, boolean autoSpin, boolean removeBuddy,
                             boolean cleanBag, boolean spinGyms, int actionDelayMs, int spinDelayMs, int catchDelayMs, int maxCatches, int maxSpins,
                             int maxThrows, java.util.List<String> throwQualities, boolean curveball, boolean arMode, boolean keepNundo, boolean useBerries, java.util.List<String> berryOrder,
                             boolean showIvNames, boolean extendedManualTransfer, boolean fastGiftOpening, boolean hideTrainerName, String trainerLabel, boolean internalEngine,
                             String discardLimits, int maxDiscardedItems) {
        this.gamePackage = gamePackage;
        this.autoCatch = autoCatch;
        this.onlyCatch = onlyCatch;
        this.autoSpin = autoSpin;
        this.removeBuddy = removeBuddy;
        this.cleanBag = cleanBag;
        this.spinGyms = spinGyms;
        this.actionDelayMs = actionDelayMs;
        this.spinDelayMs = spinDelayMs;
        this.catchDelayMs = catchDelayMs;
        this.maxCatches = maxCatches;
        this.maxSpins = maxSpins;
        this.maxThrows = maxThrows;
        this.throwQualities = throwQualities.isEmpty() ? java.util.Collections.singletonList("EXCELLENT") : throwQualities;
        this.curveball = curveball;
        this.arMode = arMode;
        this.keepNundo = keepNundo;
        this.useBerries = useBerries;
        this.berryOrder = berryOrder;
        this.skipInterruptions = true;
        this.fieldResearch = true;
        this.discardUnsupportedResearch = true;
        this.showIvNames = showIvNames;
        this.extendedManualTransfer = extendedManualTransfer;
        this.fastGiftOpening = fastGiftOpening;
        this.hideTrainerName = hideTrainerName;
        this.trainerLabel = (trainerLabel != null && !trainerLabel.trim().isEmpty()) ? trainerLabel.trim() : "KecleonBot";
        this.internalEngine = internalEngine;
        this.discardLimits = discardLimits;
        this.maxDiscardedItems = maxDiscardedItems;
    }

    public static AutomationConfig load(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new AutomationConfig(
                p.getString("package", "com.nianticlabs.pokemongo"),
                p.getBoolean("catch", true),
                PokemonSelection.parse(p.getString(PokemonSelection.KEY, "")),
                p.getBoolean("spin", true),
                p.getBoolean("remove_buddy", false),
                p.getBoolean("clean", false),
                p.getBoolean("spin_gyms", false),
                clamp(p.getInt("delay", 150), 100, 3000),
                clamp(p.getInt("spin_delay", p.getInt("delay", 150)), 100, 3000),
                clamp(p.getInt("catch_delay", p.getInt("delay", 150)), 100, 3000),
                SessionLimits.savedLimit(p.getInt("max_catches", SessionLimits.DEFAULT_CATCHES), SessionLimits.DEFAULT_CATCHES),
                SessionLimits.savedLimit(p.getInt("max_spins", SessionLimits.DEFAULT_SPINS), SessionLimits.DEFAULT_SPINS),
                0,
                buildThrowQualities(p),
                p.getBoolean("curveball", true),
                p.getBoolean("ar_mode", false),
                p.getBoolean("keep_nundo", false),
                p.getBoolean("use_berries", true),
                BerryOptions.parse(BerryOptions.fromSavedValue(p.getString("berry_order", BerryOptions.DEFAULT_ORDER))),
                p.getBoolean("show_iv_names", true),
                p.getBoolean("extended_manual_transfer", false),
                p.getBoolean("fast_gift_opening", true),
                p.getBoolean("hide_trainer_name", true),
                p.getString("trainer_name", "KecleonBot"),
                p.getBoolean("internal_engine", true),
                p.getString("discard_limits", dev.rootcatch.nativeengine.EnginePolicy.DEFAULT_LIMITS),
                0
        );
    }

    private static java.util.List<String> buildThrowQualities(SharedPreferences p) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (p.getBoolean("throw_normal", false))    list.add("NORMAL");
        if (p.getBoolean("throw_nice", false))      list.add("NICE");
        if (p.getBoolean("throw_great", false))     list.add("GREAT");
        if (p.getBoolean("throw_excellent", true))  list.add("EXCELLENT");
        if (list.isEmpty()) list.add("EXCELLENT");
        return list;
    }
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
