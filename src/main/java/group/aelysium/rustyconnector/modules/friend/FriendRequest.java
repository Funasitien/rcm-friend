package group.aelysium.rustyconnector.modules.friend;

import group.aelysium.rustyconnector.RC;
import group.aelysium.rustyconnector.common.errors.Error;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class FriendRequest {
    private final FriendRegistry registry;
    private final String senderID;
    private final String targetID;
    private final AtomicReference<Status> status = new AtomicReference<>(Status.PENDING);
    private final Instant issuedAt = Instant.now();

    protected FriendRequest(
            @NotNull FriendRegistry registry,
            @NotNull String senderID,
            @NotNull String targetID
    ) {
        this.registry = registry;
        this.senderID = senderID;
        this.targetID = targetID;
    }

    public String senderID() {
        return this.senderID;
    }
    public String targetID() {
        return this.targetID;
    }
    public Status status() {
        return this.status.get();
    }
    public boolean expired() {
        long millis = this.registry.config().requestExpiration().unit().toMillis(this.registry.config().requestExpiration().value());
        return this.issuedAt.plusMillis(millis).isBefore(Instant.now());
    }

    public void accept() {
        if(!this.status.get().equals(Status.PENDING)) return;

        try {
            this.registry.createFriendEntry(this.senderID, this.targetID);
        } catch (Exception e) {
            RC.Error(Error.from(e).whileAttempting("To save an accepted friend request to the database."));
            return;
        }

        this.registry.friends.computeIfAbsent(this.senderID, u -> new HashSet<>()).add(this.targetID);
        this.registry.friends.computeIfAbsent(this.targetID, u -> new HashSet<>()).add(this.senderID);

        this.status.set(Status.ACCEPTED);

        java.util.Set<FriendRequest> reqs = this.registry.requests.get(this.targetID);
        if(reqs != null) reqs.remove(this);
    }
    public void ignore() {
        if(!this.status.get().equals(Status.PENDING)) return;

        this.status.set(Status.IGNORED);

        java.util.Set<FriendRequest> reqs = this.registry.requests.get(this.targetID);
        if(reqs != null) reqs.remove(this);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        FriendRequest that = (FriendRequest) o;
        return Objects.equals(senderID, that.senderID) && Objects.equals(targetID, that.targetID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(senderID, targetID);
    }

    public enum Status {
        PENDING,
        ACCEPTED,
        IGNORED
    }
}