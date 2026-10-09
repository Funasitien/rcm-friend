package group.aelysium.rustyconnector.modules.friend.events;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import group.aelysium.rustyconnector.common.errors.Error;
import group.aelysium.rustyconnector.RC;
import group.aelysium.rustyconnector.common.errors.Error;
import group.aelysium.rustyconnector.common.events.EventListener;
import group.aelysium.rustyconnector.modules.friend.FriendRegistry;
import group.aelysium.rustyconnector.proxy.events.NetworkPostJoinEvent;
import group.aelysium.rustyconnector.proxy.player.Player;
import group.aelysium.rustyconnector.proxy.player.PlayerRegistry;

import net.kyori.adventure.text.Component;

import java.util.Optional;

public class OnConnect {
    @EventListener
    public void handle(NetworkPostJoinEvent event) {
        try {
            Optional.ofNullable(RC.Kernel().fetchModule("FriendRegistry")).ifPresent(f->f.ifPresent(p -> {
                try {
                    FriendRegistry registry = (FriendRegistry) p;
                    registry.fetchFriends(event.player.id());

                    if (registry.config().social_notifications) {
                        PlayerRegistry players = RC.Module("PlayerRegistry");
                        if (players != null) {
                            for (String friendID : registry.fetchFriends(event.player.id())) {
                                try {
                                    Player friend = players.fetchByID(friendID).orElse(null);
                                    if (friend != null && friend.online()) {
                                        Component msg;
                                        if (registry.config().social_showFamily && event.player.server().isPresent() && event.player.server().get().family().isPresent()) {
                                            msg = RC.Lang("rcm-friends-friendConnectedFamily").generate(event.player.username(), event.player.server().get().family().get().displayName());
                                        } else {
                                            msg = RC.Lang("rcm-friends-friendConnected").generate(event.player.username());
                                        }
                                        friend.sendMessage(msg);
                                    }
                                } catch (Exception ignore) {}
                            }
                        }
                    }
                } catch (Exception e) {
                    RC.Error(Error.from(e).whileAttempting("To get "+event.player.username()+"'s friends.").detail("User ID", event.player.id()));
                }
            }));
        } catch (Exception e) {
            RC.Error(Error.from(e).whileAttempting("To get "+event.player.username()+"'s friends.").detail("User ID", event.player.id()));
        }
    }
}
