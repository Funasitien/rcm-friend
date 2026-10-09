package group.aelysium.rustyconnector.modules.friend;

import group.aelysium.rustyconnector.RC;
import group.aelysium.rustyconnector.common.errors.Error;
import group.aelysium.rustyconnector.common.lang.Lang;
import group.aelysium.rustyconnector.proxy.player.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static net.kyori.adventure.text.Component.*;
import static net.kyori.adventure.text.JoinConfiguration.*;
import static net.kyori.adventure.text.format.NamedTextColor.BLUE;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
import net.kyori.adventure.text.format.TextColor;

public class FriendLang {
    private static final TextColor ERROR_COLOR = TextColor.color(0xC21833);
    private static final TextColor WARN_COLOR = TextColor.color(0xFFDFBA);
    private static final TextColor SUCCESS_COLOR = TextColor.color(0xBAFFC9);
    private static final TextColor PRIMARY_COLOR = BLUE;
    private static final TextColor SECONDARY_COLOR = TextColor.color(0xFFDFBA);
    private static final TextColor ACCENT_COLOR = TextColor.color(0xE6B3FF);
    private static final TextColor OFFLINE_COLOR = TextColor.color(0xD3D3D3);

    // I need more context for this one.
    @Lang("rcm-friends-controlBoard")
        public static Component controlBoard(String playerID) {
            FriendRegistry friends;
            try {
                friends = (FriendRegistry) RC.ModuleFlux("Friends").get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                RC.Error(Error.from(e).whileAttempting("To fetch the friends for userID: "+playerID));
                return text("There was an internal issue fetching your friends.", ERROR_COLOR);
            }


            boolean isPartyEnabled = false;
            try {
                // api.services().party().orElseThrow();
                isPartyEnabled = true;
            } catch (Exception ignore) {}
            boolean finalIsPartyEnabled = isPartyEnabled;

            try {
                Set<String> friendIDs = friends.fetchFriends(playerID);

            if(friendIDs.isEmpty())
                return text("You don't have any friends. Click here to send a friend request.", WARN_COLOR).clickEvent(ClickEvent.suggestCommand("/"+friends.config.friendCommandAlias+" add "));

            Set<Player> players = new HashSet<>();
            try {
                friendIDs.forEach(i -> {
                    try {
                        players.add(RC.P.PlayerFromID(i).orElseThrow());
                    } catch (NoSuchElementException ignore) {}
                });
            } catch (Exception e) {
                RC.Error(Error.from(e).whileAttempting("To fetch the friends for userID: "+playerID));
                return text("There was an internal issue fetching your friends.", ERROR_COLOR);
            }

            return join(
                newlines(),
                join(
                    spaces(),
                    text("--------------", GRAY),
                    text("Friends ", SECONDARY_COLOR).append(text("(", DARK_GRAY)).append(text(friendIDs.size(), GRAY)).append(text("/"+friends.config.maxFriends+")", DARK_GRAY)),
                    text("[+]", SUCCESS_COLOR).hoverEvent(HoverEvent.showText(text("Click to add a new friend"))).clickEvent(ClickEvent.suggestCommand("/"+friends.config.friendCommandAlias+" add ")),
                    text("--------------", GRAY)
                ),
                join(
                    newlines(),
                    players.stream().map(p -> join(
                        separator(empty()),
                        text("[x]", ERROR_COLOR).hoverEvent(HoverEvent.showText(text("Click to unfriend."))).clickEvent(ClickEvent.runCommand("/"+friends.config.unfriendCommandAlias+" " + p.username())),
                        friends.config.social_messagingEnabled ? text("[m]", WARN_COLOR).hoverEvent(HoverEvent.showText(text("Click to message "+p.username()))).clickEvent(ClickEvent.suggestCommand("/"+friends.config.social_friendMessageAlias+" " + p.username() + " ")) : empty(),
                        // text("[p]", BLUE).hoverEvent(HoverEvent.showText(resolver().get("proxy.friends.panel.invite_party", LanguageResolver.tagHandler("username",friend.username())))).clickEvent(ClickEvent.runCommand("/party invite " + friend.username() + " "))
                        space(),
                        p.online() ?
                            friends.config.social_showFamily ?
                                text(p.username(), SECONDARY_COLOR).hoverEvent(HoverEvent.showText(text("Currently Playing on: ", SECONDARY_COLOR).append(text(Objects.requireNonNull(p.family().orElseThrow().displayName()), ACCENT_COLOR))))
                                :
                                text(p.username(), SECONDARY_COLOR).hoverEvent(HoverEvent.showText(text("Online", SECONDARY_COLOR)))
                            :
                            text(p.username(), GRAY).hoverEvent(HoverEvent.showText(text("Offline", GRAY)))
                    )).toList()
                )
            );
            } catch (Exception e) {
                RC.Error(Error.from(e).whileAttempting("To fetch the friends for userID: "+playerID));
                return text("There was an internal issue fetching your friends.", ERROR_COLOR);
            }
        };

    @Lang("rcm-friends-receiveRequest")
    public static Component receiveRequest(String senderUsername) {
        return text("You have a new friend request from " + senderUsername + "! ", PRIMARY_COLOR)
            .append(text("[Accept]", SUCCESS_COLOR)
                .clickEvent(ClickEvent.runCommand("/friend accept " + senderUsername))
                .hoverEvent(HoverEvent.showText(text("Click to accept", SUCCESS_COLOR))))
            .append(text(" ", PRIMARY_COLOR))
            .append(text("[Remove]", ERROR_COLOR)
                .clickEvent(ClickEvent.runCommand("/friend requests " + senderUsername + " ignore"))
                .hoverEvent(HoverEvent.showText(text("Click to remove", ERROR_COLOR))));
    }

    @Lang("rcm-friends-requestAcceptedNotification")
    public static Component requestAcceptedNotification(String targetUsername) {
        return text(targetUsername + " has accepted your friend request!", SUCCESS_COLOR);
    }

    @Lang("rcm-friends-listTip")
    public static Component listTip() {
        return text("You can use /help for more commands!", SECONDARY_COLOR);
    }

    @Lang("rcm-friends-missingUsername")
    public static Component missingUsername() {
        return text("You must specify a username.", ERROR_COLOR);
    }

    @Lang("rcm-friends-noPlayer")
    public static Component noPlayer() {
        return text("That player does not exist or has never joined.", ERROR_COLOR);
    }

    @Lang("rcm-friends-notFriends2")
    public static Component notFriends2(String targetUsername) {
        return text("You are not friends with " + targetUsername + ".", ERROR_COLOR);
    }

    @Lang("rcm-friends-notOnline")
    public static Component notOnline(String targetUsername) {
        return text(targetUsername + " is not currently online.", ERROR_COLOR);
    }

    @Lang("rcm-friends-acceptOrIgnore")
    public static Component acceptOrIgnore() {
        return text("Invalid action. Use 'accept' or 'ignore'.", ERROR_COLOR);
    }

    @Lang("rcm-friends-alreadyFriends")
    public static Component alreadyFriends(String targetUsername) {
        return text("You are already friends with " + targetUsername + ".", WARN_COLOR);
    }

    @Lang("rcm-friends-help")
    public static Component help() {
        return join(
            newlines(),
            text("--- Friend Commands ---", PRIMARY_COLOR),
            text("/friend add <username>", SECONDARY_COLOR).append(text(" - Send a friend request", SECONDARY_COLOR)),
            text("/friend remove <username>", SECONDARY_COLOR).append(text(" - Remove a friend", SECONDARY_COLOR)),
            text("/friend requests", SECONDARY_COLOR).append(text(" - View pending friend requests", SECONDARY_COLOR)),
            text("/friend tp <username>", SECONDARY_COLOR).append(text(" - Teleport to a friend's server", SECONDARY_COLOR)),
            text("/fm <username> <message>", SECONDARY_COLOR).append(text(" - Send a private message to a friend", SECONDARY_COLOR)),
            text("/friend list", SECONDARY_COLOR).append(text(" - View your friends list", SECONDARY_COLOR))
        );
    }

    @Lang("rcm-friends-maxFriends")
    public static Component maxFriends() {
        return text("You have reached the maximum number of friends allowed.", ERROR_COLOR);
    }

    @Lang("rcm-friends-missingAction")
    public static Component missingAction() {
        return text("You must specify an action (accept/ignore).", ERROR_COLOR);
    }

    @Lang("rcm-friends-missingMessage")
    public static Component missingMessage() {
        return text("You must specify a message to send.", ERROR_COLOR);
    }

    @Lang("rcm-friends-noPendingRequests")
    public static Component noPendingRequests() {
        return text("You have no pending friend requests.", WARN_COLOR);
    }

    @Lang("rcm-friends-noRequestFrom")
    public static Component noRequestFrom(String username) {
        return text("You do not have a pending request from " + username + ".", ERROR_COLOR);
    }

    @Lang("rcm-friends-noServer")
    public static Component noServer(String targetUsername) {
        return text(targetUsername + " is not connected to a valid server.", ERROR_COLOR);
    }

    @Lang("rcm-friends-notFriends3")
    public static Component notFriends3(String targetUsername) {
        return text("You must be friends with " + targetUsername + " to teleport to them.", ERROR_COLOR);
    }

    @Lang("rcm-friends-nowFriends")
    public static Component nowFriends(String username) {
        return text("You are now friends with " + username + "!", SUCCESS_COLOR);
    }

    @Lang("rcm-friends-onlineOnly")
    public static Component onlineOnly() {
        return text("You can only send requests to online players.", ERROR_COLOR);
    }

    @Lang("rcm-friends-pendingRequestsHeader")
    public static Component pendingRequestsHeader() {
        return text("Pending friend requests:", PRIMARY_COLOR);
    }

    @Lang("rcm-friends-requestExpiERROR_COLOR")
    public static Component requestExpiERROR_COLOR() {
        return text("That friend request has expiERROR_COLOR.", ERROR_COLOR);
    }

    @Lang("rcm-friends-requestSent")
    public static Component requestSent(String targetUsername, String duration, String unit) {
        return text("Friend request sent to " + targetUsername + ". It will expire in " + duration + " " + unit + ".", SUCCESS_COLOR);
    }

    @Lang("rcm-friends-teleporting")
    public static Component teleporting(String targetUsername) {
        return text("Teleporting to " + targetUsername + "...", PRIMARY_COLOR);
    }

    @Lang("rcm-friends-notFriends")
    public static Component notFriends(String targetUsername) {
        return text("You are not friends with " + targetUsername + ".", ERROR_COLOR);
    }

    @Lang("rcm-friends-unfriended")
    public static Component unfriended(String targetUsername) {
        return text("You are no longer friends with " + targetUsername + ".", PRIMARY_COLOR);
    }

    @Lang("rcm-friends-friendConnected")
    public static Component friendConnected(String username) {
        return text("[Friend] " + username + " is now online!", ACCENT_COLOR);
    }

    @Lang("rcm-friends-friendConnectedFamily")
    public static Component friendConnectedFamily(String username, String familyName) {
        return text("[Friend] " + username + " connected to " + familyName + "!", ACCENT_COLOR);
    }

    @Lang("rcm-friends-friendDisconnected")
    public static Component friendDisconnected(String username) {
        return text("[Friend] " + username + " went offline.", OFFLINE_COLOR);
    }
}
