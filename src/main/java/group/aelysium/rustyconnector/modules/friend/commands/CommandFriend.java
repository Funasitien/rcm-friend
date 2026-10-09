package group.aelysium.rustyconnector.modules.friend.commands;

import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.event.ClickEvent;
import group.aelysium.rustyconnector.common.errors.Error;
import net.kyori.adventure.text.format.NamedTextColor;
import group.aelysium.rustyconnector.proxy.family.Server;
import group.aelysium.rustyconnector.RC;
import group.aelysium.rustyconnector.common.errors.Error;
import group.aelysium.rustyconnector.common.util.CommandClient;
import group.aelysium.rustyconnector.modules.friend.FriendRegistry;
import group.aelysium.rustyconnector.modules.friend.FriendRequest;
import group.aelysium.rustyconnector.proxy.player.Player;
import group.aelysium.rustyconnector.proxy.util.LiquidTimestamp;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.suggestion.Suggestion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static net.kyori.adventure.text.Component.text;
import net.kyori.adventure.text.format.TextColor;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;
import group.aelysium.rustyconnector.proxy.player.PlayerRegistry;

public final class CommandFriend {
    public static void register(CommandManager<CommandClient> manager, String alias) {
        var baseBuilder = manager.commandBuilder(alias)
                .permission("rustyconnector.command.friend")
                .senderType(CommandClient.Player.class);

        manager.command(baseBuilder
                .handler(context -> {
                    context.sender().send(RC.Lang("rcm-friends-help").generate());
                })
        );

        manager.command(baseBuilder
                .literal("list")
                .handler(context -> {
                    FriendRegistry friends = RC.Module("Friends");
                    if (friends == null) {
                        context.sender().send(RC.Lang("rustyconnector-internalError").generate());
                        return;
                    }

                    try {
                        Set<String> friendIDs = friends.fetchFriends(context.sender().id());
                        if (friendIDs.isEmpty()) {
                            context.sender().send(text("You don't have any friends yet.", NamedTextColor.GRAY));
                            return;
                        }

                        context.sender().send(text("Your friends:", NamedTextColor.YELLOW));
                        PlayerRegistry players = RC.Module("PlayerRegistry");
                        for (String fID : friendIDs) {
                            String fName = fID;
                            if (players != null) {
                                try { fName = players.fetchByID(fID).orElseThrow().username(); } catch(Exception ignore) {}
                            }
                            context.sender().send(text("- " + fName, TextColor.color(0xBAE1FF)));
                        }
                    } catch (Exception e) {
                        RC.Error(Error.from(e).whileAttempting("To fetch friends for list."));
                        context.sender().send(RC.Lang("rustyconnector-internalError").generate());
                    }
                })
        );

        manager.command(baseBuilder
                .literal("add")
                .optional("username", stringParser(), (context, input) -> {
                    try {
                        return CompletableFuture.completedFuture(RC.P.Players()
                                .onlinePlayers()
                                .stream()
                                .filter(Player::online)
                                .map(player -> Suggestion.suggestion(player.username()))
                                .toList());
                    } catch (Exception e) {
                        return CompletableFuture.completedFuture(List.of());
                    }
                })
                .handler(context -> {
                    String targetUsername = context.getOrDefault("username", null);
                    if (targetUsername == null) {
                        context.sender().send(RC.Lang("rcm-friends-missingUsername").generate());
                        return;
                    }

                    try {
                        FriendRegistry friends = (FriendRegistry) RC.ModuleFlux("Friends").get(3, TimeUnit.SECONDS);

                        Player targetPlayer = RC.P.PlayerFromUsername(targetUsername).orElse(null);
                        if (targetPlayer == null) {
                            context.sender().send(RC.Lang("rcm-friends-noPlayer").generate());
                            return;
                        } else if (!targetPlayer.online()) {
                            context.sender().send(RC.Lang("rcm-friends-onlineOnly").generate());
                            return;
                        }

                        Set<String> currentFriends = friends.fetchFriends(context.sender().id());
                        if (currentFriends.contains(targetPlayer.id())) {
                            context.sender().send(RC.Lang("rcm-friends-alreadyFriends").generate(targetUsername));
                            return;
                        }

                        if (currentFriends.size() >= friends.config().maxFriends) {
                            context.sender().send(RC.Lang("rcm-friends-maxFriends").generate());
                            return;
                        }

                        friends.sendFriendRequest(context.sender().id(), targetPlayer.id());

                        LiquidTimestamp expiration = friends.config().requestExpiration();
                        context.sender().send(RC.Lang("rcm-friends-requestSent").generate(targetUsername, String.valueOf(expiration.value()), expiration.unit().name().toLowerCase()));
                        
                        targetPlayer.sendMessage(RC.Lang("rcm-friends-receiveRequest").generate(context.sender().username()));
                    } catch (Exception e) {
                        RC.Error(Error.from(e).whileAttempting("To send a friend request.").detail("Request Sender", context.sender().username()).detail("Request Target", targetUsername));
                        context.sender().send(RC.Lang("rustyconnector-internalError").generate());
                    }
                })
        );

        manager.command(baseBuilder
                .literal("requests")
                .handler(context -> {
                    FriendRegistry friends = RC.Module("Friends");
                    if (friends == null) {
                        context.sender().send(RC.Lang("rustyconnector-internalError").generate());
                        return;
                    }

                    Set<FriendRequest> requests = friends.fetchFriendRequests(context.sender().id());
                    List<FriendRequest> pending = requests.stream()
                            .filter(r -> !r.expired() && r.status().equals(FriendRequest.Status.PENDING))
                            .toList();

                    if (pending.isEmpty()) {
                        context.sender().send(RC.Lang("rcm-friends-noPendingRequests").generate());
                        return;
                    }

                    context.sender().send(RC.Lang("rcm-friends-pendingRequestsHeader").generate());
                    PlayerRegistry players = RC.Module("PlayerRegistry");
                    for (FriendRequest req : pending) {
                        String rUsername = req.senderID();
                        if (players != null) {
                            try { rUsername = players.fetchByID(req.senderID()).orElseThrow().username(); } catch(Exception ignore) {}
                        }
                        context.sender().send(text("- " + rUsername, TextColor.color(0xBAE1FF)).append(text(" ")).append(text("[Accept]", TextColor.color(0xBAFFC9)).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/friend accept " + rUsername))).append(text(" ")).append(text("[Remove]", TextColor.color(0xFFB3BA)).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/friend requests " + rUsername + " ignore"))));
                    }
                })
        );

        // 3b. REQUESTS ACTION COMMAND (/friend requests <username> <action>)
        manager.command(baseBuilder
                .literal("requests")
                .optional("username", stringParser(), (context, input) -> {
                    try {
                        return CompletableFuture.completedFuture(
                                ((FriendRegistry) RC.Module("Friends"))
                                        .fetchFriendRequests(context.sender().id())
                                        .stream()
                                        .filter(r -> !r.expired() && r.status().equals(FriendRequest.Status.PENDING))
                                        .map(r -> {
                                            String u = r.senderID();
                                            PlayerRegistry players = RC.Module("PlayerRegistry");
                                            if (players != null) {
                                                try { u = players.fetchByID(u).orElseThrow().username(); } catch(Exception ignore) {}
                                            }
                                            return Suggestion.suggestion(u);
                                        })
                                        .toList()
                        );
                    } catch (Exception ignore) {
                        return CompletableFuture.completedFuture(List.of());
                    }
                })
                .optional("action", stringParser(), (context, input) -> CompletableFuture.completedFuture(List.of(Suggestion.suggestion("accept"), Suggestion.suggestion("ignore"))))
                .handler(context -> {
                    String username = context.getOrDefault("username", null);
                    String action = context.getOrDefault("action", null);
                    if (username == null) {
                        context.sender().send(RC.Lang("rcm-friends-missingUsername").generate());
                        return;
                    }
                    if (action == null) {
                        context.sender().send(RC.Lang("rcm-friends-missingAction").generate());
                        return;
                    }

                    FriendRegistry friends = RC.Module("Friends");
                    if (friends == null) {
                        context.sender().send(RC.Lang("rustyconnector-internalError").generate());
                        return;
                    }

                    PlayerRegistry players = RC.Module("PlayerRegistry");
                    Set<FriendRequest> requests = friends.fetchFriendRequests(context.sender().id());
                    FriendRequest request = null;
                    for (FriendRequest r : requests) {
                        String rUsername = r.senderID();
                        if (players != null) {
                            try { rUsername = players.fetchByID(r.senderID()).orElseThrow().username(); } catch(Exception ignore) {}
                        }
                        if (rUsername.equalsIgnoreCase(username)) {
                            request = r;
                            break;
                        }
                    }
                    if (request == null) {
                        context.sender().send(RC.Lang("rcm-friends-noRequestFrom").generate(username));
                        return;
                    }
                    if (request.expired() || !request.status().equals(FriendRequest.Status.PENDING)) {
                        context.sender().send(RC.Lang("rcm-friends-requestExpired").generate());
                        return;
                    }

                    if (action.equalsIgnoreCase("ignore")) {
                        request.ignore();
                    } else if (action.equalsIgnoreCase("accept")) {
                        request.accept();
                        try {
                            if (players != null) {
                                Player senderPlayer = players.fetchByID(request.senderID()).orElse(null);
                                if (senderPlayer != null && senderPlayer.online()) {
                                    senderPlayer.sendMessage(RC.Lang("rcm-friends-requestAcceptedNotification").generate(context.sender().username()));
                                }
                            }
                        } catch (Exception ignore) {}
                    } else {
                        context.sender().send(RC.Lang("rcm-friends-acceptOrIgnore").generate());
                    }
                })
        );

        // 4. HELP COMMAND
        manager.command(baseBuilder
                .literal("help")
                .handler(context -> {
                    context.sender().send(RC.Lang("rcm-friends-help").generate());
                })
        );

        // 5. ACCEPT COMMAND
        manager.command(baseBuilder
                .literal("accept")
                .optional("username", stringParser(), (context, input) -> {
                    try {
                        return CompletableFuture.completedFuture(
                                ((FriendRegistry) RC.Module("Friends"))
                                        .fetchFriendRequests(context.sender().id())
                                        .stream()
                                        .filter(r -> !r.expired() && r.status().equals(FriendRequest.Status.PENDING))
                                        .map(r -> {
                                            String u = r.senderID();
                                            PlayerRegistry players = RC.Module("PlayerRegistry");
                                            if (players != null) {
                                                try { u = players.fetchByID(u).orElseThrow().username(); } catch(Exception ignore) {}
                                            }
                                            return Suggestion.suggestion(u);
                                        })
                                        .toList()
                        );
                    } catch (Exception ignore) {
                        return CompletableFuture.completedFuture(List.of());
                    }
                })
                .handler(context -> {
                    String username = context.getOrDefault("username", null);
                    if (username == null) {
                        context.sender().send(RC.Lang("rcm-friends-missingUsername").generate());
                        return;
                    }
                    FriendRegistry friends = RC.Module("Friends");
                    if (friends == null) {
                        context.sender().send(RC.Lang("rustyconnector-internalError").generate());
                        return;
                    }

                    PlayerRegistry players = RC.Module("PlayerRegistry");
                    Set<FriendRequest> requests = friends.fetchFriendRequests(context.sender().id());
                    FriendRequest request = null;
                    for (FriendRequest r : requests) {
                        String rUsername = r.senderID();
                        if (players != null) {
                            try { rUsername = players.fetchByID(r.senderID()).orElseThrow().username(); } catch(Exception ignore) {}
                        }
                        if (rUsername.equalsIgnoreCase(username)) {
                            request = r;
                            break;
                        }
                    }
                    if (request == null) {
                        context.sender().send(RC.Lang("rcm-friends-noRequestFrom").generate(username));
                        return;
                    }
                    if (request.expired() || !request.status().equals(FriendRequest.Status.PENDING)) {
                        context.sender().send(RC.Lang("rcm-friends-requestExpired").generate());
                        return;
                    }
                    request.accept();
                    context.sender().send(RC.Lang("rcm-friends-nowFriends").generate(username));
                    
                    try {
                        if (players != null) {
                            Player senderPlayer = players.fetchByID(request.senderID()).orElse(null);
                            if (senderPlayer != null && senderPlayer.online()) {
                                senderPlayer.sendMessage(RC.Lang("rcm-friends-requestAcceptedNotification").generate(context.sender().username()));
                            }
                        }
                    } catch (Exception ignore) {}
                })
        );

        // 6. TP COMMAND
        manager.command(baseBuilder
                .literal("tp")
                .optional("username", stringParser(), (context, input) -> {
                    try {
                        PlayerRegistry players = RC.Module("PlayerRegistry");
                        return CompletableFuture.completedFuture(
                                ((FriendRegistry) RC.Module("Friends"))
                                        .fetchFriends(context.sender().id())
                                        .stream()
                                        .map(s -> {
                                            try { return players.fetchByID(s).orElse(null); } catch (Exception ignore) {}
                                            return null;
                                        })
                                        .filter(p -> p != null && p.online() && p.server().isPresent())
                                        .map(p->Suggestion.suggestion(p.username()))
                                        .toList()
                        );
                    } catch (Exception ignore) {
                        return CompletableFuture.completedFuture(List.of());
                    }
                })
                .handler(context -> {
                    String targetUsername = context.getOrDefault("username", null);
                    if (targetUsername == null) {
                        context.sender().send(RC.Lang("rcm-friends-missingUsername").generate());
                        return;
                    }
                    Player targetPlayer = RC.P.PlayerFromUsername(targetUsername).orElse(null);
                    if (targetPlayer == null || !targetPlayer.online()) {
                        context.sender().send(RC.Lang("rcm-friends-notOnline").generate(targetUsername));
                        return;
                    }

                    FriendRegistry friends = RC.Module("Friends");
                    try {
                        if (!friends.fetchFriends(context.sender().id()).contains(targetPlayer.id())) {
                            context.sender().send(RC.Lang("rcm-friends-notFriends3").generate(targetUsername));
                            return;
                        }
                    } catch (Exception e) {
                        context.sender().send(RC.Lang("rustyconnector-internalError").generate());
                        return;
                    }

                    Server server = targetPlayer.server().orElse(null);
                    if (server == null) {
                        context.sender().send(RC.Lang("rcm-friends-noServer").generate(targetUsername));
                        return;
                    }

                    try {
                        Player senderPlayer = RC.P.PlayerFromID(context.sender().id()).orElseThrow();
                        Player.Connection.Request request = server.connect(senderPlayer);
                        Player.Connection.Result result = request.result().get(30, TimeUnit.SECONDS);

                        if (result.connected()) {
                            context.sender().send(RC.Lang("rcm-friends-teleporting").generate(targetUsername));
                        } else {
                            context.sender().send(result.message());
                        }
                    } catch (Exception e) {
                        RC.Error(Error.from(e).urgent(true));
                        context.sender().send(RC.Lang("rustyconnector-internalError").generate());
                    }
                })
        );

        // 7. MESSAGE COMMAND
        manager.command(baseBuilder
                .literal("message")
                .optional("username", stringParser(), (context, input) -> {
                    try {
                        PlayerRegistry players = RC.Module("PlayerRegistry");
                        return CompletableFuture.completedFuture(
                                ((FriendRegistry) RC.Module("Friends"))
                                        .fetchFriends(context.sender().id())
                                        .stream()
                                        .map(s -> {
                                            try { return players.fetchByID(s).orElse(null); } catch (Exception ignore) {}
                                            return null;
                                        })
                                        .filter(p -> p != null && p.online())
                                        .map(p->Suggestion.suggestion(p.username()))
                                        .toList()
                        );
                    } catch (Exception ignore) {
                        return CompletableFuture.completedFuture(List.of());
                    }
                })
                .optional("message", org.incendo.cloud.parser.standard.StringParser.greedyStringParser())
                .handler(context -> {
                    String targetUsername = context.getOrDefault("username", null);
                    if (targetUsername == null) {
                        context.sender().send(RC.Lang("rcm-friends-missingUsername").generate());
                        return;
                    }
                    String message = context.getOrDefault("message", null);
                    if (message == null) {
                        context.sender().send(RC.Lang("rcm-friends-missingMessage").generate());
                        return;
                    }

                    Player targetPlayer = RC.P.PlayerFromUsername(targetUsername).orElse(null);
                    if (targetPlayer == null || !targetPlayer.online()) {
                        context.sender().send(RC.Lang("rcm-friends-notOnline").generate(targetUsername));
                        return;
                    }

                    FriendRegistry friends = RC.Module("Friends");
                    try {
                        if (!friends.fetchFriends(context.sender().id()).contains(targetPlayer.id())) {
                            context.sender().send(RC.Lang("rcm-friends-notFriends2").generate(targetPlayer.username()));
                            return;
                        }
                    } catch (Exception e) {
                        context.sender().send(RC.Lang("rustyconnector-internalError").generate());
                        return;
                    }

                    context.sender().send(Component.text("[you -> " + targetPlayer.username() + "]: " + message, NamedTextColor.GRAY));
                    targetPlayer.message(Component.text("[" + context.sender().username() + " -> you]: " + message, NamedTextColor.GRAY)
                            .hoverEvent(HoverEvent.showText(text("Click to reply")))
                            .clickEvent(ClickEvent.suggestCommand("/fm " + context.sender().username() + " ")));
                })
        );
    }
}
