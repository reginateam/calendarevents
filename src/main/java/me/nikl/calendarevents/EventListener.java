package me.nikl.calendarevents;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * @author Niklas Eicker
 * <p>
 * Plugin intern listener for the event CalendarEvent
 * load stuff to do from the config and do whatever was configured on the events
 */
public class EventListener implements Listener {
    private CalendarEvents plugin;
    private Map<String, ArrayList<String>> commands;
    private Map<String, List<CommandAction>> commandsWithPerm;
    private Map<String, String> broadcast;
    private Map<String, BroadcastWithPerm> broadCastWithPerm;
    private Map<String, ActionBar> actionBars;
    private Map<String, Title> titles;
    private Set<String> labels;
    private boolean parseMessagesForPlaceholders;

    public EventListener(CalendarEvents plugin, Set<String> labels) {
        this.plugin = plugin;
        this.labels = labels;
        loadListener();
    }

    /**
     * Load configured actions on events
     */
    private void loadListener() {
        this.parseMessagesForPlaceholders = plugin.getPlaceholderHook() != null;
        this.commands = new HashMap<>();
        this.commandsWithPerm = new HashMap<>();
        this.broadcast = new HashMap<>();
        this.broadCastWithPerm = new HashMap<>();
        this.actionBars = new HashMap<>();
        this.titles = new HashMap<>();
        FileConfiguration config = plugin.getConfig();
        if (!config.isConfigurationSection("listener")) return;
        ConfigurationSection listener = config.getConfigurationSection("listener");
        for (String label : listener.getKeys(false)) {

            // check whether the label is a configured event
            if (!labels.contains(label)) {
                Bukkit.getLogger().log(Level.WARNING, "[CalendarEvents] " + "Section 'listener' contains actions for a not scheduled event: " + label);
                continue;
            }

            if (listener.isList(label + ".commands")) {
                ArrayList<String> commands = new ArrayList<>(listener.getStringList(label + ".commands"));
                for (int i = 0; i < commands.size(); i++) {
                    commands.set(i, ChatColor.translateAlternateColorCodes('&', commands.get(i)));
                }
                this.commands.put(label, commands);
            }

            if (listener.isConfigurationSection(label + ".commandsWithPerm")) {
                ConfigurationSection commandWithPerm = listener.getConfigurationSection(label + ".commandsWithPerm");
                for (String command : commandWithPerm.getKeys(false)) {
                    if (!commandWithPerm.isConfigurationSection(command)) continue;
                    if (!commandWithPerm.isList(command + ".commands")) continue;
                    if (!commandWithPerm.isString(command + ".perm")) continue;
                    this.commandsWithPerm.putIfAbsent(label, new ArrayList<>());
                    this.commandsWithPerm.get(label).add(new CommandAction(commandWithPerm.getString(command + ".perm"), commandWithPerm.getStringList(command + ".commands")));
                }
            }

            if (listener.isString(label + ".broadcast")) {
                broadcast.put(label, ChatColor.translateAlternateColorCodes('&', listener.getString(label + ".broadcast")));
            }

            if (listener.isConfigurationSection(label + ".broadcastWithPerm") && listener.isString(label + ".broadcastWithPerm" + ".perm") && listener.isString(label + ".broadcastWithPerm" + ".broadcast")) {
                broadCastWithPerm.put(label, new BroadcastWithPerm(listener.getString(label + ".broadcastWithPerm" + ".perm"), ChatColor.translateAlternateColorCodes('&', listener.getString(label + ".broadcastWithPerm" + ".broadcast"))));
            }

            if (listener.isConfigurationSection(label + ".actionbar") && listener.isString(label + ".actionbar" + ".bar")) {
                actionBars.put(label, new ActionBar(listener.getString(label + ".actionbar" + ".perm"), ChatColor.translateAlternateColorCodes('&', listener.getString(label + ".actionbar" + ".bar"))));
            }

            if (listener.isConfigurationSection(label + ".title") && listener.isString(label + ".title" + ".title") && listener.isString(label + ".title" + ".subTitle")) {
                titles.put(label,
                        new Title(
                                listener.getString(label + ".title" + ".perm"),
                                ChatColor.translateAlternateColorCodes('&', listener.getString(label + ".title" + ".title")),
                                ChatColor.translateAlternateColorCodes('&', listener.getString(label + ".title" + ".subTitle"))
                        ).setTicksToDisplay(listener.getInt(label + ".title" + ".ticksToDisplay", 10))
                );
            }
        }
    }

    @EventHandler
    public void onCalendarEvent(CalendarEvent event) {
        CalendarEvents.debug("[Listener] event called: " + event.getLabels().toString());
        CalendarEvents.debug("[Listener] called at: " + event.getTime());
        // go through all labels in the listener section
        for (String label : event.getLabels()) {

            // check for commands on the event
            if (commands.get(label) != null && !commands.get(label).isEmpty()) {
                for (String cmd : commands.get(label)) {
                    cmd = setEventPlaceholders(cmd, event);
                    if (cmd.contains("%allOnline%")) {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), placeholders(player, cmd.replaceAll("%allOnline%", player.getName()).replaceAll("%player%", player.getName())));
                        }
                    } else if (cmd.contains("%allOffline%")) {
                        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                            if (!player.hasPlayedBefore() || player.isOnline()) {
                                continue;
                            }
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), placeholders(null, cmd.replaceAll("%allOffline%", player.getName()).replaceAll("%player%", player.getName())));
                        }
                    } else if (cmd.contains("%allPlayers%")) {
                        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                            if (!player.hasPlayedBefore()) {
                                continue;
                            }
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), placeholders(null, cmd.replaceAll("%allPlayers%", player.getName()).replaceAll("%player%", player.getName())));
                        }
                    } else {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), placeholders(null, cmd));
                    }
                }
            }

            // check for commands with permissions
            if (commandsWithPerm.get(label) != null && !commandsWithPerm.get(label).isEmpty()) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    for (CommandAction commandAction : commandsWithPerm.get(label)) {
                        if (!player.hasPermission(commandAction.perm)) continue;
                        for (String cmd : commandAction.commands) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), placeholders(player, cmd
                                    .replace("%allOnline%", player.getName())
                                    .replace("%player%", player.getName())
                                    .replace("%perm%", commandAction.perm)));
                        }
                    }
                }
            }

            // check for broadcast
            if (broadcast.get(label) != null) {
                Bukkit.broadcastMessage(setEventPlaceholders(placeholders(null, broadcast.get(label)), event));
            }

            // check for broadcast with permission node
            if (broadCastWithPerm.get(label) != null) {
                BroadcastWithPerm broadcastWithPerm = this.broadCastWithPerm.get(label);
                Bukkit.broadcast(setEventPlaceholders(placeholders(null, broadcastWithPerm.message), event), broadcastWithPerm.perm);
            }

            // check for actionbar
            if (actionBars.get(label) != null) {
                ActionBar actionBar = this.actionBars.get(label);
                String bar = setEventPlaceholders(actionBar.bar, event);
                if (actionBar.perm == null || actionBar.perm.equals("")) {
                    // no permission => send to every player
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendActionBar(Component.text(placeholders(player, bar).replace("%player%", player.getName())));
                    }
                } else {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        // check for permission node first
                        if (!player.hasPermission(actionBar.perm)) continue;
                        player.sendActionBar(Component.text(placeholders(player, bar).replace("%player%", player.getName())));
                    }
                }
            }

            // check for title
            if (titles.get(label) != null) {
                Title title = this.titles.get(label);
                String titleString = setEventPlaceholders(title.title, event);
                String subTitle = setEventPlaceholders(title.subTitle, event);
                if (title.perm == null || title.perm.equals("")) {
                    // no permission => send to every player
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.showTitle(net.kyori.adventure.title.Title.title(
                                Component.text((placeholders(player, titleString).replace("%player%", player.getName()))),
                                Component.text(placeholders(player, subTitle).replace("%player%", player.getName())),
                                net.kyori.adventure.title.Title.Times.times(
                                        Util.ticksToSeconds(title.ticksToDisplay / 10),
                                        Util.ticksToSeconds(title.ticksToDisplay),
                                        Util.ticksToSeconds(title.ticksToDisplay / 10)
                                )
                        ));
                    }
                } else {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        // check for permission node first
                        if (!player.hasPermission(title.perm)) continue;
                        player.showTitle(net.kyori.adventure.title.Title.title(
                                Component.text((placeholders(player, titleString).replace("%player%", player.getName()))),
                                Component.text(placeholders(player, subTitle).replace("%player%", player.getName())),
                                net.kyori.adventure.title.Title.Times.times(
                                        Util.ticksToSeconds(title.ticksToDisplay / 10),
                                        Util.ticksToSeconds(title.ticksToDisplay),
                                        Util.ticksToSeconds(title.ticksToDisplay / 10)
                                )
                        ));
                    }
                }
            }
        }
    }

    private String setEventPlaceholders(String message, CalendarEvent event) {
        return message.replace("%time%", event.getTime()).replace("%day%", event.getDay()).replace("%month%", event.getMonth());
    }

    private String placeholders(Player player, String message) {
        return this.parseMessagesForPlaceholders ? plugin.getPlaceholderHook().setPlaceholders(player, message) : message;
    }


    /**
     * Store broadcast info
     */
    private class BroadcastWithPerm {
        String perm, message;

        private BroadcastWithPerm(String perm, String message) {
            this.perm = perm;
            this.message = message;
        }
    }

    /**
     * Store actionbar info
     */
    private class ActionBar {
        String perm, bar;

        private ActionBar(String perm, String bar) {
            this.perm = perm;
            this.bar = bar;
        }
    }

    /**
     * Store title info
     */
    private class Title {
        String perm, title, subTitle;
        int ticksToDisplay;

        private Title(String perm, String title, String subTitle) {
            this.perm = perm;
            this.title = title;
            this.subTitle = subTitle;
        }

        private Title setTicksToDisplay(int ticksToDisplay) {
            if (ticksToDisplay < 1) ticksToDisplay = 1;
            this.ticksToDisplay = ticksToDisplay;
            return this;
        }
    }

    /**
     * Store CommandWithPerm info
     */
    private class CommandAction {
        String perm;
        List<String> commands;

        private CommandAction(String perm, List<String> commands) {
            this.perm = perm;
            this.commands = commands;
        }
    }
}
