package commands.runnables.externalcategory;

import java.util.Locale;
import java.util.Optional;
import commands.listeners.CommandProperties;
import commands.listeners.OnButtonListener;
import commands.runnables.MemberAccountAbstract;
import constants.Emojis;
import constants.LogStatus;
import core.CustomObservableMap;
import core.EmbedFactory;
import core.TextManager;
import core.utils.EmbedUtil;
import core.utils.StringUtil;
import modules.osu.OsuAccount;
import modules.osu.OsuAccountCheck;
import modules.osu.OsuAccountDownloader;
import modules.osu.OsuAccountSync;
import mysql.modules.osuaccounts.DBOsuAccounts;
import mysql.modules.osuaccounts.OsuAccountData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.ButtonStyle;

@CommandProperties(
        trigger = "osu",
        emoji = "✍️",
        executableWithoutArgs = true,
        requiresMemberCache = true,
        releaseDate = { 2020, 11, 28 },
        aliases = { "osu!" }
)
public class OsuCommand extends MemberAccountAbstract implements OnButtonListener {

    private enum Status { DEFAULT, CONNECTING, ABORTED }

    private final static String BUTTON_ID_CONNECT = "connect";
    private final static String BUTTON_ID_CANCEL = "cancel";
    private final static String GUEST = "Guest";

    private boolean memberIsAuthor;
    private String gameMode = "osu";
    private int gameModeSlot = 0;
    private Status status = Status.DEFAULT;
    private OsuAccount osuAccount = null;
    private String osuName;

    public OsuCommand(Locale locale, String prefix) {
        super(locale, prefix);
    }

    @Override
    protected EmbedBuilder processMember(GuildMessageReceivedEvent event, Member member, boolean memberIsAuthor, String args) throws Throwable {
        this.memberIsAuthor = memberIsAuthor;

        boolean userExists = false;
        CustomObservableMap<Long, OsuAccountData> osuMap = DBOsuAccounts.getInstance().retrieve();
        EmbedBuilder eb = EmbedFactory.getEmbedDefault(this, getString("noacc", member.getEffectiveName()));
        setGameMode(args);

        if (osuMap.containsKey(member.getIdLong())) {
            Optional<OsuAccount> osuAccountOpt = OsuAccountDownloader.download(String.valueOf(osuMap.get(member.getIdLong()).getOsuId()), gameMode).get();
            if (osuAccountOpt.isPresent()) {
                userExists = true;
                eb = generateAccountEmbed(member, osuAccountOpt.get());
            }
        }

        if (memberIsAuthor && OsuAccountSync.getInstance().getUserInCache(member.getIdLong()).isEmpty()) {
            setButtons(Button.of(ButtonStyle.PRIMARY, BUTTON_ID_CONNECT, getString("connect", userExists)));
        }

        return eb;
    }

    private void setGameMode(String args) {
        if (args.toLowerCase().contains("osu")) {
            setFound();
        } else if (args.toLowerCase().contains("taiko")) {
            gameMode = "taiko";
            gameModeSlot = 1;
            setFound();
        } else if (args.toLowerCase().contains("catch") || args.toLowerCase().contains("ctb")) {
            gameMode = "fruits";
            gameModeSlot = 2;
            setFound();
        } else if (args.toLowerCase().contains("mania")) {
            gameMode = "mania";
            gameModeSlot = 3;
            setFound();
        }
    }

    @Override
    protected void sendMessage(Member member, TextChannel channel, MessageEmbed eb) {
        channel.sendMessageEmbeds(eb)
                .setActionRows(getActionRows())
                .queue(message -> {
                    if (memberIsAuthor) {
                        setDrawMessage(message);
                        registerButtonListener(member);
                    }
                });
    }

    @Override
    public boolean onButton(ButtonClickEvent event) throws Throwable {
        if (event.getComponentId().equals(BUTTON_ID_CONNECT)) {
            this.status = Status.CONNECTING;
            DBOsuAccounts.getInstance().retrieve().remove(event.getMember().getIdLong());

            Optional<String> osuUsernameOpt = event.getMember().getActivities().stream()
                    .map(OsuAccountCheck::getOsuUsernameFromActivity)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();

            if (osuUsernameOpt.isPresent()) {
                String osuUsername = osuUsernameOpt.get();
                if (!osuUsername.equals(GUEST)) {
                    setButtons();
                    deregisterListeners();
                    Optional<OsuAccount> osuAccountOptional = OsuAccountDownloader.download(osuUsername, gameMode).get();
                    this.osuName = osuUsername;
                    this.osuAccount = osuAccountOptional.orElse(null);
                    DBOsuAccounts.getInstance().retrieve().put(getMemberId().get(), new OsuAccountData(getMemberId().get(), this.osuAccount.getOsuId()));
                    this.status = Status.DEFAULT;
                    return true;
                }
            }

            OsuAccountSync.getInstance().add(event.getMember().getIdLong(), osuUsername -> {
                if (!osuUsername.equals(GUEST)) {
                    setButtons();
                    deregisterListeners();
                    OsuAccountSync.getInstance().remove(event.getMember().getIdLong());
                    OsuAccountDownloader.download(osuUsername, gameMode)
                            .thenAccept(osuAccountOptional -> {
                                this.osuName = osuUsername;
                                this.osuAccount = osuAccountOptional.orElse(null);
                                osuAccountOptional
                                        .ifPresent(o -> DBOsuAccounts.getInstance().retrieve().put(getMemberId().get(), new OsuAccountData(getMemberId().get(), o.getOsuId())));
                                this.status = Status.DEFAULT;
                                drawMessage(draw(event.getMember()));
                            });
                }
            });
            return true;
        } else if (event.getComponentId().equals(BUTTON_ID_CANCEL) && status == Status.CONNECTING) {
            setButtons();
            deregisterListeners();
            OsuAccountSync.getInstance().remove(event.getMember().getIdLong());
            this.osuAccount = null;
            this.status = Status.ABORTED;
            return true;
        }
        return false;
    }

    @Override
    public EmbedBuilder draw(Member member) {
        switch (status) {
            case CONNECTING:
                setButtons(
                        Button.of(ButtonStyle.PRIMARY, BUTTON_ID_CONNECT, getString("refresh")),
                        Button.of(ButtonStyle.SECONDARY, BUTTON_ID_CANCEL, TextManager.getString(getLocale(), TextManager.GENERAL, "process_abort"))
                );
                EmbedBuilder eb = EmbedFactory.getEmbedDefault(this, getString("synchronize", Emojis.LOADING_UNICODE));
                return eb;

            case ABORTED:
                return EmbedFactory.getAbortEmbed(this);

            default:
                if (osuAccount != null) {
                    setButtons(Button.of(ButtonStyle.PRIMARY, BUTTON_ID_CONNECT, getString("connect", 1)));
                    eb = generateAccountEmbed(member, osuAccount);
                    EmbedUtil.addLog(eb, LogStatus.SUCCESS, getString("connected"));
                } else {
                    eb = EmbedFactory.getEmbedError(this)
                            .setTitle(TextManager.getString(getLocale(), TextManager.GENERAL, "no_results"))
                            .setDescription(TextManager.getNoResultsString(getLocale(), osuName));
                }
                return eb;
        }
    }

    private EmbedBuilder generateAccountEmbed(Member member, OsuAccount acc) {
        EmbedBuilder eb = EmbedFactory.getEmbedDefault(this)
                .setTitle(getString("embedtitle", gameModeSlot, StringUtil.escapeMarkdown(acc.getUsername()), acc.getCountryEmoji()))
                .setDescription(getString(
                        "main",
                        StringUtil.numToString(acc.getPp()),
                        acc.getGlobalRank().map(StringUtil::numToString).orElse("?"),
                        acc.getCountryRank().map(StringUtil::numToString).orElse("?"),
                        String.valueOf(acc.getAccuracy()),
                        String.valueOf(acc.getLevel()),
                        String.valueOf(acc.getLevelProgress())
                ))
                .setThumbnail(acc.getAvatarUrl());
        return EmbedUtil.setMemberAuthor(eb, member);
    }

}