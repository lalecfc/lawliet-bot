package mysql.modules.autochannel;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Optional;
import mysql.DBDataLoad;
import mysql.DBKeySetLoad;
import mysql.DBMain;
import mysql.DBObserverMapCache;

public class DBAutoChannel extends DBObserverMapCache<Long, AutoChannelData> {

    private static final DBAutoChannel ourInstance = new DBAutoChannel();

    public static DBAutoChannel getInstance() {
        return ourInstance;
    }

    private DBAutoChannel() {
    }

    @Override
    protected AutoChannelData load(Long serverId) throws Exception {
        AutoChannelData autoChannelBean = DBMain.getInstance().get(
                "SELECT channelId, active, channelName, locked FROM AutoChannel WHERE serverId = ?;",
                preparedStatement -> preparedStatement.setLong(1, serverId),
                resultSet -> {
                    if (resultSet.next()) {
                        return new AutoChannelData(
                                serverId,
                                resultSet.getLong(1),
                                resultSet.getBoolean(2),
                                resultSet.getString(3),
                                resultSet.getBoolean(4),
                                getChildChannels(serverId)
                        );
                    } else {
                        return new AutoChannelData(
                                serverId,
                                null,
                                false,
                                "%VCName [%Creator]",
                                false,
                                new ArrayList<>()
                        );
                    }
                });

        autoChannelBean.getChildChannelIds()
                .addListAddListener(list -> list.forEach(channelId -> addChildChannel(autoChannelBean.getGuildId(), channelId)))
                .addListRemoveListener(list -> list.forEach(channelId -> removeChildChannel(autoChannelBean.getGuildId(), channelId)));

        return autoChannelBean;
    }

    @Override
    protected void save(AutoChannelData autoChannelBean) {
        DBMain.getInstance().asyncUpdate("REPLACE INTO AutoChannel (serverId, channelId, active, channelName, locked) VALUES (?, ?, ?, ?, ?);", preparedStatement -> {
            preparedStatement.setLong(1, autoChannelBean.getGuildId());

            Optional<Long> channelIdOpt = autoChannelBean.getParentChannelId();
            if (channelIdOpt.isPresent()) {
                preparedStatement.setLong(2, channelIdOpt.get());
            } else {
                preparedStatement.setNull(2, Types.BIGINT);
            }

            preparedStatement.setBoolean(3, autoChannelBean.isActive());
            preparedStatement.setString(4, autoChannelBean.getNameMask());
            preparedStatement.setBoolean(5, autoChannelBean.isLocked());
        });
    }

    private ArrayList<Long> getChildChannels(long serverId) {
        return new DBDataLoad<Long>("AutoChannelChildChannels", "channelId", "serverId = ?",
                preparedStatement -> preparedStatement.setLong(1, serverId)
        ).getArrayList(resultSet -> resultSet.getLong(1));
    }

    private void addChildChannel(long serverId, long channelId) {
        DBMain.getInstance().asyncUpdate("INSERT IGNORE INTO AutoChannelChildChannels (serverId, channelId) VALUES (?, ?);", preparedStatement -> {
            preparedStatement.setLong(1, serverId);
            preparedStatement.setLong(2, channelId);
        });
    }

    private void removeChildChannel(long serverId, long channelId) {
        DBMain.getInstance().asyncUpdate("DELETE FROM AutoChannelChildChannels WHERE serverId = ? AND channelId = ?;", preparedStatement -> {
            preparedStatement.setLong(1, serverId);
            preparedStatement.setLong(2, channelId);
        });
    }

    public ArrayList<Long> retrieveAllChildChannelServerIds() {
        return new DBKeySetLoad<Long>("AutoChannelChildChannels", "serverId")
                .get(resultSet -> resultSet.getLong(1));
    }

}
