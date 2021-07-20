package mysql.modules.fisheryusers;

import constants.FisheryGear;
import core.assets.MemberAsset;
import core.utils.NumberUtil;
import mysql.DBRedis;

public class FisheryMemberGearData implements MemberAsset {

    public final String FIELD_GEAR;

    private final FisheryMemberData fisheryMemberData;
    private final FisheryGear gear;

    public FisheryMemberGearData(FisheryMemberData fisheryMemberData, FisheryGear gear) {
        this.fisheryMemberData = fisheryMemberData;
        this.gear = gear;
        this.FIELD_GEAR = "gear:" + gear.ordinal();
    }

    @Override
    public long getGuildId() {
        return fisheryMemberData.getGuildId();
    }

    @Override
    public long getMemberId() {
        return fisheryMemberData.getMemberId();
    }

    public FisheryGear getGear() {
        return gear;
    }

    public int getLevel() {
        return DBRedis.getInstance().getInteger(jedis -> jedis.hget(fisheryMemberData.KEY_ACCOUNT, FIELD_GEAR));
    }

    void setLevel(int level) {
        DBRedis.getInstance().update(jedis -> jedis.hset(fisheryMemberData.KEY_ACCOUNT, FIELD_GEAR, String.valueOf(level)));
    }

    void levelUp() {
        DBRedis.getInstance().update(jedis -> jedis.hincrBy(fisheryMemberData.KEY_ACCOUNT, FIELD_GEAR, 1));
    }

    public long getPrice() {
        return NumberUtil.flattenLong(Math.round(Math.pow(getValue(getLevel()), 1.02) * gear.getStartPrice()), 4);
    }

    public long getEffect() {
        return getEffect(getLevel());
    }

    public long getEffect(long level) {
        return getValue(level) * gear.getEffect();
    }

    public long getDeltaEffect() {
        long level = getLevel();
        return (getValue(level + 1) - getValue(level)) * gear.getEffect();
    }

    public static long getValue(long level) {
        long n = level + 1;
        return n * (n + 1) / 2;
    }

}
