package commands.runnables.utilitycategory;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import commands.listeners.CommandProperties;
import commands.listeners.OnReactionListener;
import commands.listeners.OnStaticReactionAddListener;
import commands.listeners.OnStaticReactionRemoveListener;
import commands.runnables.NavigationAbstract;
import constants.Emojis;
import constants.LogStatus;
import constants.Response;
import core.*;
import core.atomicassets.AtomicRole;
import core.atomicassets.AtomicTextChannel;
import core.atomicassets.MentionableAtomicAsset;
import core.cache.ReactionMessagesCache;
import core.emojiconnection.EmojiConnection;
import core.utils.*;
import modules.ReactionMessage;
import mysql.modules.staticreactionmessages.DBStaticReactionMessages;
import mysql.modules.staticreactionmessages.StaticReactionMessageData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GenericGuildMessageReactionEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionRemoveEvent;
import net.dv8tion.jda.api.interactions.components.ButtonStyle;

@CommandProperties(
        trigger = "reactionroles",
        botChannelPermissions = Permission.MESSAGE_EXT_EMOJI,
        botGuildPermissions = { Permission.MANAGE_ROLES, Permission.MESSAGE_HISTORY },
        userGuildPermissions = Permission.MANAGE_ROLES,
        emoji = "☑️️",
        executableWithoutArgs = true,
        usesExtEmotes = true,
        aliases = { "rmess", "reactionrole", "rroles", "selfrole", "selfroles", "sroles", "srole" }
)
public class ReactionRolesCommand extends NavigationAbstract implements OnReactionListener, OnStaticReactionAddListener, OnStaticReactionRemoveListener {

    private static final int MAX_LINKS = 20;
    private final static int
            ADD_OR_EDIT = 0,
            ADD_MESSAGE = 1,
            EDIT_MESSAGE = 2,
            CONFIGURE_MESSAGE = 3,
            UPDATE_TITLE = 4,
            UPDATE_DESC = 5,
            UPDATE_IMAGE = 10,
            ADD_SLOT = 6,
            REMOVE_SLOT = 7,
            EXAMPLE = 8,
            SENT = 9;

    private String title;
    private String description;
    private List<EmojiConnection> emojiConnections = new ArrayList<>();
    private String emojiTemp;
    private String banner;
    private AtomicRole roleTemp;
    private AtomicTextChannel atomicTextChannel;
    private boolean removeRole = true;
    private boolean editMode = false;
    private boolean multipleRoles = true;
    private long editMessageId = 0L;
    private File imageCdn = null;

    private static final Cache<Long, Boolean> blockCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .build();

    public ReactionRolesCommand(Locale locale, String prefix) {
        super(locale, prefix);
    }

    @Override
    public boolean onTrigger(GuildMessageReceivedEvent event, String args) {
        registerNavigationListener(event.getMember());
        registerReactionListener(event.getMember());
        return true;
    }

    @ControllerMessage(state = ADD_MESSAGE)
    public Response onMessageAddMessage(GuildMessageReceivedEvent event, String input) {
        List<TextChannel> serverTextChannel = MentionUtil.getTextChannels(event.getMessage(), input).getList();
        if (serverTextChannel.size() > 0) {
            if (checkWriteInChannelWithLog(serverTextChannel.get(0))) {
                atomicTextChannel = new AtomicTextChannel(serverTextChannel.get(0));
                setLog(LogStatus.SUCCESS, getString("channelset"));
                return Response.TRUE;
            } else {
                return Response.FALSE;
            }
        }
        setLog(LogStatus.FAILURE, TextManager.getNoResultsString(getLocale(), input));
        return Response.FALSE;
    }

    @ControllerMessage(state = UPDATE_TITLE)
    public Response onMessageUpdateTitle(GuildMessageReceivedEvent event, String input) {
        if (input.length() > 0 && input.length() <= 250) {
            title = input;
            setLog(LogStatus.SUCCESS, getString("titleset", input));
            setState(CONFIGURE_MESSAGE);
            return Response.TRUE;
        } else {
            setLog(LogStatus.FAILURE, TextManager.getString(getLocale(), TextManager.GENERAL, "too_many_characters", "250"));
            return Response.FALSE;
        }
    }

    @ControllerMessage(state = UPDATE_DESC)
    public Response onMessageUpdateDesc(GuildMessageReceivedEvent event, String input) {
        if (input.length() > 0 && input.length() <= 1024) {
            description = input;
            setLog(LogStatus.SUCCESS, getString("descriptionset", input));
            setState(CONFIGURE_MESSAGE);
            return Response.TRUE;
        } else {
            setLog(LogStatus.FAILURE, TextManager.getString(getLocale(), TextManager.GENERAL, "too_many_characters", "1024"));
            return Response.FALSE;
        }
    }

    @ControllerMessage(state = UPDATE_IMAGE)
    public Response onMessageUpdateImage(GuildMessageReceivedEvent event, String input) throws IOException, ExecutionException, InterruptedException {
        List<Message.Attachment> attachments = event.getMessage().getAttachments();
        if (attachments.size() > 0) {
            LocalFile tempFile = new LocalFile(LocalFile.Directory.CDN, String.format("reactionroles/%d.png", System.nanoTime()));
            boolean success = FileUtil.downloadImageAttachment(attachments.get(0), tempFile);
            if (success) {
                banner = uploadFile(tempFile);
                setLog(LogStatus.SUCCESS, getString("imageset"));
                setState(CONFIGURE_MESSAGE);
                return Response.TRUE;
            }
        }

        setLog(LogStatus.FAILURE, TextManager.getNoResultsString(getLocale(), input));
        return Response.FALSE;
    }

    private String uploadFile(LocalFile file) {
        if (imageCdn != null) {
            imageCdn.delete();
            imageCdn = null;
        }

        imageCdn = file;
        return file.cdnGetUrl();
    }

    @ControllerMessage(state = ADD_SLOT)
    public Response onMessageAddSlot(GuildMessageReceivedEvent event, String input) {
        if (input.length() > 0) {
            boolean ok = false;
            List<String> emojis = MentionUtil.getEmojis(event.getMessage(), input).getList();
            List<Role> roles = MentionUtil.getRoles(event.getMessage(), input).getList();

            if (emojis.size() > 0) {
                if (processEmoji(emojis.get(0))) {
                    ok = true;
                } else {
                    return Response.FALSE;
                }
            }

            if (roles.size() > 0) {
                if (processRole(event.getMember(), roles)) {
                    ok = true;
                } else {
                    return Response.FALSE;
                }
            }

            if (ok) return Response.TRUE;
        }

        setLog(LogStatus.FAILURE, TextManager.getNoResultsString(getLocale(), input));
        return Response.FALSE;
    }

    private boolean processEmoji(String emoji) {
        if (EmojiUtil.emojiIsUnicode(emoji) || ShardManager.getInstance().emoteIsKnown(emoji)) {
            for (EmojiConnection emojiConnection : new ArrayList<>(emojiConnections)) {
                if (emojiConnection.getEmojiTag().equals(emoji)) {
                    setLog(LogStatus.FAILURE, getString("emojialreadyexists"));
                    return false;
                }
            }

            this.emojiTemp = emoji;
            return true;
        } else {
            setLog(LogStatus.FAILURE, TextManager.getString(getLocale(), TextManager.GENERAL, "emojiunknown"));
            return false;
        }
    }

    private boolean processRole(Member member, List<Role> list) {
        Role roleTest = list.get(0);
        if (!checkRoleWithLog(member, roleTest)) {
            return false;
        }

        roleTemp = new AtomicRole(roleTest);
        return true;
    }

    @ControllerButton(state = ADD_OR_EDIT)
    public boolean onButtonAddOrEdit(ButtonClickEvent event, int i) {
        switch (i) {
            case -1:
                deregisterListenersWithButtonMessage();
                return false;

            case 0:
                setState(ADD_MESSAGE);
                editMode = false;
                return true;

            case 1:
                if (getReactionMessagesInGuild(event.getGuild()).size() > 0) {
                    setState(EDIT_MESSAGE);
                    editMode = true;
                    return true;
                } else {
                    setLog(LogStatus.FAILURE, getString("noreactionmessage"));
                    return true;
                }

            default:
                return false;
        }
    }

    @ControllerButton(state = ADD_MESSAGE)
    public boolean onButtonAddMessage(ButtonClickEvent event, int i) {
        switch (i) {
            case -1:
                setState(ADD_OR_EDIT);
                return true;

            case 0:
                if (atomicTextChannel != null) {
                    setState(CONFIGURE_MESSAGE);
                    return true;
                }

            default:
                return false;
        }
    }

    @ControllerButton(state = EDIT_MESSAGE)
    public boolean onButtonEditMessage(ButtonClickEvent event, int i) {
        if (i == -1) {
            setState(ADD_OR_EDIT);
            return true;
        } else if (i >= 0) {
            List<ReactionMessage> reactionMessages = getReactionMessagesInGuild(event.getGuild());
            if (i < reactionMessages.size()) {
                ReactionMessage reactionMessage = reactionMessages.get(i);
                TextChannel messageChannel = reactionMessage.getTextChannel().get();
                if (checkWriteInChannelWithLog(messageChannel)) {
                    editMessageId = reactionMessage.getMessageId();
                    updateValuesFromMessage(reactionMessage);
                    setState(CONFIGURE_MESSAGE);
                }

                return true;
            }
        }

        return false;
    }

    @ControllerButton(state = CONFIGURE_MESSAGE)
    public boolean onButtonConfigureMessage(ButtonClickEvent event, int i) {
        switch (i) {
            case -1:
                if (!editMode) {
                    setState(ADD_MESSAGE);
                } else {
                    imageCdn = null;
                    setState(EDIT_MESSAGE);
                }
                return true;

            case 0:
                setState(UPDATE_TITLE);
                return true;

            case 1:
                setState(UPDATE_DESC);
                return true;

            case 2:
                setState(UPDATE_IMAGE);
                return true;

            case 3:
                if (emojiConnections.size() < MAX_LINKS) {
                    setState(ADD_SLOT);
                } else {
                    setLog(LogStatus.FAILURE, getString("toomanyshortcuts", String.valueOf(MAX_LINKS)));
                }
                roleTemp = null;
                emojiTemp = null;
                return true;

            case 4:
                if (emojiConnections.size() > 0) {
                    setState(REMOVE_SLOT);
                } else {
                    setLog(LogStatus.FAILURE, getString("noshortcuts"));
                }
                return true;

            case 5:
                removeRole = !removeRole;
                setLog(LogStatus.SUCCESS, getString("roleremoveset"));
                return true;

            case 6:
                multipleRoles = !multipleRoles;
                setLog(LogStatus.SUCCESS, getString("multiplerolesset"));
                return true;

            case 7:
                if (emojiConnections.size() > 0) {
                    if (getLinkString().length() <= 1024) {
                        setState(EXAMPLE);
                    } else {
                        setLog(LogStatus.FAILURE, getString("shortcutstoolong"));
                    }
                } else {
                    setLog(LogStatus.FAILURE, getString("noshortcuts"));
                }
                return true;

            case 8:
                if (emojiConnections.size() > 0) {
                    if (getLinkString().length() <= 1024) {
                        if (sendMessage()) {
                            setState(SENT);
                            deregisterListeners();
                        }
                    } else {
                        setLog(LogStatus.FAILURE, getString("shortcutstoolong"));
                    }
                } else {
                    setLog(LogStatus.FAILURE, getString("noshortcuts"));
                }
                return true;

            default:
                return false;
        }
    }

    @ControllerButton(state = UPDATE_IMAGE)
    public boolean onButtonUpdateImage(ButtonClickEvent event, int i) {
        if (i == -1) {
            setState(CONFIGURE_MESSAGE);
            return true;
        } else if (i == 0) {
            if (imageCdn != null) {
                imageCdn.delete();
                imageCdn = null;
            }
            banner = null;
            setLog(LogStatus.SUCCESS, getString("imageset"));
            setState(CONFIGURE_MESSAGE);
            return true;
        }

        return false;
    }

    @ControllerButton(state = ADD_SLOT)
    public boolean onButtonAddSlot(ButtonClickEvent event, int i) {
        if (i == 0 && roleTemp != null && emojiTemp != null) {
            emojiConnections.add(new EmojiConnection(emojiTemp, roleTemp.getAsMention()));
            setState(CONFIGURE_MESSAGE);
            setLog(LogStatus.SUCCESS, getString("linkadded"));
            return true;
        }

        if (i == -1) {
            setState(CONFIGURE_MESSAGE);
            return true;
        }

        return false;
    }

    @Override
    public boolean onReaction(GenericGuildMessageReactionEvent event) throws Throwable {
        if (getState() == ADD_SLOT) {
            if (BotPermissionUtil.can(event.getChannel(), Permission.MESSAGE_MANAGE)) {
                event.getReaction().removeReaction(event.getUser()).queue();
            }
            boolean success = calculateEmoji(EmojiUtil.reactionEmoteAsMention(event.getReactionEmote()));
            if (success) {
                processDraw(event.getMember()).exceptionally(ExceptionLogger.get());
            }
            return success;
        }

        return false;
    }

    @ControllerButton(state = REMOVE_SLOT)
    public boolean onButtonRemoveSlot(ButtonClickEvent event, int i) {
        if (i == -1) {
            setState(CONFIGURE_MESSAGE);
            return true;
        }
        if (i < emojiConnections.size() && i != -2) {
            setLog(LogStatus.SUCCESS, getString("linkremoved"));
            emojiConnections.remove(i);
            if (emojiConnections.size() == 0) setState(CONFIGURE_MESSAGE);
            return true;
        }
        return false;
    }

    @ControllerButton(state = SENT)
    public boolean onButtonSent(ButtonClickEvent event, int i) {
        return false;
    }

    @ControllerButton
    public boolean onButtonDefault(ButtonClickEvent event, int i) {
        if (i == -1) {
            setState(CONFIGURE_MESSAGE);
            return true;
        }
        return false;
    }

    private boolean sendMessage() {
        Optional<TextChannel> channelOpt = atomicTextChannel.get();
        if (channelOpt.isPresent() && checkWriteInChannelWithLog(channelOpt.get())) {
            TextChannel textChannel = channelOpt.get();
            if (!editMode) {
                Message message = textChannel.sendMessageEmbeds(getMessageEmbed(false).build()).complete();
                registerStaticReactionMessage(message);
                ReactionMessagesCache.getInstance().put(message.getIdLong(), generateReactionMessage(message.getIdLong()));
                if (BotPermissionUtil.canReadHistory(textChannel, Permission.MESSAGE_MANAGE, Permission.MESSAGE_ADD_REACTION)) {
                    RestActionQueue restActionQueue = new RestActionQueue();
                    for (EmojiConnection emojiConnection : new ArrayList<>(emojiConnections)) {
                        restActionQueue.attach(emojiConnection.addReaction(message));
                    }
                    restActionQueue
                            .getCurrentRestAction()
                            .queue();
                }
                return true;
            } else {
                Message message = textChannel.editMessageEmbedsById(editMessageId, getMessageEmbed(false).build()).complete();
                ReactionMessagesCache.getInstance().put(message.getIdLong(), generateReactionMessage(message.getIdLong()));
                if (BotPermissionUtil.canReadHistory(textChannel, Permission.MESSAGE_MANAGE, Permission.MESSAGE_ADD_REACTION)) {
                    RestActionQueue restActionQueue = new RestActionQueue();
                    for (EmojiConnection emojiConnection : new ArrayList<>(emojiConnections)) {
                        boolean exist = false;
                        for (MessageReaction reaction : message.getReactions()) {
                            if (emojiConnection.isEmoji(reaction.getReactionEmote())) {
                                exist = true;
                                break;
                            }
                        }
                        if (!exist) {
                            restActionQueue.attach(emojiConnection.addReaction(message));
                        }
                    }
                    if (BotPermissionUtil.can(textChannel, Permission.MESSAGE_MANAGE)) {
                        for (MessageReaction reaction : message.getReactions()) {
                            boolean exist = false;
                            for (EmojiConnection emojiConnection : new ArrayList<>(emojiConnections)) {
                                if (emojiConnection.isEmoji(reaction.getReactionEmote())) {
                                    exist = true;
                                    break;
                                }
                            }
                            if (!exist) {
                                restActionQueue.attach(reaction.clearReactions());
                            }
                        }
                    }
                    if (restActionQueue.isSet()) {
                        restActionQueue.getCurrentRestAction().queue();
                    }
                }
                return true;
            }
        } else {
            return false;
        }
    }

    private ReactionMessage generateReactionMessage(long messageId) {
        return new ReactionMessage(
                getGuildId().get(),
                atomicTextChannel.getIdLong(),
                messageId,
                title != null ? title : getCommandLanguage().getTitle(),
                description,
                banner,
                removeRole,
                multipleRoles,
                emojiConnections
        );
    }

    private boolean calculateEmoji(String emoji) {
        if (emoji == null || (!EmojiUtil.emojiIsUnicode(emoji) && !ShardManager.getInstance().emoteIsKnown(emoji))) {
            setLog(LogStatus.FAILURE, TextManager.getString(getLocale(), TextManager.GENERAL, "emojiunknown"));
            return true;
        }

        for (EmojiConnection emojiConnection : new ArrayList<>(emojiConnections)) {
            if (emojiConnection.getEmojiTag().equals(emoji)) {
                setLog(LogStatus.FAILURE, getString("emojialreadyexists"));
                return true;
            }
        }

        emojiTemp = emoji;
        return true;
    }

    @Draw(state = ADD_OR_EDIT)
    public EmbedBuilder onDrawAddOrEdit(Member member) {
        setOptions(getString("state0_options").split("\n"));
        return EmbedFactory.getEmbedDefault(this, getString("state0_description"));
    }

    @Draw(state = ADD_MESSAGE)
    public EmbedBuilder onDrawAddMessage(Member member) {
        String notSet = TextManager.getString(getLocale(), TextManager.GENERAL, "notset");
        if (atomicTextChannel != null) {
            setOptions(new String[] { TextManager.getString(getLocale(), TextManager.GENERAL, "continue") });
        }
        return EmbedFactory.getEmbedDefault(this, getString("state1_description", Optional.ofNullable(atomicTextChannel).map(MentionableAtomicAsset::getAsMention).orElse(notSet)), getString("state1_title"));
    }

    @Draw(state = EDIT_MESSAGE)
    public EmbedBuilder onDrawEditMessage(Member member) {
        List<ReactionMessage> reactionMessages = getReactionMessagesInGuild(member.getGuild());
        String[] options = new String[reactionMessages.size()];
        for (int i = 0; i < reactionMessages.size(); i++) {
            ReactionMessage reactionMessage = reactionMessages.get(i);
            AtomicTextChannel channel = new AtomicTextChannel(reactionMessage.getGuildId(), reactionMessage.getTextChannelId());
            options[i] = getString("state2_template", reactionMessage.getTitle(), channel.getName());
        }

        setOptions(options);
        return EmbedFactory.getEmbedDefault(this, getString("state2_description"), getString("state2_title"));
    }

    @Draw(state = CONFIGURE_MESSAGE)
    public EmbedBuilder onDrawConfigureMessage(Member member) {
        String notSet = TextManager.getString(getLocale(), TextManager.GENERAL, "notset");
        setOptions(getString("state3_options").split("\n"));

        String add;
        if (editMode) {
            add = "edit";
        } else {
            add = "new";
        }

        return EmbedFactory.getEmbedDefault(this, getString("state3_description"), getString("state3_title_" + add))
                .addField(getString("state3_mtitle"), StringUtil.escapeMarkdown(Optional.ofNullable(title).orElse(notSet)), true)
                .addField(getString("state3_mdescription"), StringUtil.shortenString(StringUtil.escapeMarkdown(Optional.ofNullable(description).orElse(notSet)), 1024), true)
                .addField(getString("state3_mimage"), StringUtil.getEmojiForBoolean(banner != null), true)
                .addField(getString("state3_mshortcuts"), StringUtil.shortenString(Optional.ofNullable(getLinkString()).orElse(notSet), 1024), false)
                .addField(getString("state3_mproperties"), getString("state3_mproperties_desc", StringUtil.getOnOffForBoolean(getLocale(), removeRole), StringUtil.getOnOffForBoolean(getLocale(), multipleRoles)), false);
    }

    @Draw(state = UPDATE_TITLE)
    public EmbedBuilder onDrawUpdateTitle(Member member) {
        return EmbedFactory.getEmbedDefault(this, getString("state4_description"), getString("state4_title"));
    }

    @Draw(state = UPDATE_DESC)
    public EmbedBuilder onDrawUpdateDesc(Member member) {
        return EmbedFactory.getEmbedDefault(this, getString("state5_description"), getString("state5_title"));
    }

    @Draw(state = UPDATE_IMAGE)
    public EmbedBuilder onDrawUpdateImage(Member member) {
        setOptions(getString("state10_options").split("\n"));
        return EmbedFactory.getEmbedDefault(this, getString("state10_description"), getString("state10_title"));
    }

    @Draw(state = ADD_SLOT)
    public EmbedBuilder onDrawAddSlot(Member member) {
        String notSet = TextManager.getString(getLocale(), TextManager.GENERAL, "notset");
        if (roleTemp != null && emojiTemp != null) setOptions(new String[] { getString("state6_options") });
        return EmbedFactory.getEmbedDefault(this, getString("state6_description", Optional.ofNullable(emojiTemp).orElse(notSet), Optional.ofNullable(roleTemp).map(MentionableAtomicAsset::getAsMention).orElse(notSet)), getString("state6_title"));
    }

    @Draw(state = REMOVE_SLOT)
    public EmbedBuilder onDrawRemoveSlot(Member member) {
        ArrayList<OptionButton> optionsDelete = new ArrayList<>();
        for (EmojiConnection emojiConnection : new ArrayList<>(emojiConnections)) {
            long roleId = StringUtil.filterLongFromString( emojiConnection.getConnection());
            OptionButton optionButton = new OptionButton(
                    ButtonStyle.PRIMARY,
                    new AtomicRole(getGuildId().get(), roleId).getName(),
                    Emoji.fromMarkdown(emojiConnection.getEmojiTag())
            );
            optionsDelete.add(optionButton);
        }
        setOptions(optionsDelete.toArray(new OptionButton[0]));

        return EmbedFactory.getEmbedDefault(this, getString("state7_description"), getString("state7_title"));
    }

    @Draw(state = EXAMPLE)
    public EmbedBuilder onDrawExample(Member member) {
        return getMessageEmbed(true);
    }

    @Draw(state = SENT)
    public EmbedBuilder onDrawSent(Member member) {
        return EmbedFactory.getEmbedDefault(this, getString("state9_description"), getString("state9_title"));
    }

    private EmbedBuilder getMessageEmbed(boolean test) {
        String titleAdd = "";
        String identity = "";
        if (!test) identity = Emojis.EMPTY_EMOJI;
        if (!removeRole && !test) titleAdd = Emojis.EMPTY_EMOJI;
        if (!multipleRoles && !test) titleAdd += Emojis.EMPTY_EMOJI + Emojis.EMPTY_EMOJI;

        return EmbedFactory.getEmbedDefault()
                .setTitle(getCommandProperties().emoji() + " " + (title != null ? title : getString("title")) + identity + titleAdd)
                .setDescription(description)
                .setImage(banner)
                .addField(TextManager.getString(getLocale(), TextManager.GENERAL, "options"), getLinkString(), false);
    }

    private String getLinkString() {
        StringBuilder link = new StringBuilder();
        for (EmojiConnection emojiConnection : new ArrayList<>(emojiConnections)) {
            link.append(emojiConnection.getEmojiTag());
            link.append(" → ");
            link.append(emojiConnection.getConnection());
            link.append("\n");
        }
        if (link.length() == 0) return null;
        return link.toString();
    }

    @Override
    public void onStaticReactionAdd(Message message, GuildMessageReactionAddEvent event) {
        Member member = event.getMember();
        updateValuesFromMessage(ReactionMessagesCache.getInstance().get(message).get());
        if (!blockCache.asMap().containsKey(member.getIdLong())) {
            try {
                if (!multipleRoles) {
                    blockCache.put(member.getIdLong(), true);
                    if (removeMultipleRoles(event)) {
                        return;
                    }
                }

                giveRole(event);
            } finally {
                if (!multipleRoles) {
                    blockCache.invalidate(member.getIdLong());
                }
            }
        }
    }

    private List<ReactionMessage> getReactionMessagesInGuild(Guild guild) {
        List<StaticReactionMessageData> guildReactions = DBStaticReactionMessages.getInstance().retrieve(guild.getIdLong()).values().stream()
                .filter(m -> m.getCommand().equals(getTrigger()))
                .collect(Collectors.toList());

        return guildReactions.stream()
                .sorted((md0, md1) -> {
                    int channelComp = Integer.compare(
                            md0.getTextChannel().map(GuildChannel::getPositionRaw).orElse(0),
                            md1.getTextChannel().map(GuildChannel::getPositionRaw).orElse(0)
                    );
                    if (channelComp == 0) {
                        return Long.compare(md0.getMessageId(), md1.getMessageId());
                    }
                    return channelComp;
                })
                .map(m -> m.getTextChannel().flatMap(ch -> ReactionMessagesCache.getInstance().get(ch, m.getMessageId())).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableList());
    }

    private void updateValuesFromMessage(ReactionMessage message) {
        this.title = message.getTitle();
        this.description = message.getDescription().orElse(null);
        this.banner = message.getBanner().orElse(null);
        this.multipleRoles = message.isMultipleRoles();
        this.removeRole = message.isRemoveRole();
        this.emojiConnections = message.getEmojiConnections();
        this.atomicTextChannel = new AtomicTextChannel(message.getGuildId(), message.getTextChannelId());
    }

    private boolean giveRole(GuildMessageReactionAddEvent event) {
        for (EmojiConnection emojiConnection : new ArrayList<>(emojiConnections)) {
            if (emojiConnection.isEmoji(event.getReactionEmote())) {
                Optional<Role> rOpt = MentionUtil.getRoleByTag(event.getGuild(), emojiConnection.getConnection());
                if (rOpt.isEmpty()) {
                    return true;
                }

                Role r = rOpt.get();
                if (PermissionCheckRuntime.getInstance().botCanManageRoles(getLocale(), getClass(), r)) {
                    event.getGuild().addRoleToMember(event.getMember(), r)
                            .reason(getCommandLanguage().getTitle())
                            .complete();
                }
                return true;
            }
        }

        return false;
    }

    private boolean removeMultipleRoles(GuildMessageReactionAddEvent event) {
        for (EmojiConnection emojiConnection : new ArrayList<>(emojiConnections)) {
            Optional<Role> rOpt = MentionUtil.getRoleByTag(event.getGuild(), emojiConnection.getConnection());
            if (rOpt.isPresent()) {
                Role r = rOpt.get();
                if (event.getMember().getRoles().contains(r) && PermissionCheckRuntime.getInstance().botCanManageRoles(getLocale(), getClass(), r)) {
                    if (!removeRole) return true;
                    event.getGuild().removeRoleFromMember(event.getMember(), r)
                            .reason(getCommandLanguage().getTitle())
                            .complete();
                }
            }
        }

        return false;
    }

    @Override
    public void onStaticReactionRemove(Message message, GuildMessageReactionRemoveEvent event) {
        updateValuesFromMessage(ReactionMessagesCache.getInstance().get(message).get());
        if (removeRole) {
            for (EmojiConnection emojiConnection : new ArrayList<>(emojiConnections)) {
                if (emojiConnection.isEmoji(event.getReactionEmote())) {
                    Optional<Role> rOpt = MentionUtil.getRoleByTag(event.getGuild(), emojiConnection.getConnection());
                    if (rOpt.isEmpty()) return;

                    Role role = rOpt.get();
                    MemberCacheController.getInstance().loadMembers(message.getGuild()).join();
                    if (event.getGuild().getMembers().stream().anyMatch(m -> m.getIdLong() == event.getUserIdLong()) &&
                            PermissionCheckRuntime.getInstance().botCanManageRoles(getLocale(), getClass(), role)
                    ) {
                        event.getGuild().removeRoleFromMember(event.getUserId(), role)
                                .reason(getCommandLanguage().getTitle())
                                .queue();
                    }
                    break;
                }
            }
        }
    }

}
