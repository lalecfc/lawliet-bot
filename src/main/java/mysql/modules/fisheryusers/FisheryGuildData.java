package mysql.modules.fisheryusers;

import java.time.Duration;
import java.util.*;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import constants.Settings;
import core.CustomObservableList;
import core.assets.GuildAsset;
import core.utils.TimeUtil;
import javafx.util.Pair;
import mysql.DBRedis;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Role;
import org.checkerframework.checker.nullness.qual.NonNull;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Tuple;

public class FisheryGuildData implements GuildAsset {

    public final String KEY_RECENT_FISH_GAINS_RAW;
    public final String KEY_RECENT_FISH_GAINS_PROCESSED;

    private final long guildId;
    private long recentFishGainsRefreshHour = 0;
    private final HashMap<Long, Long> coinsHiddenMap = new HashMap<>();
    private final CustomObservableList<Long> ignoredChannelIds;
    private final CustomObservableList<Long> roleIds;

    private final Cache<Long, Pair<Integer, Integer>> messageActivityCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .build();

    public FisheryGuildData(long guildId, @NonNull ArrayList<Long> ignoredChannelIds, @NonNull ArrayList<Long> roleIds) {
        this.guildId = guildId;
        this.ignoredChannelIds = new CustomObservableList<>(ignoredChannelIds);
        this.roleIds = new CustomObservableList<>(roleIds);

        this.KEY_RECENT_FISH_GAINS_RAW = "recent_fish_gains_raw:" + guildId;
        this.KEY_RECENT_FISH_GAINS_PROCESSED = "recent_fish_gains_processed:" + guildId;
    }

    @Override
    public long getGuildId() {
        return guildId;
    }

    public CustomObservableList<Long> getIgnoredChannelIds() {
        return ignoredChannelIds;
    }

    public CustomObservableList<Long> getRoleIds() {
        return roleIds;
    }

    public CustomObservableList<Role> getRoles() {
        return getGuild().map(guild -> {
            CustomObservableList<Role> roles = roleIds.transform(guild::getRoleById, ISnowflake::getIdLong, true);
            roles.sort(Comparator.comparingInt(Role::getPosition));
            return roles;
        }).orElse(new CustomObservableList<>(new ArrayList<>()));
    }

    public FisheryMemberData getMemberData(long memberId) {
        return new FisheryMemberData(this, memberId);
    }

    public long getCoinsHidden(long memberId) {
        return coinsHiddenMap.getOrDefault(memberId, 0L);
    }

    public void addCoinsHidden(long memberId, long coinsRaw, long amount) {
        long coinsHidden = getCoinsHidden(memberId);
        coinsHidden = Math.min(coinsRaw, coinsHidden + amount);
        if (coinsHidden > 0) {
            coinsHiddenMap.put(memberId, coinsHidden);
        } else {
            coinsHiddenMap.remove(memberId);
        }
    }

    public synchronized Optional<Map<Long, Long>> refreshRecentFishGains() {
        long currentHour = TimeUtil.currentHour();
        if (currentHour > recentFishGainsRefreshHour) { /* TODO: exclude members which are not in the server */
            HashMap<Long, Long> processedMap = new HashMap<>();

            DBRedis.getInstance().update(jedis -> {
                long minHour = currentHour - 24 * 7;
                List<Map.Entry<String, String>> list = DBRedis.getInstance().hscan(jedis, KEY_RECENT_FISH_GAINS_RAW);
                ArrayList<String> outdatedEntries = new ArrayList<>();

                for (Map.Entry<String, String> entry : list) {
                    String[] keyArgs = entry.getKey().split(":");
                    long hour = Long.parseLong(keyArgs[0]);
                    if (hour >= minHour) {
                        long userId = Long.parseLong(keyArgs[1]);
                        long add = Long.parseLong(entry.getValue());
                        long recentFishGains = processedMap.computeIfAbsent(userId, k -> 0L);
                        processedMap.put(userId, Math.min(recentFishGains + add, Settings.FISHERY_MAX));
                    } else {
                        outdatedEntries.add(entry.getKey());
                    }
                }

                Pipeline pipeline = jedis.pipelined();
                pipeline.del(KEY_RECENT_FISH_GAINS_PROCESSED);
                for (Map.Entry<Long, Long> entry : processedMap.entrySet()) {
                    pipeline.zadd(KEY_RECENT_FISH_GAINS_PROCESSED, entry.getValue(), String.valueOf(entry.getKey()));
                }
                pipeline.hdel(KEY_RECENT_FISH_GAINS_RAW, outdatedEntries.toArray(new String[0]));
                pipeline.sync();
            });

            recentFishGainsRefreshHour = currentHour;
            return Optional.of(processedMap);
        } else {
            return Optional.empty();
        }
    }

    public Map<Long, Long> getAllRecentFishGains() {
        return refreshRecentFishGains().orElseGet(() -> {
            return DBRedis.getInstance().get(jedis -> {
                HashMap<Long, Long> recentFishGains = new HashMap<>();
                List<Tuple> list = DBRedis.getInstance().zscan(jedis, KEY_RECENT_FISH_GAINS_PROCESSED);
                for (Tuple tuple : list) {
                    recentFishGains.put(Long.parseLong(tuple.getElement()), (long) tuple.getScore());
                }
                return recentFishGains;
            });
        });
    }

    public FisheryRecentFishGainsData getRecentFishGainsForMember(long memberId) {
        refreshRecentFishGains();
        Double scoreDouble = DBRedis.getInstance().get(jedis -> jedis.zscore(KEY_RECENT_FISH_GAINS_PROCESSED, String.valueOf(memberId)));
        long score = DBRedis.parseLong(scoreDouble);
        return DBRedis.getInstance().get(jedis -> {
            Long rank = jedis.zcount(KEY_RECENT_FISH_GAINS_PROCESSED, score + 1, Settings.FISHERY_MAX);
            return new FisheryRecentFishGainsData(guildId, memberId, DBRedis.parseInteger(rank) + 1, score);
        });
    }

    public boolean messageActivityIsValid(long memberId, String messageContent) {
        int currentMessageSlot = (Calendar.getInstance().get(Calendar.SECOND) + Calendar.getInstance().get(Calendar.MINUTE) * 60) / 20;
        int currentMessageContentHash = messageContent.hashCode();

        boolean valid = true;
        Pair<Integer, Integer> pair = messageActivityCache.getIfPresent(memberId);
        if (pair != null) {
            int lastMessageSlot = pair.getKey();
            int lastMessageContentHash = pair.getValue();
            valid = currentMessageSlot > lastMessageSlot && currentMessageContentHash != lastMessageContentHash;
        }
        messageActivityCache.put(memberId, new Pair<>(currentMessageSlot, currentMessageContentHash));
        return valid;
    }


}