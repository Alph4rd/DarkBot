package com.github.manolo8.darkbot;

import com.github.manolo8.darkbot.backpage.BackpageManager;
import com.github.manolo8.darkbot.config.Config;
import com.github.manolo8.darkbot.config.ConfigManager;
import com.github.manolo8.darkbot.config.utils.ByteArrayToBase64TypeAdapter;
import com.github.manolo8.darkbot.config.utils.ConditionTypeAdapterFactory;
import com.github.manolo8.darkbot.config.utils.SpecialTypeAdapter;
import com.github.manolo8.darkbot.core.BotInstaller;
import com.github.manolo8.darkbot.core.IDarkBotAPI;
import com.github.manolo8.darkbot.core.itf.Behaviour;
import com.github.manolo8.darkbot.core.itf.Configurable;
import com.github.manolo8.darkbot.core.itf.Module;
import com.github.manolo8.darkbot.core.manager.EffectManager;
import com.github.manolo8.darkbot.core.manager.FacadeManager;
import com.github.manolo8.darkbot.core.manager.GuiManager;
import com.github.manolo8.darkbot.core.manager.HeroManager;
import com.github.manolo8.darkbot.core.manager.MapManager;
import com.github.manolo8.darkbot.core.manager.PingManager;
import com.github.manolo8.darkbot.core.manager.RepairManager;
import com.github.manolo8.darkbot.core.manager.SettingsManager;
import com.github.manolo8.darkbot.core.manager.StarManager;
import com.github.manolo8.darkbot.core.manager.StatsManager;
import com.github.manolo8.darkbot.core.utils.Lazy;
import com.github.manolo8.darkbot.extensions.features.Feature;
import com.github.manolo8.darkbot.extensions.features.FeatureDefinition;
import com.github.manolo8.darkbot.extensions.features.FeatureRegistry;
import com.github.manolo8.darkbot.extensions.plugins.IssueHandler;
import com.github.manolo8.darkbot.extensions.plugins.PluginHandler;
import com.github.manolo8.darkbot.extensions.plugins.PluginListener;
import com.github.manolo8.darkbot.extensions.plugins.PluginUpdater;
import com.github.manolo8.darkbot.extensions.util.VerifierChecker;
import com.github.manolo8.darkbot.extensions.util.Version;
import com.github.manolo8.darkbot.gui.MainGui;
import com.github.manolo8.darkbot.gui.utils.Popups;
import com.github.manolo8.darkbot.modules.DisconnectModule;
import com.github.manolo8.darkbot.modules.DummyModule;
import com.github.manolo8.darkbot.modules.TemporalModule;
import com.github.manolo8.darkbot.utils.I18n;
import com.github.manolo8.darkbot.utils.RuntimeUtil;
import com.github.manolo8.darkbot.utils.StartupParams;
import com.github.manolo8.darkbot.utils.Time;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class Main extends Thread implements PluginListener {

    public static final Version VERSION      = new Version("1.13.17 beta 100");
    public static final Object UPDATE_LOCKER = new Object();
    public static final Gson GSON            = new GsonBuilder()
            .setPrettyPrinting()
            .setLenient()
            .disableHtmlEscaping()
            .registerTypeHierarchyAdapter(byte[].class, new ByteArrayToBase64TypeAdapter())
            .registerTypeAdapterFactory(new SpecialTypeAdapter())
            .registerTypeAdapterFactory(new ConditionTypeAdapterFactory())
            .create();

    public ConfigManager configManager = new ConfigManager();
    public Config config;
    public static IDarkBotAPI API;

    public final Lazy.Sync<Boolean> status      = new Lazy.Sync<>();
    public final Lazy.Sync<String> configChange = new Lazy.Sync<>();
    public final StarManager starManager;
    public final MapManager mapManager;
    public final SettingsManager settingsManager;
    public final FacadeManager facadeManager;
    public final HeroManager hero;
    public final EffectManager effectManager;
    public final GuiManager guiManager;
    public final StatsManager statsManager;
    public final PingManager pingManager;
    public final BackpageManager backpage;
    public final PluginHandler pluginHandler;
    public final PluginUpdater pluginUpdater;
    public final FeatureRegistry featureRegistry;
    public final RepairManager repairManager;

    private final MainGui form;
    private final BotInstaller botInstaller;

    public Module module;
    public long lastRefresh = System.currentTimeMillis();
    public double avgTick;
    public boolean tickingModule;

    private String moduleId;
    private List<Behaviour> behaviours = new ArrayList<>();
    private final List<Runnable> tasks = new ArrayList<>();

    private volatile boolean running;

    public Main(StartupParams params) {
        super("Main");
        config = configManager.loadConfig(params.getStartConfig());

        VerifierChecker.getAuthApi().setupAuth();

        this.starManager     = StarManager.getInstance();
        this.mapManager      = new MapManager(this);
        this.settingsManager = new SettingsManager(this);
        this.facadeManager   = new FacadeManager(this);
        this.hero            = new HeroManager(this);
        this.effectManager   = new EffectManager(this);
        this.guiManager      = new GuiManager(this);
        this.statsManager    = new StatsManager(this);
        this.pingManager     = new PingManager();
        this.pluginHandler   = new PluginHandler();
        this.pluginUpdater   = new PluginUpdater(this);
        this.backpage        = new BackpageManager(this);
        this.featureRegistry = new FeatureRegistry(this, pluginHandler);
        this.repairManager   = new RepairManager();

        this.botInstaller = new BotInstaller(
                settingsManager, facadeManager, effectManager, guiManager, mapManager,
                hero, statsManager, pingManager, repairManager);


        API = configManager.getAPI(params);
        API.setSize(config.BOT_SETTINGS.API_CONFIG.width, config.BOT_SETTINGS.API_CONFIG.height);

        this.botInstaller.invalid.add(value -> {
            if (!value) lastRefresh = System.currentTimeMillis();
        });

        this.status.add(this::onRunningToggle);
        this.configChange.add(this::setConfigInternal);

        this.pluginHandler.updatePluginsSync();
        this.pluginHandler.addListener(this);

        this.form = new MainGui(this);
        this.pluginUpdater.scheduleUpdateChecker();

        if (configManager.getConfigFailed())
            Popups.showMessageAsync("Error", I18n.get("bot.issue.config_load_failed"), JOptionPane.ERROR_MESSAGE);

        API.createWindow();
        if (params.getAutoStart()) setRunning(true);
        start();
    }

    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    public void run() {
        long time;

        while (true) {
            time = System.currentTimeMillis();

            try {
                tick();
            } catch (Exception e) {
                e.printStackTrace();
                Time.sleep(1000);
            }

            avgTick = ((avgTick * 9) + (System.currentTimeMillis() - time)) / 10;

            Time.sleepMax(time, botInstaller.invalid.get() ? 1000 :
                    Math.max(config.BOT_SETTINGS.OTHER.MIN_TICK, Math.min((int) (avgTick * 1.25), 100)));
        }
    }

    private void tick() {
        this.status.tick();
        checkModule();

        if (isInvalid()) tickingModule = false;
        else validTick();

        this.form.tick();
        this.configManager.saveChangedConfig();
        this.configChange.tick();

        processTasks();
    }

    private void processTasks() {
        Runnable[] tasks = new Runnable[0];
        synchronized (Main.UPDATE_LOCKER) {
            if (this.tasks.isEmpty()) return;

            tasks = this.tasks.toArray(tasks);
            this.tasks.clear();
        }
        for (Runnable task : tasks) {
            try {
                task.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isInvalid() {
        return botInstaller.isInvalid();
    }

    private void validTick() {
        settingsManager.tick();
        hero.tick();
        mapManager.tick();
        facadeManager.tick();
        effectManager.tick();
        guiManager.tick();
        statsManager.tick();
        repairManager.tick();

        tickingModule = running && guiManager.canTickModule();
        if (tickingModule) tickRunning();
        else tickLogic(false);

        if (!running && (!hero.hasTarget() || !mapManager.isTarget(hero.target))) {
            hero.setTarget(Stream.concat(mapManager.entities.ships.stream(), mapManager.entities.npcs.stream())
                    .filter(mapManager::isTarget)
                    .findFirst().orElse(null));
        }

        pingManager.tick();
    }

    private void tickRunning() {
        guiManager.pet.tick();
        guiManager.group.tick();
        checkRefresh();
        tickLogic(true);
    }

    private void tickLogic(boolean running) {
        synchronized (pluginHandler) {
            try {
                if (running) module.tickModule();
                else module.tickStopped();
            } catch (Throwable e) {
                FeatureDefinition<Module> modDef = featureRegistry.getFeatureDefinition(module);
                if (modDef != null) modDef.getIssues().addWarning("bot.issue.feature.failed_to_tick", IssueHandler.createDescription(e));
            }
            for (Behaviour behaviour : behaviours) {
                try {
                    if (running) behaviour.tickBehaviour();
                    else behaviour.tickStopped();
                } catch (Throwable e) {
                    featureRegistry.getFeatureDefinition(behaviour)
                            .getIssues()
                            .addFailure("bot.issue.feature.failed_to_tick", IssueHandler.createDescription(e));
                }
            }
        }
    }

    private void checkRefresh() {
        if (config.MISCELLANEOUS.REFRESH_TIME == 0 ||
                System.currentTimeMillis() - lastRefresh < config.MISCELLANEOUS.REFRESH_TIME * 60 * 1000) return;

        if (!module.canRefresh()) return;

        lastRefresh = System.currentTimeMillis();
        if (config.MISCELLANEOUS.PAUSE_FOR > 0) {
            System.out.println("Pausing (logging off): time arrived & module allows refresh");
            setModule(new DisconnectModule(config.MISCELLANEOUS.PAUSE_FOR * 60 * 1000L, I18n.get("module.disconnect.reason.break")));
        } else {
            System.out.println("Triggering refresh: time arrived & module allows refresh");
            API.handleRefresh();
        }
    }

    public <A extends Module> A setModule(A module) {
        return setModule(module, false);
    }

    private <A extends Module> A setModule(A module, boolean setConfig) {
        if (module != null) {
            module.install(this);
            if (setConfig) updateCustomConfig(module);
        }
        this.module = module;
        return module;
    }

    private <A extends Module> void updateCustomConfig(A module) {
        if (module instanceof Configurable) {
            String name = module.getClass().getAnnotation(Feature.class).name();
            form.setCustomConfig(name, config.CUSTOM_CONFIGS.get(module.getClass().getCanonicalName()));
        } else {
            form.setCustomConfig(null, null);
        }
    }

    @Override
    public void beforeLoad() {
        if (module != null) setModule(new DummyModule(), true);
    }

    @Override
    public void afterLoadComplete() {
        moduleId = "(none)";
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        if (this.running == running) return;
        status.send(running);
        this.running = running;
    }

    private void onRunningToggle(boolean running) {
        if (config.MISCELLANEOUS.RESET_REFRESH)
            lastRefresh = System.currentTimeMillis();
        if (running && module instanceof TemporalModule) {
            moduleId = "(none)";
        }

        if (running) hero.pet.clickable.setRadius(0);
        else hero.pet.clickable.reset();
    }

    public void setBehaviours(List<Behaviour> behaviours) {
        this.behaviours = behaviours;
    }

    private void checkModule() {
        if (module == null || !Objects.equals(moduleId, config.GENERAL.CURRENT_MODULE)) {
            Module module = featureRegistry.getFeature(moduleId = config.GENERAL.CURRENT_MODULE, Module.class)
                .orElseGet(() -> {
                    String name = moduleId.substring(moduleId.lastIndexOf(".") + 1);
                    Popups.showMessageAsync("Error", I18n.get("bot.issue.module_load_failed", name), JOptionPane.ERROR_MESSAGE);
                    return new DummyModule();
                });
            setModule(module, true);
        }
    }

    public void setConfig(String config) {
        this.configChange.send(config);
    }

    public MainGui getGui() {
        return form;
    }

    private void setConfigInternal(String config) {
        if (configManager.getConfigName().equals(config)) return;
        try {
            SwingUtilities.invokeAndWait(() -> {
                this.config = configManager.loadConfig(config);
                mapManager.updateAreas(true);
                pluginHandler.updateConfig(); // Get plugins to update what features are enabled
                featureRegistry.updateConfig(); // Update the features & configurables
                form.updateConfiguration(); // Rebuild config gui
                setModule(null, true);
            });
        } catch (InterruptedException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void addTask(Runnable task) {
        synchronized (Main.UPDATE_LOCKER) {
            this.tasks.add(task);
        }
    }

}
