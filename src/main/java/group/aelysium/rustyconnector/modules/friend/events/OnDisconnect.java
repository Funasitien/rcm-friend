package group.aelysium.rustyconnector.modules.friend.events;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import group.aelysium.rustyconnector.common.errors.Error;
import group.aelysium.rustyconnector.RC;
import group.aelysium.rustyconnector.common.errors.Error;
import group.aelysium.rustyconnector.common.events.EventListener;
import group.aelysium.rustyconnector.modules.friend.FriendRegistry;
import group.aelysium.rustyconnector.proxy.events.NetworkLeaveEvent;
import group.aelysium.rustyconnector.proxy.player.Player;
import group.aelysium.rustyconnector.proxy.player.PlayerRegistry;

import net.kyori.adventure.text.Component;

import java.util.Optional;

public class OnDisconnect {
    @EventListener
    public void handle(NetworkLeaveEvent event) {
        try {
            Optional.ofNullable(RC.Kernel().fetchModule("FriendRegistry")).ifPresent(f->f.ifPresent(p -> {
                FriendRegistry registry = (FriendRegistry) p;
                
                if (registry.config().social_notifications) {
                    try {
                        PlayerRegistry players = RC.Module("PlayerRegistry");
                        if (players != null) {
                            for (String friendID : registry.fetchFriends(event.player.id())) {
                                try {
                                    Player friend = players.fetchByID(friendID).orElse(null);
                                    if (friend != null && friend.online()) {
                                        friend.sendMessage(RC.Lang("rcm-friends-friendDisconnected").generate(event.player.username()));
                                    }
                                } catch (Exception ignore) {}
                            }
                        }
                    } catch (Exception ignore) {}
                }
                
                registry.clearCacheFor(event.player.id());
            }));
        } catch (Exception e) {
            RC.Error(Error.from(e));
        }
    }
}
