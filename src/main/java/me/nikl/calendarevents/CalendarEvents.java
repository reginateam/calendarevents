package me.nikl.calendarevents;

import com.google.common.base.Charsets;
import me.nikl.calendarevents.external.PlaceholderHook;
import me.nikl.calendarevents.scheduling.Timer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;

/**
 * @author Niklas Eicker
 *
 * Plugin class
 */
public class CalendarEvents extends JavaPlugin {
    private static final boolean DEBUG = false;
    private Timer timer;
    private EventsManager eventsManager;
    private File configurationFile;
    private FileConfiguration configuration;
    private PlaceholderHook placeholderHook;

    public static void debug(String message) {
        if (DEBUG) Bukkit.getLogger().info(message);
    }

    @Override
    public void onEnable() {
        reloadConfiguration();
        Settings.loadSettingsFromConfig(configuration);
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.placeholderHook = new PlaceholderHook(this);
        }
        this.eventsManager = new EventsManager(this);
        this.timer = new Timer(this);
        this.getCommand("calendarevents").setExecutor(new Commands(this));
    }

    @Override
    public void onDisable() {
        if (this.timer != null) this.timer.cancel();
    }

    public void reloadConfiguration() {
        this.configurationFile = new File(this.getDataFolder().toString() + File.separatorChar + "config.yml");
        if (!configurationFile.exists()) {
            this.saveResource("config.yml", false);
        }
        try {
            this.configuration = YamlConfiguration.loadConfiguration(new InputStreamReader(new FileInputStream(configurationFile), Charsets.UTF_8));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void reload() {
        if (this.timer != null) this.timer.cancel();
        eventsManager.reload();
        getNewTimer();
    }

    public void getNewTimer() {
        this.timer = new Timer(this);
    }

    /**
     * Get the API instance to manipulate Events on runtime.
     *
     * @return API instance
     */
    public CalendarEventsApi getApi() {
        return this.eventsManager;
    }

    @Override
    public FileConfiguration getConfig() {
        return this.configuration;
    }

    public PlaceholderHook getPlaceholderHook() {
        return placeholderHook;
    }
}
