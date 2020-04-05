package DiscordListener;

import Commands.ManagementCategory.AutoChannelCommand;
import Constants.Permission;
import General.PermissionCheckRuntime;
import MySQL.AutoChannel.AutoChannelBean;
import MySQL.AutoChannel.DBAutoChannel;
import MySQL.DBServerOld;
import MySQL.Server.DBServer;
import MySQL.Server.ServerBean;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.event.channel.server.voice.ServerVoiceChannelMemberLeaveEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;

public class VoiceChannelMemberLeaveListener {

    public void onLeave(ServerVoiceChannelMemberLeaveEvent event) throws Exception {
        if (event.getUser().isYourself()) return;
        AutoChannelBean autoChannelBean = DBAutoChannel.getInstance().getBean(event.getServer().getId());

        for (long childChannelId: new ArrayList<>(autoChannelBean.getChildChannels())) {
            if (event.getChannel().getId() == childChannelId) {
                ServerBean serverBean = DBServer.getInstance().getBean(event.getServer().getId());
                if (PermissionCheckRuntime.getInstance().botHasPermission(serverBean.getLocale(), AutoChannelCommand.class, event.getChannel(), Permission.MANAGE_CHANNEL | Permission.CONNECT)) {
                    if (event.getChannel().getConnectedUsers().size() == 0) {
                        event.getChannel().delete().get();
                        autoChannelBean.getChildChannels().remove(childChannelId);
                    }
                }
                break;
            }
        }
    }
}