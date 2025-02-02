package events.discordevents.buttonclick;

import commands.Command;
import commands.CommandManager;
import commands.listeners.OnStaticButtonListener;
import core.CustomObservableMap;
import core.MemberCacheController;
import core.utils.BotPermissionUtil;
import events.discordevents.DiscordEvent;
import events.discordevents.eventtypeabstracts.ButtonClickAbstract;
import mysql.modules.guild.DBGuild;
import mysql.modules.guild.GuildData;
import mysql.modules.staticreactionmessages.DBStaticReactionMessages;
import mysql.modules.staticreactionmessages.StaticReactionMessageData;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;

@DiscordEvent
public class ButtonClickCommandsStatic extends ButtonClickAbstract {

    @Override
    public boolean onButtonClick(ButtonClickEvent event) throws Throwable {
        if (!BotPermissionUtil.canWriteEmbed(event.getTextChannel())) {
            return true;
        }

        CustomObservableMap<Long, StaticReactionMessageData> map = DBStaticReactionMessages.getInstance()
                .retrieve(event.getGuild().getIdLong());
        StaticReactionMessageData messageData = map.get(event.getMessageIdLong());

        if (messageData != null) {
            GuildData guildBean = DBGuild.getInstance().retrieve(event.getGuild().getIdLong());
            Command command = CommandManager.createCommandByTrigger(messageData.getCommand(), guildBean.getLocale(), guildBean.getPrefix()).get();
            if (command instanceof OnStaticButtonListener && map.containsKey(event.getMessageIdLong())) {
                if (command.getCommandProperties().requiresMemberCache()) {
                    MemberCacheController.getInstance().loadMembers(event.getGuild()).get();
                }
                ((OnStaticButtonListener) command).onStaticButton(event);
            }
        }

        return true;
    }

}
