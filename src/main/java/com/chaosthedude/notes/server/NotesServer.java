package com.chaosthedude.notes.server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.chaosthedude.notes.network.NotesNetworking;
import com.chaosthedude.notes.network.NotesNetworking.DeleteServerNote;
import com.chaosthedude.notes.network.NotesNetworking.IncomingShare;
import com.chaosthedude.notes.network.NotesNetworking.RespondToShare;
import com.chaosthedude.notes.network.NotesNetworking.SaveServerNote;
import com.chaosthedude.notes.network.NotesNetworking.ServerHello;
import com.chaosthedude.notes.network.NotesNetworking.ServerNoteDeleted;
import com.chaosthedude.notes.network.NotesNetworking.ServerNoteUpdated;
import com.chaosthedude.notes.network.NotesNetworking.ShareNote;
import com.chaosthedude.notes.network.ServerNoteData;
import com.chaosthedude.notes.server.ServerNoteStore.KnownPlayer;
import com.chaosthedude.notes.server.ServerNoteStore.PendingShare;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Server side of note sharing. Handlers run on the server thread.
 */
public final class NotesServer {

	public static final int MAX_SERVER_NOTES = 1000;
	public static final int MAX_INBOX_SIZE = 50;

	private static final long SHARE_COOLDOWN_MS = 1000;
	private static final long EDIT_COOLDOWN_MS = 200;

	private static ServerNoteStore store;
	private static final Map<UUID, Long> lastShareTimes = new HashMap<>();
	private static final Map<UUID, Long> lastEditTimes = new HashMap<>();

	private NotesServer() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			store = new ServerNoteStore(server.getWorldPath(LevelResource.ROOT).resolve("notes"));
			store.load();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			store = null;
			lastShareTimes.clear();
			lastEditTimes.clear();
		});

		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> onJoin(listener.player));
		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
			lastShareTimes.remove(listener.player.getUUID());
			lastEditTimes.remove(listener.player.getUUID());
		});

		ServerPlayNetworking.registerGlobalReceiver(ShareNote.TYPE, (payload, context) -> onShare(context.server(), context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(RespondToShare.TYPE, (payload, context) -> onRespondToShare(context.server(), context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(SaveServerNote.TYPE, (payload, context) -> onSaveServerNote(context.server(), context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(DeleteServerNote.TYPE, (payload, context) -> onDeleteServerNote(context.server(), context.player(), payload));
	}

	private static void onJoin(ServerPlayer player) {
		if (store == null) {
			return;
		}

		store.rememberPlayer(player.getUUID(), player.getPlainTextName());

		// Players without the mod can still join, they just don't get any notes
		if (!ServerPlayNetworking.canSend(player, ServerHello.TYPE)) {
			return;
		}

		List<String> knownPlayers = store.getKnownPlayerNames();
		ServerPlayNetworking.send(player, new ServerHello(knownPlayers.subList(0, Math.min(knownPlayers.size(), NotesNetworking.MAX_KNOWN_PLAYERS))));
		for (ServerNoteData note : store.getNotes()) {
			ServerPlayNetworking.send(player, new ServerNoteUpdated(note));
		}
		for (PendingShare share : store.getInbox(player.getUUID())) {
			ServerPlayNetworking.send(player, new IncomingShare(share.id(), share.sender(), share.title(), share.text()));
		}
	}

	private static void onShare(MinecraftServer server, ServerPlayer sender, ShareNote payload) {
		if (store == null) {
			return;
		}
		if (isOnCooldown(lastShareTimes, sender, SHARE_COOLDOWN_MS)) {
			sender.sendSystemMessage(Component.translatable("notes.share.slowDown"));
			return;
		}

		String recipientName = payload.recipient().trim();
		String title = cleanTitle(payload.title());

		UUID recipientId;
		ServerPlayer onlineRecipient = server.getPlayerList().getPlayerByName(recipientName);
		if (onlineRecipient != null) {
			recipientId = onlineRecipient.getUUID();
			recipientName = onlineRecipient.getPlainTextName();
		} else {
			Optional<KnownPlayer> known = store.findPlayer(recipientName);
			if (known.isEmpty()) {
				sender.sendSystemMessage(Component.translatable("notes.share.unknownPlayer", recipientName));
				return;
			}
			recipientId = known.get().id();
			recipientName = known.get().name();
		}

		if (recipientId.equals(sender.getUUID())) {
			sender.sendSystemMessage(Component.translatable("notes.share.self"));
			return;
		}
		if (onlineRecipient != null && !ServerPlayNetworking.canSend(onlineRecipient, IncomingShare.TYPE)) {
			sender.sendSystemMessage(Component.translatable("notes.share.noMod", recipientName));
			return;
		}
		if (store.getInbox(recipientId).size() >= MAX_INBOX_SIZE) {
			sender.sendSystemMessage(Component.translatable("notes.share.inboxFull", recipientName));
			return;
		}

		// Every share goes through the inbox and stays there until the recipient accepts or declines it, so nothing
		// is lost if they disconnect before responding
		PendingShare share = new PendingShare(UUID.randomUUID(), sender.getPlainTextName(), sender.getUUID(), title, payload.text(), System.currentTimeMillis());
		store.addToInbox(recipientId, share);

		if (onlineRecipient != null) {
			ServerPlayNetworking.send(onlineRecipient, new IncomingShare(share.id(), share.sender(), share.title(), share.text()));
			sender.sendSystemMessage(Component.translatable("notes.share.sent", title, recipientName));
		} else {
			sender.sendSystemMessage(Component.translatable("notes.share.queued", recipientName, title));
		}
	}

	private static void onRespondToShare(MinecraftServer server, ServerPlayer recipient, RespondToShare payload) {
		if (store == null) {
			return;
		}

		store.removeFromInbox(recipient.getUUID(), payload.shareId()).ifPresent(share -> {
			ServerPlayer sender = server.getPlayerList().getPlayer(share.senderId());
			if (sender != null) {
				String key = payload.accepted() ? "notes.share.accepted" : "notes.share.declined";
				sender.sendSystemMessage(Component.translatable(key, recipient.getPlainTextName(), share.title()));
			}
		});
	}

	private static void onSaveServerNote(MinecraftServer server, ServerPlayer player, SaveServerNote payload) {
		if (store == null) {
			return;
		}

		ServerNoteData existing = store.getNote(payload.id());
		if (isOnCooldown(lastEditTimes, player, EDIT_COOLDOWN_MS)) {
			// The client already applied this edit locally, so send back the real state
			resendNote(player, payload.id(), existing);
			return;
		}
		if (existing == null && store.getNoteCount() >= MAX_SERVER_NOTES) {
			player.sendSystemMessage(Component.translatable("notes.server.full", MAX_SERVER_NOTES));
			resendNote(player, payload.id(), null);
			return;
		}

		String name = player.getPlainTextName();
		String author = existing != null ? existing.author() : name;
		ServerNoteData note = new ServerNoteData(payload.id(), cleanTitle(payload.title()), payload.text(), author, name, System.currentTimeMillis());
		store.putNote(note);
		broadcast(server, new ServerNoteUpdated(note));
	}

	private static void onDeleteServerNote(MinecraftServer server, ServerPlayer player, DeleteServerNote payload) {
		if (store == null) {
			return;
		}
		if (isOnCooldown(lastEditTimes, player, EDIT_COOLDOWN_MS)) {
			resendNote(player, payload.id(), store.getNote(payload.id()));
			return;
		}

		if (store.removeNote(payload.id())) {
			broadcast(server, new ServerNoteDeleted(payload.id()));
		}
	}

	/**
	 * Corrects a client whose optimistic edit was rejected by sending the note's actual state.
	 */
	private static void resendNote(ServerPlayer player, UUID id, ServerNoteData note) {
		ServerPlayNetworking.send(player, note != null ? new ServerNoteUpdated(note) : new ServerNoteDeleted(id));
	}

	private static void broadcast(MinecraftServer server, CustomPacketPayload payload) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, payload.type())) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private static boolean isOnCooldown(Map<UUID, Long> lastTimes, ServerPlayer player, long cooldownMs) {
		long now = System.currentTimeMillis();
		Long last = lastTimes.get(player.getUUID());
		if (last != null && now - last < cooldownMs) {
			return true;
		}

		lastTimes.put(player.getUUID(), now);
		return false;
	}

	private static String cleanTitle(String title) {
		String cleaned = title.replaceAll("\\p{Cntrl}", "").trim();
		return cleaned.isEmpty() ? "New Note" : cleaned;
	}

}
