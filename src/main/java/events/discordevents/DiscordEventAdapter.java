package events.discordevents;

import java.util.*;
import core.*;
import events.discordevents.eventtypeabstracts.*;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.voice.VoiceChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.voice.update.VoiceChannelUpdateUserLimitEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdatePendingEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateBoostCountEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.api.events.http.HttpRequestEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionRemoveEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.events.user.UserActivityStartEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;

public class DiscordEventAdapter extends ListenerAdapter {

    private final HashMap<Class<?>, ArrayList<DiscordEventAbstract>> listenerMap;

    public DiscordEventAdapter() {
        listenerMap = new HashMap<>();

        Reflections reflections = new Reflections("events/discordevents");
        Set<Class<?>> annotated = reflections.getTypesAnnotatedWith(DiscordEvent.class);
        Set<Class<? extends DiscordEventAbstract>> listenerTypeAbstracts = reflections.getSubTypesOf(DiscordEventAbstract.class);

        annotated.stream()
                .map(clazz -> {
                    try {
                        return clazz.newInstance();
                    } catch (InstantiationException | IllegalAccessException e) {
                        MainLogger.get().error("Error when creating listener class", e);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .filter(obj -> obj instanceof DiscordEventAbstract)
                .map(obj -> (DiscordEventAbstract) obj)
                .forEach(listener -> putListener(listener, listenerTypeAbstracts));
    }

    private void putListener(DiscordEventAbstract listener, Set<Class<? extends DiscordEventAbstract>> listenerTypeAbstracts) {
        for (Class<?> clazz : listenerTypeAbstracts) {
            if (clazz.isInstance(listener)) {
                ArrayList<DiscordEventAbstract> listenerList = listenerMap.computeIfAbsent(clazz, k -> new ArrayList<>());
                listenerList.add(listener);
            }
        }
    }

    private ArrayList<DiscordEventAbstract> getListenerList(Class<? extends DiscordEventAbstract> clazz) {
        return listenerMap.computeIfAbsent(clazz, k -> new ArrayList<>());
    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        ShardManager.getInstance().initAssetIds(event.getJDA());
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> DiscordConnector.getInstance().onJDAJoin(event.getJDA()));
    }

    @Override
    public void onGuildMessageReceived(@NotNull GuildMessageReceivedEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> GuildMessageReceivedAbstract.onGuildMessageReceivedStatic(event, getListenerList(GuildMessageReceivedAbstract.class)));
    }

    @Override
    public void onPrivateMessageReceived(@NotNull PrivateMessageReceivedEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> PrivateMessageReceivedAbstract.onPrivateMessageReceivedStatic(event, getListenerList(PrivateMessageReceivedAbstract.class)));
    }

    @Override
    public void onGuildMessageUpdate(@NotNull GuildMessageUpdateEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> GuildMessageUpdateAbstract.onGuildMessageUpdateStatic(event, getListenerList(GuildMessageUpdateAbstract.class)));
    }

    @Override
    public void onGuildMessageDelete(@NotNull GuildMessageDeleteEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> GuildMessageDeleteAbstract.onGuildMessageDeleteStatic(event, getListenerList(GuildMessageDeleteAbstract.class)));
    }

    @Override
    public void onGuildMessageReactionAdd(@NotNull GuildMessageReactionAddEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> GuildMessageReactionAddAbstract.onGuildMessageReactionAddStatic(event, getListenerList(GuildMessageReactionAddAbstract.class)));
    }

    @Override
    public void onGuildMessageReactionRemove(@NotNull GuildMessageReactionRemoveEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> GuildMessageReactionRemoveAbstract.onGuildMessageReactionRemoveStatic(event, getListenerList(GuildMessageReactionRemoveAbstract.class)));
    }

    @Override
    public void onVoiceChannelDelete(@NotNull VoiceChannelDeleteEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> VoiceChannelDeleteAbstract.onVoiceChannelDeleteStatic(event, getListenerList(VoiceChannelDeleteAbstract.class)));
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> GuildJoinAbstract.onGuildJoinStatic(event, getListenerList(GuildJoinAbstract.class)));
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> GuildLeaveAbstract.onGuildLeaveStatic(event, getListenerList(GuildLeaveAbstract.class)));
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> GuildMemberJoinAbstract.onGuildMemberJoinStatic(event, getListenerList(GuildMemberJoinAbstract.class)));
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> GuildMemberRemoveAbstract.onGuildMemberRemoveStatic(event, getListenerList(GuildMemberRemoveAbstract.class)));
    }

    @Override
    public void onGuildUnban(@NotNull GuildUnbanEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> GuildUnbanAbstract.onGuildUnbanStatic(event, getListenerList(GuildUnbanAbstract.class)));
    }

    @Override
    public void onTextChannelDelete(@NotNull TextChannelDeleteEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> TextChannelDeleteAbstract.onTextChannelDeleteStatic(event, getListenerList(TextChannelDeleteAbstract.class)));
    }

    @Override
    public void onVoiceChannelUpdateUserLimit(@NotNull VoiceChannelUpdateUserLimitEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> VoiceChannelUpdateUserLimitAbstract.onVoiceChannelUpdateUserLimitStatic(event, getListenerList(VoiceChannelUpdateUserLimitAbstract.class)));
    }

    @Override
    public void onGuildVoiceJoin(@NotNull GuildVoiceJoinEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> GuildVoiceJoinAbstract.onGuildVoiceJoinStatic(event, getListenerList(GuildVoiceJoinAbstract.class)));
    }

    @Override
    public void onGuildVoiceLeave(@NotNull GuildVoiceLeaveEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> GuildVoiceLeaveAbstract.onGuildVoiceLeaveStatic(event, getListenerList(GuildVoiceLeaveAbstract.class)));
    }

    @Override
    public void onGuildVoiceMove(@NotNull GuildVoiceMoveEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> GuildVoiceMoveAbstract.onGuildVoiceMoveStatic(event, getListenerList(GuildVoiceMoveAbstract.class)));
    }

    @Override
    public void onGuildMemberRoleAdd(@NotNull GuildMemberRoleAddEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> GuildMemberRoleAddAbstract.onGuildMemberRoleAddStatic(event, getListenerList(GuildMemberRoleAddAbstract.class)));
    }

    @Override
    public void onGuildMemberRoleRemove(@NotNull GuildMemberRoleRemoveEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> GuildMemberRoleRemoveAbstract.onGuildMemberRoleRemoveStatic(event, getListenerList(GuildMemberRoleRemoveAbstract.class)));
    }

    @Override
    public void onGuildUpdateBoostCount(@NotNull GuildUpdateBoostCountEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> GuildUpdateBoostCountAbstract.onGuildUpdateBoostCountStatic(event, getListenerList(GuildUpdateBoostCountAbstract.class)));
    }

    @Override
    public void onUserActivityStart(@NotNull UserActivityStartEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> UserActivityStartAbstract.onUserActivityStartStatic(event, getListenerList(UserActivityStartAbstract.class)));
    }

    @Override
    public void onGuildMemberUpdatePending(@NotNull GuildMemberUpdatePendingEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> GuildMemberUpdatePendingAbstract.onGuildMemberUpdatePendingStatic(event, getListenerList(GuildMemberUpdatePendingAbstract.class)));
    }

    @Override
    public void onButtonClick(@NotNull ButtonClickEvent event) {
        GlobalThreadPool.getExecutorService()
                .submit(() -> ButtonClickAbstract.onButtonClickStatic(event, getListenerList(ButtonClickAbstract.class)));
    }

    @Override
    public void onHttpRequest(@NotNull HttpRequestEvent event) {
        String routeBased = event.getRequest().getRoute().getBaseRoute().getRoute();
        String[] routeBasedParts = routeBased.split("/");
        String[] routeCompiledParts = event.getRoute().getCompiledRoute().split("/");

        String route = event.getRoute().getMethod().toString() + " " + routeBased;
        RequestRouteLogger.getInstance().logRoute(route, event.isRateLimit());

        Long guildId = null;
        for (int i = 0; i < routeBasedParts.length; i++) {
            if (routeBasedParts[i].equals("{channel_id}")) {
                long channelId = Long.parseLong(routeCompiledParts[i]);
                Optional<GuildChannel> channelOpt = ShardManager.getInstance().getLocalGuildChannelById(channelId);
                if (channelOpt.isPresent()) {
                    guildId = channelOpt.get().getGuild().getIdLong();
                }
                break;
            } else if (routeBasedParts[i].equals("{guild_id}")) {
                guildId = Long.parseLong(routeCompiledParts[i]);
                break;
            }
        }

        RestLogger.getInstance().insert(guildId);
    }

}
