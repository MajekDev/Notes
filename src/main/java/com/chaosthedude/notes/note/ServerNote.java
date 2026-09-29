package com.chaosthedude.notes.note;

import java.util.UUID;

import com.chaosthedude.notes.Notes;
import com.chaosthedude.notes.network.NotesClientNetworking;
import com.chaosthedude.notes.network.ServerNoteData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

/**
 * A note stored on the server instead of in a local file. Instances held by {@link ServerNoteCache} are updated in
 * place when the server sends changes, so a pinned server note stays current.
 */
public class ServerNote extends Note {

	private final UUID id;
	private String author;
	private String lastEditor;
	private long lastModified;

	public ServerNote(ServerNoteData data) {
		id = data.id();
		scope = Scope.SERVER;
		apply(data);
	}

	public static ServerNote create(String title, String text) {
		String playerName = Minecraft.getInstance().getUser().getName();
		return new ServerNote(new ServerNoteData(UUID.randomUUID(), title, text, playerName, playerName, System.currentTimeMillis()));
	}

	public void apply(ServerNoteData data) {
		title = data.title();
		rawText = data.text();
		author = data.author();
		lastEditor = data.lastEditor();
		lastModified = data.lastModified();
	}

	public ServerNoteData toData() {
		return new ServerNoteData(id, title, rawText, author, lastEditor, lastModified);
	}

	/**
	 * Returns a detached copy for the editor, so unsaved edits don't leak into the shared cache.
	 */
	public ServerNote copyForEditing() {
		return new ServerNote(toData());
	}

	public UUID getId() {
		return id;
	}

	public String getAuthor() {
		return author;
	}

	@Override
	public void updateSaveFile() {
		// Server notes have no save file
	}

	@Override
	public void updateSaveDir() {
		// Server notes have no save directory
	}

	@Override
	public long getLastModified() {
		return lastModified;
	}

	@Override
	public String getLastModifiedString() {
		return super.getLastModifiedString() + " " + I18n.get("notes.byPlayer", lastEditor);
	}

	@Override
	public String getUncollidingSaveName(String name) {
		return name;
	}

	@Override
	public void save() {
		lastEditor = Minecraft.getInstance().getUser().getName();
		lastModified = System.currentTimeMillis();
		NotesClientNetworking.saveServerNote(this);
		// Apply locally right away; the server's broadcast will overwrite this with the authoritative copy
		ServerNoteCache.upsert(toData());
	}

	@Override
	public boolean tryOpenExternal() {
		// There's no local file to open, so always use the in-game editor and viewer
		return false;
	}

	@Override
	public void copy() {
		ServerNote.create(title + " Copy", rawText).save();
	}

	@Override
	public void delete() {
		if (isPinned()) {
			Notes.pinnedNote = null;
		}

		NotesClientNetworking.deleteServerNote(id);
		ServerNoteCache.remove(id);
	}

	@Override
	public void update() {
		ServerNote cached = ServerNoteCache.get(id);
		if (cached != null && cached != this) {
			apply(cached.toData());
		}
	}

	@Override
	public boolean equals(Note note) {
		return note instanceof ServerNote serverNote && id.equals(serverNote.id);
	}

	@Override
	public boolean isValidScope() {
		return ServerNoteCache.get(id) != null;
	}

}
