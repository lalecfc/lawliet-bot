package modules.schedulers;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import commands.Command;
import commands.CommandManager;
import commands.listeners.OnAlertListener;
import commands.runnables.utilitycategory.AlertsCommand;
import core.*;
import core.cache.ServerPatreonBoostCache;
import core.components.ActionRows;
import core.utils.EmbedUtil;
import core.utils.ExceptionUtil;
import core.utils.TimeUtil;
import mysql.modules.tracker.DBTracker;
import mysql.modules.tracker.TrackerData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.internal.utils.concurrent.CountingThreadFactory;

public class AlertScheduler extends Startable {

    private static final AlertScheduler ourInstance = new AlertScheduler();

    public static AlertScheduler getInstance() {
        return ourInstance;
    }

    private AlertScheduler() {
    }

    private final ScheduledExecutorService executorService =
            Executors.newScheduledThreadPool(3, new CountingThreadFactory(() -> "Main", "Alerts", false));

    @Override
    protected void run() {
        try {
            DBTracker.getInstance().retrieveAll().forEach(this::loadAlert);
        } catch (Throwable e) {
            MainLogger.get().error("Could not start alerts", e);
        }
    }

    public void loadAlert(TrackerData slot) {
        loadAlert(slot.getGuildId(), slot.hashCode(), slot.getNextRequest());
    }

    public void loadAlert(long guildId, int hash, Instant due) {
        long millis = TimeUtil.getMillisBetweenInstants(Instant.now(), due);
        executorService.schedule(() -> {
            CustomObservableMap<Integer, TrackerData> map = DBTracker.getInstance().retrieve(guildId);
            if (map.containsKey(hash)) {
                TrackerData slot = map.get(hash);
                try(AsyncTimer asyncTimer = new AsyncTimer(Duration.ofMinutes(5))) {
                    asyncTimer.setTimeOutListener(t -> {
                        t.interrupt();
                        MainLogger.get().error("Alert stuck: {} with key {}", slot.getCommandTrigger(), slot.getCommandKey(), ExceptionUtil.generateForStack(t));
                    });

                    if (slot.isActive() && manageAlert(slot)) {
                        loadAlert(slot);
                    }
                } catch (InterruptedException e) {
                    MainLogger.get().error("Interrupted", e);
                    loadAlert(slot);
                }
            }
        }, millis, TimeUnit.MILLISECONDS);
    }

    private boolean manageAlert(TrackerData slot) {
        Instant minInstant = Instant.now().plus(1, ChronoUnit.MINUTES);

        try {
            processAlert(slot);
        } catch (Throwable throwable) {
            MainLogger.get().error("Error in tracker \"{}\" with key \"{}\"", slot.getCommandTrigger(), slot.getCommandKey(), throwable);
            minInstant = Instant.now().plus(10, ChronoUnit.MINUTES);
        }

        if (slot.isActive()) {
            if (minInstant.isAfter(slot.getNextRequest())) {
                slot.setNextRequest(minInstant);
            }
            return true;
        }

        return false;
    }

    private void processAlert(TrackerData slot) throws Throwable {
        Optional<Command> commandOpt = CommandManager.createCommandByTrigger(slot.getCommandTrigger(), slot.getGuildData().getLocale(), slot.getGuildData().getPrefix());
        if (commandOpt.isEmpty()) {
            MainLogger.get().error("Invalid alert for command: {}", slot.getCommandTrigger());
            slot.delete();
            return;
        }

        OnAlertListener command = (OnAlertListener) commandOpt.get();
        Optional<TextChannel> channelOpt = slot.getTextChannel();
        if (channelOpt.isPresent()) {
            if (PermissionCheckRuntime.getInstance().botHasPermission(((Command) command).getLocale(), AlertsCommand.class, channelOpt.get(), Permission.MESSAGE_READ, Permission.MESSAGE_WRITE, Permission.MESSAGE_EMBED_LINKS)) {
                if (checkNSFW(slot, channelOpt.get(), (Command) command) ||
                        checkPatreon(slot, channelOpt.get(), (Command) command)
                ) {
                    return;
                }

                switch (command.onTrackerRequest(slot)) {
                    case STOP:
                        slot.stop();
                        break;

                    case STOP_AND_DELETE:
                        slot.delete();
                        break;

                    case STOP_AND_SAVE:
                        slot.stop();
                        slot.save();
                        break;

                    case CONTINUE:
                        break;

                    case CONTINUE_AND_SAVE:
                        slot.save();
                        break;
                }
            }
        } else {
            if (slot.getGuild().isPresent()) {
                slot.delete();
            } else {
                slot.setNextRequest(Instant.now().plus(10, ChronoUnit.MINUTES));
            }
        }
    }

    private boolean checkNSFW(TrackerData slot, TextChannel channel, Command command) {
        if (command.getCommandProperties().nsfw() && !channel.isNSFW()) {
            EmbedBuilder eb = EmbedFactory.getNSFWBlockEmbed(command.getLocale());
            EmbedUtil.addTrackerRemoveLog(eb, command.getLocale());
            channel.sendMessageEmbeds(eb.build())
                    .setActionRows(ActionRows.of(EmbedFactory.getNSFWBlockButton(command.getLocale())))
                    .complete();
            slot.delete();
            return true;
        }
        return false;
    }

    private boolean checkPatreon(TrackerData slot, TextChannel channel, Command command) {
        if (command.getCommandProperties().patreonRequired() &&
                !ServerPatreonBoostCache.getInstance().get(channel.getGuild().getIdLong())
        ) {
            EmbedBuilder eb = EmbedFactory.getPatreonBlockEmbed(command.getLocale());
            EmbedUtil.addTrackerRemoveLog(eb, command.getLocale());
            channel.sendMessageEmbeds(eb.build()).complete();
            slot.delete();
            return true;
        }
        return false;
    }

}
