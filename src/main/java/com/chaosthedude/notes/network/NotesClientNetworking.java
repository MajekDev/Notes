package com.chaosthedude.notes.network;

import java.util.UUID;

import com.chaosthedude.notes.network.NotesNetworking.DeleteServerNote;
import com.chaosthedude.notes.network.NotesNetworking.IncomingShare;
import com.chaosthedude.notes.network.NotesNetworking.RespondToShare;
import com.chaosthedude.notes.network.NotesNetworking.SaveServerNote;
import com.chaosthedude.notes.network.NotesNetworking.ServerHello;
import com.chaosthedude.notes.network.NotesNetworking.ServerNoteDeleted;
import com.chaosthedude.notes.network.NotesNetworking.ServerNoteUpdated;
import com.chaosthedude.notes.network.NotesNetworking.ShareNote;
import com.chaosthedude.notes.note.Note;
import com.chaosthedude.notes.note.PendingShares;
import com.chaosthedude.notes.note.PendingShares.PendingShare;
import com.chaosthedude.notes.note.ServerNote;
import com.chaosthedude.notes.note.ServerNoteCache;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * Client side of note sharing. Handlers run on the render thread.
 */
public final class NotesClientNetworking {

	private static final SystemToast.SystemToastId SHARE_TOAST = new SystemToast.SystemToastId();

	private NotesClientNetworking() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ServerHello.TYPE, (payload, context) -> {
			ServerNoteCache.reset(payload.knownPlayers());
			// The server resends every pending share after the hello
			PendingShares.clear();
		});
		ClientPlayNetworking.registerGlobalReceiver(ServerNoteUpdated.TYPE, (payload, context) -> ServerNoteCache.upsert(payload.note()));
		ClientPlayNetworking.registerGlobalReceiver(ServerNoteDeleted.TYPE, (payload, context) -> ServerNoteCache.remove(payload.id()));
		ClientPlayNetworking.registerGlobalReceiver(IncomingShare.TYPE, (payload, context) -> {
			PendingShares.add(new PendingShare(payload.shareId(), payload.sender(), payload.title(), payload.text()));
			showToast(Component.translatable("notes.toast.received", payload.sender()), Component.translatable("notes.toast.receivedDesc", payload.title()));
		});

		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			ServerNoteCache.clear();
			PendingShares.clear();
		});
	}

	/**
	 * Whether the current server has Notes installed and can receive shared notes.
	 */
	public static boolean canShare() {
		return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(ShareNote.TYPE);
	}

	/**
	 * Returns false (and tells the player) if the note is too large to send.
	 */
	public static boolean checkFits(Note note) {
		if (NotesNetworking.fitsInPayload(note.getTitle(), note.getRawText())) {
			return true;
		}

		showToast(Component.translatable("notes.toast.tooLong"), Component.translatable("notes.toast.tooLongDesc", NotesNetworking.MAX_TEXT_LENGTH));
		return false;
	}

	public static void shareNote(String recipient, Note note) {
		ClientPlayNetworking.send(new ShareNote(recipient, note.getTitle(), note.getRawText()));
	}

	public static void respondToShare(PendingShare share, boolean accepted) {
		if (canShare()) {
			ClientPlayNetworking.send(new RespondToShare(share.getId(), accepted));
		}
	}

	public static void saveServerNote(ServerNote note) {
		ClientPlayNetworking.send(new SaveServerNote(note.getId(), note.getTitle(), note.getRawText()));
	}

	public static void deleteServerNote(UUID id) {
		ClientPlayNetworking.send(new DeleteServerNote(id));
	}

	public static void showToast(Component title, Component message) {
		SystemToast.add(Minecraft.getInstance().gui.toastManager(), SHARE_TOAST, title, message);
	}

}
