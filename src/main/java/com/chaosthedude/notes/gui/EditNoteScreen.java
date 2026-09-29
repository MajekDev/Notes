package com.chaosthedude.notes.gui;

import java.util.ArrayList;
import java.util.List;

import com.chaosthedude.notes.Notes;
import com.chaosthedude.notes.network.NotesClientNetworking;
import com.chaosthedude.notes.note.Note;
import com.chaosthedude.notes.note.Scope;
import com.chaosthedude.notes.note.ServerNote;
import com.chaosthedude.notes.note.ServerNoteCache;
import com.chaosthedude.notes.util.StringUtils;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

public class EditNoteScreen extends Screen {

	private final Screen parentScreen;
	private NotesButton saveButton;
	private NotesButton scopeButton;
	private NotesButton insertBiomeButton;
	private NotesButton insertChunkButton;
	private NotesButton insertCoordsButton;
	private NotesButton cancelButton;
	private NotesTitleField noteTitleField;
	private NotesTextField noteTextField;
	private String saveDirName;
	private boolean newNote;
	private Note note;
	private Scope scope;
	private boolean pinned;
	private boolean setTextFieldFocused;

	public EditNoteScreen(Screen parentScreen, Note note) {
		super(Component.literal(note != null ? I18n.get("notes.editNote") : I18n.get("notes.newNote")));
		this.parentScreen = parentScreen;
		if (note != null) {
			// Edit a copy of server notes so unsaved changes don't show up in the shared cache
			this.note = note instanceof ServerNote serverNote ? serverNote.copyForEditing() : note;
			newNote = false;
		} else {
			this.note = new Note("New Note", "", Scope.getCurrentScope());
			newNote = true;
		}

		scope = this.note.getScope();
		pinned = this.note.isPinned();
		setTextFieldFocused = false;
	}

	@Override
	public void init() {
		setupTextFields();
		setupButtons();
	}

	@Override
	public void tick() {
		insertBiomeButton.active = insertChunkButton.active = insertCoordsButton.active = noteTextField.isFocused();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		boolean ret = super.mouseClicked(event, doubleClick);
		if (setTextFieldFocused) {
			// Change focus back to the text field after clicking the biome, coords, or chunk button
			setFocused(noteTextField);
			setTextFieldFocused = false;
		}
		return ret;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		boolean ret = super.keyPressed(event);
		updateNoteText();
		return ret;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		boolean ret = super.charTyped(event);
		updateNoteText();
		return ret;
	}

	@Override
	public void setFocused(GuiEventListener listener) {
		super.setFocused(listener);
		if (listener != noteTextField && noteTextField != null) {
			noteTextField.setFocused(false);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
		super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
		guiGraphics.centeredText(font, title.getString(), width / 2 + 60, 15, 0xffffffff);
		final String saveName = scope == Scope.SERVER ? note.getTitle() : note.getUncollidingSaveName(note.getTitle());
		guiGraphics.centeredText(font, I18n.get("notes.saveAs", saveName), width / 2 + 55, 65, 0xffaaaaaa);
	}

	private void setupButtons() {
		saveButton = addRenderableWidget(new NotesButton(10, 40, 110, 20, Component.translatable("notes.save"), (onPress) -> {
			updateNoteText();
			final Note savedNote = saveNote();
			if (savedNote == null) {
				return;
			}

			minecraft.gui.setScreen(new ViewNoteScreen(parentScreen, savedNote));
			if (pinned) {
				Notes.pinnedNote = savedNote;
			}
		}));
		scopeButton = addRenderableWidget(new NotesButton(10, 65, 110, 20, getScopeMessage(), (onPress) -> {
			final List<Scope> scopes = getAvailableScopes();
			scope = scopes.get((scopes.indexOf(scope) + 1) % scopes.size());
			scopeButton.setMessage(getScopeMessage());
		}));
		insertBiomeButton = addRenderableWidget(new NotesButton(10, 100, 110, 20, Component.translatable("notes.biome"), (onPress) -> {
			insertBiome();
			setTextFieldFocused = true;
		}));
		insertChunkButton = addRenderableWidget(new NotesButton(10, 125, 110, 20, Component.translatable("notes.chunk"), (onPress) -> {
			insertChunk();
			setTextFieldFocused = true;
		}));
		insertCoordsButton = addRenderableWidget(new NotesButton(10, 150, 110, 20, Component.translatable("notes.coordinates"), (onPress) -> {
			insertCoords();
			setTextFieldFocused = true;
		}));
		cancelButton = addRenderableWidget(new NotesButton(10, height - 30, 110, 20, Component.translatable("gui.cancel"), (onPress) -> {
			minecraft.gui.setScreen(parentScreen);
		}));

		insertBiomeButton.active = false;
		insertChunkButton.active = false;
		insertCoordsButton.active = false;
	}

	private void setupTextFields() {
		noteTitleField = addRenderableWidget(new NotesTitleField(font, 130, 40, width - 140, 20, Component.literal("")));
		noteTitleField.setValue(note.getTitle());
		if (newNote) {
			setFocused(noteTitleField);
		}

		noteTextField = addRenderableWidget(new NotesTextField(font, 130, 85, width - 140, height - 95));
		noteTextField.setValue(note.getFilteredText());
		noteTextField.setScrollAmount(0);
		if (!newNote) {
			setFocused(noteTextField);
		}
	}

	/**
	 * Saves the note in the selected scope, moving it between a local file and the server if the scope changed.
	 * Returns the saved note, or null if it couldn't be saved.
	 */
	private Note saveNote() {
		if (scope == Scope.SERVER) {
			if (!NotesClientNetworking.checkFits(note)) {
				return null;
			}

			final ServerNote serverNote;
			if (note instanceof ServerNote existing) {
				serverNote = existing;
			} else {
				serverNote = ServerNote.create(note.getTitle(), note.getRawText());
				if (!newNote) {
					note.getPrevSaveFile().delete();
				}
			}
			serverNote.save();
			return ServerNoteCache.get(serverNote.getId());
		}

		if (note instanceof ServerNote serverNote) {
			final Note localNote = new Note(serverNote.getTitle(), serverNote.getRawText(), scope);
			localNote.save();
			serverNote.delete();
			return localNote;
		}

		note.setScope(scope);
		note.save();
		return note;
	}

	/**
	 * The scopes the scope button cycles through: this world or server, global, and shared server notes if the
	 * server supports them.
	 */
	private List<Scope> getAvailableScopes() {
		final List<Scope> scopes = new ArrayList<>();
		if (Scope.currentScopeIsValid()) {
			scopes.add(Scope.getCurrentScope());
		}
		scopes.add(Scope.GLOBAL);
		if (ServerNoteCache.isAvailable()) {
			scopes.add(Scope.SERVER);
		}
		return scopes;
	}

	private Component getScopeMessage() {
		return Component.translatable("notes.scope").append(": ").append(Component.translatable(scope.getUnlocalizedName()));
	}

	private void updateNoteText() {
		note.setTitle(noteTitleField.getValue());
		note.setText(noteTextField.getValue());
	}

	private void insertBiome() {
		noteTextField.insertText(StringUtils.fixBiomeName(minecraft.level, minecraft.level.getBiome(minecraft.player.blockPosition()).value()));
	}

	private void insertChunk() {
		noteTextField.insertText((int) minecraft.player.chunkPosition().x() + ", " + (int) minecraft.player.chunkPosition().z());
	}

	private void insertCoords() {
		noteTextField.insertText((int) minecraft.player.getBlockX() + ", " + (int) minecraft.player.getBlockY() + ", " + (int) minecraft.player.getBlockZ());
	}

}
