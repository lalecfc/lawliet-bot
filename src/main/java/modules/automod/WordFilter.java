package modules.automod;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import commands.Command;
import commands.runnables.moderationcategory.WordFilterCommand;
import constants.Category;
import core.TextManager;
import core.utils.BotPermissionUtil;
import core.utils.JDAUtil;
import mysql.modules.bannedwords.BannedWordsBean;
import mysql.modules.bannedwords.DBBannedWords;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Message;

public class WordFilter extends AutoModAbstract {

    private final BannedWordsBean bannedWordsBean;

    public WordFilter(Message message) throws ExecutionException {
        super(message);
        bannedWordsBean = DBBannedWords.getInstance().retrieve(message.getGuild().getIdLong());
    }

    @Override
    protected boolean withAutoActions(Message message, Locale locale) {
        return true;
    }

    @Override
    protected void designEmbed(Message message, Locale locale, EmbedBuilder eb) {
        eb.setDescription(TextManager.getString(locale, Category.MODERATION, "wordfilter_log", message.getAuthor().getAsTag()))
                .addField(TextManager.getString(locale, Category.MODERATION, "wordfilter_log_channel"), message.getTextChannel().getAsMention(), true)
                .addField(TextManager.getString(locale, Category.MODERATION, "wordfilter_log_content"), message.getContentRaw(), true);

        bannedWordsBean.getLogReceiverUserIds().transform(message.getGuild()::getMemberById, ISnowflake::getIdLong)
                .forEach(member -> JDAUtil.sendPrivateMessage(member, eb.build()).queue());
    }

    @Override
    protected Class<? extends Command> getCommandClass() {
        return WordFilterCommand.class;
    }

    @Override
    protected boolean checkCondition(Message message) {
        return bannedWordsBean.isActive() &&
                stringContainsWord(message.getContentRaw(), new ArrayList<>(bannedWordsBean.getWords())) &&
                !bannedWordsBean.getIgnoredUserIds().contains(message.getAuthor().getIdLong()) &&
                !BotPermissionUtil.can(message.getMember(), Permission.ADMINISTRATOR);
    }

    private boolean stringContainsWord(String input, ArrayList<String> badWords) {
        input = " " + translateString(input) + " ";
        for (String word : badWords) {
            if (input.contains(" " + word + " ")) {
                return true;
            }
        }
        return false;
    }

    public static String translateString(String input) {
        return input.toLowerCase()
                .replace("1", "i")
                .replace("3", "e")
                .replace("4", "a")
                .replace("@", "a")
                .replace("5", "s")
                .replace("7", "t")
                .replace("0", "o")
                .replace("9", "g")
                .replace("\n", " ")
                .replaceAll("[^\\p{IsLatin}\\p{IsCyrillic}\\p{IsArabic} ]", "");
    }

}
