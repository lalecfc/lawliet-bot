package commands.commandrunnables.interactionscategory;
import commands.commandlisteners.CommandProperties;
import commands.commandrunnables.InteractionAbstract;

import java.util.Locale;

@CommandProperties(
        trigger = "tickle",
        emoji = "\uD83E\uDD23",
        executable = true
)
public class TickleCommand extends InteractionAbstract {

    public TickleCommand(Locale locale, String prefix) {
        super(locale, prefix);
    }

    protected String[] getGifs() {
        return new String[]{
                "https://media.discordapp.net/attachments/736274285764542565/736274294211870890/tickle.gif",
                "https://media.discordapp.net/attachments/736274285764542565/736274298494255174/tickle.gif",
                "https://media.discordapp.net/attachments/736274285764542565/736274306392260608/tickle.gif",
                "https://media.discordapp.net/attachments/736274285764542565/736274313069461576/tickle.gif",
                "https://media.discordapp.net/attachments/736274285764542565/736274320795631736/tickle.gif",
                "https://media.discordapp.net/attachments/736274285764542565/736274323920126022/tickle.gif",
                "https://media.discordapp.net/attachments/736274285764542565/736274329494618213/tickle.gif",
                "https://media.discordapp.net/attachments/736274285764542565/736274332023652482/tickle.gif",
                "https://media.discordapp.net/attachments/736274285764542565/736274342559612958/tickle.gif",
                "https://media.discordapp.net/attachments/736274285764542565/736274347186061422/tickle.gif"
        };
    }

}
