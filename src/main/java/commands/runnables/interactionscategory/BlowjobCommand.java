package commands.runnables.interactionscategory;

import java.util.Locale;
import commands.listeners.CommandProperties;
import commands.runnables.RolePlayAbstract;

@CommandProperties(
        trigger = "blowjob",
        emoji = "\uD83C\uDF46",
        executableWithoutArgs = true,
        nsfw = true,
        requiresMemberCache = true
)
public class BlowjobCommand extends RolePlayAbstract {

    public BlowjobCommand(Locale locale, String prefix) {
        super(locale, prefix, true,
                "https://cdn.discordapp.com/attachments/834490895260844032/834491115314610277/blowjob.gif",
                "https://cdn.discordapp.com/attachments/834490895260844032/834491215537242182/blowjob.gif",
                "https://cdn.discordapp.com/attachments/834490895260844032/834491223678517288/blowjob.gif",
                "https://cdn.discordapp.com/attachments/834490895260844032/834491235456516156/blowjob.gif",
                "https://cdn.discordapp.com/attachments/834490895260844032/834491248382705684/blowjob.gif",
                "https://cdn.discordapp.com/attachments/834490895260844032/834491260789063700/blowjob.gif",
                "https://cdn.discordapp.com/attachments/834490895260844032/834491273691136041/blowjob.gif",
                "https://cdn.discordapp.com/attachments/834490895260844032/834491298353381446/blowjob.gif",
                "https://cdn.discordapp.com/attachments/834490895260844032/834491311238283315/blowjob.gif",
                "https://cdn.discordapp.com/attachments/834490895260844032/834491323016806531/blowjob.gif",
                "https://cdn.discordapp.com/attachments/834490895260844032/834491339138924574/blowjob.gif",
                "https://cdn.discordapp.com/attachments/834490895260844032/834491350865936434/blowjob.gif"
        );
    }

}
