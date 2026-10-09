package group.aelysium.rustyconnector.modules.friend;

import group.aelysium.rustyconnector.RC;
import group.aelysium.rustyconnector.common.cache.TimeoutCache;
import group.aelysium.rustyconnector.common.crypt.SHA256;
import group.aelysium.rustyconnector.common.errors.Error;
import group.aelysium.rustyconnector.common.events.EventManager;
import group.aelysium.rustyconnector.common.haze.HazeDatabase;
import group.aelysium.rustyconnector.common.modules.ExternalModuleBuilder;
import group.aelysium.rustyconnector.common.modules.Module;
import group.aelysium.rustyconnector.modules.friend.commands.CommandFM;
import group.aelysium.rustyconnector.modules.friend.commands.CommandFriend;
import group.aelysium.rustyconnector.modules.friend.commands.CommandUnfriend;
import group.aelysium.rustyconnector.modules.friend.events.OnConnect;
import group.aelysium.rustyconnector.modules.friend.events.OnDisconnect;
import group.aelysium.rustyconnector.proxy.ProxyKernel;
import group.aelysium.rustyconnector.proxy.util.LiquidTimestamp;
import group.aelysium.rustyconnector.shaded.group.aelysium.ara.Flux;
import group.aelysium.rustyconnector.shaded.group.aelysium.haze.lib.DataHolder;
import group.aelysium.rustyconnector.shaded.group.aelysium.haze.lib.Filter;
import group.aelysium.rustyconnector.shaded.group.aelysium.haze.lib.Type;
import group.aelysium.rustyconnector.shaded.group.aelysium.haze.requests.CreateRequest;
import group.aelysium.rustyconnector.shaded.group.aelysium.haze.requests.DeleteRequest;
import group.aelysium.rustyconnector.shaded.group.aelysium.haze.requests.ReadRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.kyori.adventure.text.Component.text;
import group.aelysium.rustyconnector.common.lang.LangLibrary;

public class FriendRegistry implements Module {
    private static final String FRIENDS_TABLE = "RC_Friends";

    private final ScheduledExecutorService expiredRequestCleaner = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    protected final FriendConfig config;
    protected final Flux<HazeDatabase> database;
    protected final Map<String, Set<FriendRequest>> requests = new ConcurrentHashMap<>();
    protected final TimeoutCache<String, Set<String>> friends = new TimeoutCache<>(LiquidTimestamp.from(5, TimeUnit.MINUTES));

    public FriendRegistry(
            @NotNull FriendConfig config
    ) throws Exception {
        this.config = config;

        this.database = RC.P.Haze().fetchDatabase(this.config.database);
        if(this.database == null) throw new NoSuchElementException("No database exists on the haze provider with the name '"+this.config.database+"'.");
        HazeDatabase db = this.database.get(1, TimeUnit.MINUTES);

        this.expiredRequestCleaner.schedule(this::clean, config.requestExpiration().value(), config.requestExpiration().unit());

        if(db.doesDataHolderExist(FRIENDS_TABLE)) return;

        DataHolder table = new DataHolder(FRIENDS_TABLE);
        Map<String, Type> columns = Map.of(
            "hashed_id", Type.STRING(64).nullable(false).primaryKey(true),
            "player1_id", Type.STRING(128).nullable(false),
            "player2_id", Type.STRING(128).nullable(false),
            "last_joined", Type.DATETIME().nullable(false)
        );
        columns.forEach(table::addKey);
        db.createDataHolder(table);
    }

    private void clean() {
        if(this.shutdown.get()) return;
        try {
            // Time complexity of this is vulgar
            Map<String, Set<FriendRequest>> expired = new HashMap<>();
            this.requests.forEach((k, v) -> v.forEach(i -> {
                if(i.expired()) expired.computeIfAbsent(k, kk->new HashSet<>()).add(i);
            }));
            expired.forEach((k,v)->v.forEach(e->this.requests.getOrDefault(k, new HashSet<>()).remove(e)));
        } catch (Exception e) {
            RC.Error(Error.from(e).whileAttempting("To clear out expired party invitations."));
        }

        if(this.shutdown.get()) return;
        this.expiredRequestCleaner.schedule(this::clean, config.requestExpiration().value(), config.requestExpiration().unit());
    }

    public FriendConfig config() {
        return this.config;
    }

    public void clearCacheFor(@NotNull String playerID) {
        this.friends.remove(playerID).clear();
    }
    public @NotNull Set<String> fetchFriends(@NotNull String playerID) throws Exception {
        Set<String> found = this.friends.get(playerID);
        if(found == null) {
            HazeDatabase db = this.database.get(5, TimeUnit.SECONDS);
            ReadRequest sp = db.newReadRequest(FRIENDS_TABLE);
            sp.withFilter(
                Filter
                    .by("player1_id", playerID, Filter.EQUALS)
                    .OR("player2_id", playerID, Filter.EQUALS)
            );
            Set<FriendsDTO> response = sp.execute(FriendsDTO.class);

            Set<String> friends = new HashSet<>();
            response.forEach(e -> {
                friends.add(e.player1_id());
                friends.add(e.player2_id());
            });
            friends.remove(playerID);

            this.friends.put(playerID, friends);
            return friends;
        }

        return Collections.unmodifiableSet(Optional.ofNullable(this.friends.get(playerID)).orElse(Set.of()));
    }
    public @NotNull Set<FriendRequest> fetchFriendRequests(@NotNull String playerID) {
        return Collections.unmodifiableSet(Optional.ofNullable(this.requests.get(playerID)).orElse(Set.of()));
    }

    public @NotNull FriendRequest sendFriendRequest(@NotNull String fromPlayerID, @NotNull String toPlayerID) {
        FriendRequest request = new FriendRequest(this, fromPlayerID, toPlayerID);
        this.requests.computeIfAbsent(toPlayerID, k -> new HashSet<>()).add(request);
        return request;
    }

    protected void createFriendEntry(@NotNull String player1ID, @NotNull String player2ID) throws Exception {
        HazeDatabase db = this.database.get(5, TimeUnit.SECONDS);
        CreateRequest sp = db.newCreateRequest(FRIENDS_TABLE);

        sp.parameter("hashed_id", primaryKeyFromPlayerIDs(player1ID, player2ID));
        if (player1ID.compareTo(player2ID) <= 0) {
            sp.parameter("player1_id", player1ID);
            sp.parameter("player2_id", player2ID);
        } else {
            sp.parameter("player1_id", player2ID);
            sp.parameter("player2_id", player1ID);
        }
        sp.parameter("last_joined", java.time.LocalDateTime.now());

        sp.execute();
    }

    public void unfriend(@NotNull String player1ID, @NotNull String player2ID) throws Exception {
        HazeDatabase db = this.database.get(5, TimeUnit.SECONDS);
        DeleteRequest sp = db.newDeleteRequest(FRIENDS_TABLE);

        sp.withFilter(Filter.by("hashed_id", (primaryKeyFromPlayerIDs(player1ID, player2ID)), Filter.EQUALS));
        sp.execute();

        this.friends.getOrDefault(player1ID, new HashSet<>()).remove(player2ID);
        this.friends.getOrDefault(player2ID, new HashSet<>()).remove(player1ID);
    }

    private String primaryKeyFromPlayerIDs(@NotNull String player1ID, @NotNull String player2ID) {
        if (player1ID.compareTo(player2ID) <= 0) {
            return SHA256.hash(player1ID+"-"+player2ID);
        } else {
            return SHA256.hash(player2ID+"-"+player1ID);
        }
    }

    @Override
    public @Nullable Component details() {
        return null;
    }

    @Override
    public void close() {
        this.shutdown.set(true);
        this.expiredRequestCleaner.close();

        this.requests.forEach((k,v)->v.clear());
        this.requests.clear();

        this.friends.forEach((k,v)->v.clear());
        this.friends.close();
    }

    @SuppressWarnings("unused")
    public static class Builder extends ExternalModuleBuilder<FriendRegistry> {
        @Override
        public void bind(@NotNull ProxyKernel kernel, @NotNull FriendRegistry instance) {
            try {
                try {
                    RC.Lang().registerLangNodes(FriendLang.class);
                    RC.P.Adapter().log(text("FriendModule: Registered LangLibrary!", NamedTextColor.YELLOW));
                } catch (Exception e) {
                    RC.Error(Error.from(e).whileAttempting("To register FriendLang nodes."));
                }
                FriendConfig config = FriendConfig.New();

                kernel.fetchModule("EventManager").onStart(e->{
                    ((EventManager) e).listen(new OnConnect());
                    ((EventManager) e).listen(new OnDisconnect());
                    RC.P.Adapter().log(text("FriendModule: Registered EventManager!", NamedTextColor.YELLOW));
                });

                CommandFriend.register(kernel.Adapter().commandManager(), config.friendCommandAlias);
                CommandUnfriend.register(kernel.Adapter().commandManager(), config.unfriendCommandAlias);
                CommandFM.register(kernel.Adapter().commandManager(), config.social_friendMessageAlias);
            } catch (ParseException e) {
                RC.Error(Error.from(e).whileAttempting("To bind the FriendModule."));
            }
        }

        @NotNull
        @Override
        public FriendRegistry onStart(@NotNull Context context) throws Exception {
            return new FriendRegistry(FriendConfig.New());
        }
    }

    public record FriendsDTO (
        String hashed_id,
        @NotNull String player1_id,
        @NotNull String player2_id,
        java.time.LocalDateTime last_joined
    ) {}
}
