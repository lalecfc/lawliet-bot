package modules.schedulers;

import java.time.Instant;
import java.util.Locale;
import commands.Command;
import commands.CommandManager;
import commands.runnables.moderationcategory.MuteCommand;
import constants.Category;
import core.*;
import core.schedule.MainScheduler;
import modules.Mod;
import mysql.modules.moderation.DBModeration;
import mysql.modules.servermute.DBServerMute;
import mysql.modules.servermute.ServerMuteData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;

public class ServerMuteScheduler extends Startable {

    private static final ServerMuteScheduler ourInstance = new ServerMuteScheduler();

    public static ServerMuteScheduler getInstance() {
        return ourInstance;
    }

    private ServerMuteScheduler() {
    }

    @Override
    protected void run() {
        try {
            DBServerMute.getInstance().retrieveAll()
                    .forEach(this::loadServerMute);
        } catch (Throwable e) {
            MainLogger.get().error("Could not start server mute", e);
        }
    }

    public void loadServerMute(ServerMuteData serverMuteData) {
        serverMuteData.getExpirationTime()
                .ifPresent(expirationTime -> loadServerMute(serverMuteData.getGuildId(), serverMuteData.getMemberId(), expirationTime));
    }

    public void loadServerMute(long guildId, long memberId, Instant expires) {
        MainScheduler.getInstance().schedule(expires, "servermute_" + guildId, () -> {
            CustomObservableMap<Long, ServerMuteData> map = DBServerMute.getInstance().retrieve(guildId);
            if (map.containsKey(memberId) &&
                    map.get(memberId).getExpirationTime().orElse(Instant.now()).getEpochSecond() == expires.getEpochSecond() &&
                    ShardManager.getInstance().guildIsManaged(guildId)
            ) {
                onServerMuteExpire(map.get(memberId));
            }
        });
    }

    private void onServerMuteExpire(ServerMuteData serverMuteData) {
        DBServerMute.getInstance().retrieve(serverMuteData.getGuildId())
                .remove(serverMuteData.getMemberId(), serverMuteData);

        MemberCacheController.getInstance().loadMembers(serverMuteData.getGuild().get()).thenAccept(members -> {
            serverMuteData.getMember().ifPresent(member -> {
                Locale locale = serverMuteData.getGuildData().getLocale();
                Role muteRole = DBModeration.getInstance().retrieve(member.getGuild().getIdLong()).getMuteRole().orElse(null);
                if (muteRole != null && PermissionCheckRuntime.getInstance().botCanManageRoles(locale, MuteCommand.class, muteRole)) {
                    member.getGuild().removeRoleFromMember(member, muteRole)
                            .reason(TextManager.getString(locale, Category.MODERATION, "mute_expired_title"))
                            .queue();
                }

                Command command = CommandManager.createCommandByClass(MuteCommand.class, locale, serverMuteData.getGuildData().getPrefix());
                EmbedBuilder eb = EmbedFactory.getEmbedDefault(command, TextManager.getString(locale, Category.MODERATION, "mute_expired", member.getUser().getAsTag()));
                Mod.postLogMembers(command, eb, member.getGuild(), member);
            });
        });
    }

}
