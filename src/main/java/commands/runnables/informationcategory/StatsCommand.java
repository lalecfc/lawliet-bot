package commands.runnables.informationcategory;

import java.util.Locale;
import java.util.concurrent.ExecutionException;
import commands.Command;
import commands.listeners.CommandProperties;
import constants.ExternalLinks;
import core.EmbedFactory;
import core.Program;
import core.ShardManager;
import core.components.ActionRows;
import core.utils.BotUtil;
import core.utils.StringUtil;
import mysql.modules.version.DBVersion;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.ButtonStyle;
import net.dv8tion.jda.api.utils.TimeFormat;

@CommandProperties(
        trigger = "stats",
        emoji = "\uD83D\uDCCA",
        executableWithoutArgs = true,
        onlyPublicVersion = true,
        aliases = { "info" }
)
public class StatsCommand extends Command {

    public StatsCommand(Locale locale, String prefix) {
        super(locale, prefix);
    }

    @Override
    public boolean onTrigger(GuildMessageReceivedEvent event, String args) throws ExecutionException, InterruptedException {
        String dephordName = "???";
        try {
            dephordName = ShardManager.getInstance().fetchUserById(303085910784737281L).get().getAsTag();
        } catch (InterruptedException | ExecutionException e) {
            //Ignore
        }

        String neverCookFirstName = "???";
        try {
            neverCookFirstName = ShardManager.getInstance().fetchUserById(298153126223937538L).get().getAsTag();
        } catch (InterruptedException | ExecutionException e) {
            //Ignore
        }

        String laleName = "???";
        try {
            laleName = ShardManager.getInstance().fetchUserById(774017093309431808L).get().getAsTag();
        } catch (InterruptedException | ExecutionException e) {
            //Ignore
        }

        String owner = ShardManager.getInstance().fetchOwner().get().getAsTag();
        EmbedBuilder eb = EmbedFactory.getEmbedDefault(
                this,
                getString(
                        "template",
                        owner,
                        ExternalLinks.BOT_INVITE_URL,
                        BotUtil.getCurrentVersion(),
                        TimeFormat.DATE_TIME_SHORT.atInstant(DBVersion.getInstance().retrieve().getCurrentVersion().getDate()).toString(),
                        ShardManager.getInstance().getGlobalGuildSize().map(StringUtil::numToString).orElse("-"),
                        owner,
                        StringUtil.numToString(event.getJDA().getShardInfo().getShardId()),
                        StringUtil.numToString(Program.getClusterId())
                ) + "\n\n" + getString("translator", dephordName, neverCookFirstName, laleName)
        );

        event.getChannel().sendMessageEmbeds(eb.build())
                .setActionRows(ActionRows.of(Button.of(ButtonStyle.LINK, ExternalLinks.BOT_INVITE_URL, getString("invite"))))
                .queue();
        return true;
    }

}
