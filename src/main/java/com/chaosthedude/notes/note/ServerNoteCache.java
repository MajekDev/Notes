package com.chaosthedude.notes.note;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.chaosthedude.notes.Notes;
import com.chaosthedude.notes.network.ServerNoteData;

/**
 * Client-side copy of the notes stored on the current server. Only accessed on the render thread.
 */
public final class ServerNoteCache {

	private static final Map<UUID, ServerNote> NOTES = new LinkedHashMap<>();
	private static final List<String> KNOWN_PLAYERS = new ArrayList<>();
	private static boolean available;

	private ServerNoteCache() {
	}

	/**
	 * Whether the current server has Notes installed, so server notes and sharing can be used.
	 */
	public static boolean isAvailable() {
		return available;
	}

	public static void reset(List<String> knownPlayers) {
		clear();
		KNOWN_PLAYERS.addAll(knownPlayers);
		available = true;
	}

	public static void clear() {
		if (Notes.pinnedNote instanceof ServerNote) {
			Notes.pinnedNote = null;
		}

		NOTES.clear();
		KNOWN_PLAYERS.clear();
		available = false;
	}

	public static List<ServerNote> getNotes() {
		return new ArrayList<>(NOTES.values());
	}

	public static ServerNote get(UUID id) {
		return NOTES.get(id);
	}

	public static List<String> getKnownPlayers() {
		return KNOWN_PLAYERS;
	}

	public static ServerNote upsert(ServerNoteData data) {
		ServerNote note = NOTES.get(data.id());
		if (note != null) {
			note.apply(data);
		} else {
			note = new ServerNote(data);
			NOTES.put(data.id(), note);
		}
		return note;
	}

	public static void remove(UUID id) {
		ServerNote removed = NOTES.remove(id);
		if (removed != null && removed.isPinned()) {
			Notes.pinnedNote = null;
		}
	}

}
