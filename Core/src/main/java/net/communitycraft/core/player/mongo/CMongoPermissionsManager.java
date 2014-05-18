package net.communitycraft.core.player.mongo;

import com.mongodb.*;
import lombok.Getter;
import lombok.SneakyThrows;
import net.communitycraft.core.Core;
import net.communitycraft.core.player.*;
import org.bson.types.ObjectId;
import org.bukkit.ChatColor;

import java.lang.ref.WeakReference;
import java.util.*;

import static net.communitycraft.core.player.mongo.MongoUtils.getListFor;
import static net.communitycraft.core.player.mongo.MongoUtils.getPermissibileDataFor;
import static net.communitycraft.core.player.mongo.MongoUtils.getValueFrom;

final class CMongoPermissionsManager implements CPermissionsManager {
    private final CMongoDatabase database;
    private final CPlayerManager playerManager;
    @Getter private CGroup defaultGroup;
    private Map<String, CMongoGroup> groups;
    private final List<WeakReference<GroupReloadObserver>> groupReloadObservers = new LinkedList<>();

    public CMongoPermissionsManager(CMongoDatabase database, CPlayerManager playerManager) {
        this.database = database;
        this.playerManager = playerManager;
        reloadPermissions();
    }

    @Override
    public CGroup createNewGroup(String name) {
        if (getGroup(name) != null) throw new IllegalStateException("Group already exists!");
        @SuppressWarnings("unchecked") CMongoGroup group = new CMongoGroup(name, Collections.EMPTY_MAP, Collections.EMPTY_LIST, ChatColor.WHITE, ChatColor.WHITE, name);
        saveGroup(group);
        if (this.getDefaultGroup() == null) setDefaultGroup(group);
        return group;
    }

    @Override
    public CGroup getGroup(String name) {
        return groups.get(name);
    }

    CGroup getGroupByObjectId(ObjectId id) {
        for (CGroup cGroup : getGroups()) {
            if (cGroup.equals(id)) return cGroup;
        }
        return null;
    }

    @Override
    @SneakyThrows
    public void deleteGroup(CGroup group) {
        DBObject query = new BasicDBObjectBuilder().add(MongoKey.ID_KEY.toString(), ((CMongoGroup) group).getObjectId()).get();
        DBCollection groupsCollection = database.getCollection(MongoKey.GROUPS_COLLECTION.toString());
        DBObject andRemove = groupsCollection.findAndRemove(query);
        if (andRemove == null) throw new IllegalStateException("Group does not exist!");
        for (CPlayer cPlayer : group.getOnlineDirectMembers()) {
            cPlayer.removeFromGroup(group);
        }
        DBObject findPlayersInGroupQuery = new BasicDBObjectBuilder().add(MongoKey.USER_GROUPS_KEY.toString(), ((CMongoGroup) group).getObjectId()).get();
        DBCollection usersCollection = database.getCollection(MongoKey.USERS_COLLETION.toString());
        DBCursor playersInGroup = usersCollection.find(findPlayersInGroupQuery);
        CMongoPlayerManager mongoPlayerManager = (CMongoPlayerManager) playerManager;
        for (DBObject dbObject : playersInGroup) {
            COfflinePlayer playerInGroup = mongoPlayerManager.getOfflinePlayerByObjectId(getValueFrom(dbObject, MongoKey.ID_KEY, ObjectId.class));
            playerInGroup.removeFromGroup(group);
            playerInGroup.saveIntoDatabase();
        }
        this.groups.remove(group.getName());
        Core.logInfo("Removed group " + group.getName());
    }

    @Override
    public void saveGroup(CGroup group) {
        DBCollection collection = database.getCollection(MongoKey.GROUPS_COLLECTION.toString());
        CMongoGroup group1 = (CMongoGroup) group;
        DBObject dbObject = group1.getDBObject();
        if (defaultGroup.equals(group1)) dbObject.put(MongoKey.GROUPS_DEFAULT_MARKER.toString(), true);
        else if (dbObject.containsField(MongoKey.GROUPS_DEFAULT_MARKER.toString())) dbObject.removeField(MongoKey.GROUPS_DEFAULT_MARKER.toString());
        collection.save(dbObject);
        group1.setObjectId(getValueFrom(dbObject, MongoKey.ID_KEY, ObjectId.class));
    }

    @Override
    public List<CGroup> getGroups() {
        return new ArrayList<CGroup>(groups.values());
    }

    @Override
    public void reloadPermissions() {
        this.groups = new HashMap<>();
        this.defaultGroup = null;
        DBCollection groups = database.getCollection(MongoKey.GROUPS_COLLECTION.toString());
        for (DBObject dbObject : groups.find()) {
            CMongoGroup groupFor = getGroupFor(dbObject);
            if (groupFor == null) continue;
            this.groups.put(groupFor.getName(), groupFor);
            if (dbObject.containsField(MongoKey.GROUPS_DEFAULT_MARKER.toString())) this.defaultGroup = groupFor;
        }
        for (CGroup cGroup : getGroups()) {
            cGroup.reloadPermissions();
        }
        Iterator<WeakReference<GroupReloadObserver>> iterator = groupReloadObservers.iterator();
        while (iterator.hasNext()) {
            GroupReloadObserver observer = iterator.next().get();
            if (observer == null) {
                iterator.remove();
                continue;
            }
            observer.onReloadPermissions(this);
        }
    }

    public void registerObserver(GroupReloadObserver observer) {
        this.groupReloadObservers.add(new WeakReference<>(observer));
    }

    CMongoGroup getGroupFor(DBObject object) {
        String name = getValueFrom(object, MongoKey.GROUPS_NAME_KEY, String.class);
        List<ObjectId> parentIds = getListFor(getValueFrom(object, MongoKey.GROUPS_PARENTS_KEY, BasicDBList.class), ObjectId.class);
        List<CGroup> parents = new ArrayList<>();
        for (ObjectId parentId : parentIds) {
            parents.add(getGroupByObjectId(parentId));
        }
        ObjectId objectId = getValueFrom(object, MongoKey.ID_KEY, ObjectId.class);
        CPermissible perm = getPermissibileDataFor(object);
        CMongoGroup cMongoGroup = new CMongoGroup(name, perm.getDeclaredPermissions(), parents, perm.getTablistColor(), perm.getChatColor(), perm.getChatPrefix());
        cMongoGroup.setObjectId(objectId);
        return cMongoGroup;
    }

    @Override
    public void setDefaultGroup(CGroup group) {
        CGroup defaultGroup1 = this.defaultGroup;
        this.defaultGroup = null;
        saveGroup(defaultGroup1);
        this.defaultGroup = group;
        saveGroup(group);
    }
}