package com.chaosthedude.notes.server;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.chaosthedude.notes.NotesCommon;
import com.chaosthedude.notes.network.ServerNoteData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Persists server notes, pending shares, and known players under {@code <world>/notes/}.
 *
 * <p>Server notes are stored one per file, named by id rather than title, so player-supplied titles never touch the
 * file system.
 */
public class ServerNoteStore {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Type INBOX_TYPE = new TypeToken<Map<UUID, List<PendingShare>>>() {}.getType();
	private static final Type PLAYERS_TYPE = new TypeToken<List<KnownPlayer>>() {}.getType();

	private final Path notesDir;
	private final Path inboxFile;
	private final Path playersFile;

	private final Map<UUID, ServerNoteData> notes = new LinkedHashMap<>();
	private final Map<UUID, List<PendingShare>> inbox = new HashMap<>();
	// Keyed by lowercase name
	private final Map<String, KnownPlayer> knownPlayers = new HashMap<>();

	public record PendingShare(UUID id, String sender, UUID senderId, String title, String text, long created) {
	}

	public record KnownPlayer(UUID id, String name) {
	}

	public ServerNoteStore(Path rootDir) {
		notesDir = rootDir.resolve("server");
		inboxFile = rootDir.resolve("inbox.json");
		playersFile = rootDir.resolve("players.json");
	}

	public void load() {
		try {
			Files.createDirectories(notesDir);
			try (DirectoryStream<Path> files = Files.newDirectoryStream(notesDir, "*.json")) {
				for (Path file : files) {
					ServerNoteData note = read(file, ServerNoteData.class);
					if (note != null && note.id() != null) {
						notes.put(note.id(), note);
					}
				}
			}
		} catch (IOException e) {
			NotesCommon.LOGGER.error("Failed to load server notes from " + notesDir, e);
		}

		Map<UUID, List<PendingShare>> loadedInbox = read(inboxFile, INBOX_TYPE);
		if (loadedInbox != null) {
			loadedInbox.forEach((recipient, shares) -> inbox.put(recipient, new ArrayList<>(shares)));
		}

		List<KnownPlayer> loadedPlayers = read(playersFile, PLAYERS_TYPE);
		if (loadedPlayers != null) {
			for (KnownPlayer player : loadedPlayers) {
				knownPlayers.put(player.name().toLowerCase(Locale.ROOT), player);
			}
		}

		NotesCommon.LOGGER.info("Loaded " + notes.size() + " server notes");
	}

	// Server notes

	public Collection<ServerNoteData> getNotes() {
		return notes.values();
	}

	public ServerNoteData getNote(UUID id) {
		return notes.get(id);
	}

	public int getNoteCount() {
		return notes.size();
	}

	public void putNote(ServerNoteData note) {
		notes.put(note.id(), note);
		write(notesDir.resolve(note.id() + ".json"), note);
	}

	public boolean removeNote(UUID id) {
		if (notes.remove(id) == null) {
			return false;
		}

		try {
			Files.deleteIfExists(notesDir.resolve(id + ".json"));
		} catch (IOException e) {
			NotesCommon.LOGGER.error("Failed to delete server note " + id, e);
		}
		return true;
	}

	// Pending shares

	public List<PendingShare> getInbox(UUID recipient) {
		return inbox.getOrDefault(recipient, List.of());
	}

	public void addToInbox(UUID recipient, PendingShare share) {
		inbox.computeIfAbsent(recipient, id -> new ArrayList<>()).add(share);
		write(inboxFile, inbox);
	}

	public Optional<PendingShare> removeFromInbox(UUID recipient, UUID shareId) {
		List<PendingShare> shares = inbox.get(recipient);
		if (shares == null) {
			return Optional.empty();
		}

		Optional<PendingShare> removed = shares.stream().filter(share -> share.id().equals(shareId)).findFirst();
		if (removed.isPresent()) {
			shares.remove(removed.get());
			if (shares.isEmpty()) {
				inbox.remove(recipient);
			}
			write(inboxFile, inbox);
		}
		return removed;
	}

	// Known players

	public Optional<KnownPlayer> findPlayer(String name) {
		return Optional.ofNullable(knownPlayers.get(name.toLowerCase(Locale.ROOT)));
	}

	public List<String> getKnownPlayerNames() {
		return knownPlayers.values().stream().map(KnownPlayer::name).sorted(String.CASE_INSENSITIVE_ORDER).toList();
	}

	public void rememberPlayer(UUID id, String name) {
		KnownPlayer player = new KnownPlayer(id, name);
		if (player.equals(knownPlayers.get(name.toLowerCase(Locale.ROOT)))) {
			return;
		}

		// Drop the old entry if this player changed their name
		knownPlayers.values().removeIf(known -> known.id().equals(id));
		knownPlayers.put(name.toLowerCase(Locale.ROOT), player);
		write(playersFile, new ArrayList<>(knownPlayers.values()));
	}

	// File helpers

	private static <T> T read(Path file, Type type) {
		if (!Files.exists(file)) {
			return null;
		}

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return GSON.fromJson(reader, type);
		} catch (Exception e) {
			NotesCommon.LOGGER.error("Failed to read " + file, e);
			return null;
		}
	}

	private static void write(Path file, Object data) {
		try {
			Files.createDirectories(file.getParent());
			// Write to a temp file first so a crash mid-write can't corrupt the existing file
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(data), StandardCharsets.UTF_8);
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			NotesCommon.LOGGER.error("Failed to write " + file, e);
		}
	}

}
