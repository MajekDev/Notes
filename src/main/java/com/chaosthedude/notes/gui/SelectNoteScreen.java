package com.chaosthedude.notes.gui;

import com.chaosthedude.notes.network.NotesClientNetworking;
import com.chaosthedude.notes.note.PendingShares;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

public class SelectNoteScreen extends Screen {

	private Screen prevScreen;
	private NotesButton newButton;
	private NotesButton viewButton;
	private NotesButton editButton;
	private NotesButton copyButton;
	private NotesButton deleteButton;
	private NotesButton pinButton;
	private NotesButton shareButton;
	private NotesButton inboxButton;
	private NotesButton cancelButton;
	private NotesList selectionList;

	public SelectNoteScreen(Screen prevScreen) {
		super(Component.translatable("notes.selectNote"));
		this.prevScreen = prevScreen;
	}

	@Override
	public void init() {
		setupButtons();
		selectionList = addRenderableWidget(new NotesList(this, minecraft, 130, 40, width - 140, height - 50, 40));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
		super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
		guiGraphics.centeredText(font, I18n.get("notes.selectNote"), 130 + ((width - 140) / 2), 15, 0xffffffff);
	}

	@Override
	public void tick() {
		if (selectionList.getSelected() != null) {
			pinButton.setMessage(selectionList.getSelected().isPinned() ? Component.translatable("notes.unpin") : Component.translatable("notes.pin"));
		}

		shareButton.active = selectionList.getSelected() != null && NotesClientNetworking.canShare();
		inboxButton.visible = !PendingShares.isEmpty();
		inboxButton.setMessage(Component.translatable("notes.inbox", PendingShares.size()));
	}

	public void selectNote(NotesListEntry entry) {
		final boolean enable = entry != null;
		viewButton.active = enable;
		deleteButton.active = enable;
		editButton.active = enable;
		copyButton.active = enable;
		pinButton.active = enable;
		shareButton.active = enable && NotesClientNetworking.canShare();
	}

	private void setupButtons() {
		newButton = addRenderableWidget(new NotesButton(10, 40, 110, 20, Component.translatable("notes.new"), (onPress) -> {
			minecraft.gui.setScreen(new EditNoteScreen(SelectNoteScreen.this, null));
		}));
		viewButton = addRenderableWidget(new NotesButton(10, 75, 110, 20, Component.translatable("notes.view"), (onPress) -> {
			NotesListEntry notesEntry = SelectNoteScreen.this.selectionList.getSelected();
			if (notesEntry != null) {
				notesEntry.loadNote();
			}
		}));
		editButton = addRenderableWidget(new NotesButton(10, 100, 110, 20, Component.translatable("notes.edit"), (onPress) -> {
			NotesListEntry notesEntry = SelectNoteScreen.this.selectionList.getSelected();
			if (notesEntry != null) {
				notesEntry.editNote();
			}
		}));
		copyButton = addRenderableWidget(new NotesButton(10, 125, 110, 20, Component.translatable("notes.copy"), (onPress) -> {
			NotesListEntry notesEntry = SelectNoteScreen.this.selectionList.getSelected();
			notesEntry.copyNote();
		}));
		deleteButton = addRenderableWidget(new NotesButton(10, 150, 110, 20, Component.translatable("notes.delete"), (onPress) -> {
			NotesListEntry notesEntry = SelectNoteScreen.this.selectionList.getSelected();
			if (notesEntry != null) {
				notesEntry.deleteNote();
			}
		}));
		pinButton = addRenderableWidget(new NotesButton(10, 175, 110, 20, Component.translatable("notes.pin"), (onPress) -> {
			NotesListEntry notesEntry = SelectNoteScreen.this.selectionList.getSelected();
			if (notesEntry != null) {
				notesEntry.togglePin();
				selectionList.refreshList();
			}
		}));
		shareButton = addRenderableWidget(new NotesButton(10, 200, 110, 20, Component.translatable("notes.share"), (onPress) -> {
			NotesListEntry notesEntry = SelectNoteScreen.this.selectionList.getSelected();
			if (notesEntry != null && NotesClientNetworking.checkFits(notesEntry.getNote())) {
				minecraft.gui.setScreen(new SharePlayerScreen(SelectNoteScreen.this, notesEntry.getNote()));
			}
		}));
		inboxButton = addRenderableWidget(new NotesButton(10, 10, 110, 20, Component.translatable("notes.inbox", PendingShares.size()), (onPress) -> {
			minecraft.gui.setScreen(new IncomingShareScreen(SelectNoteScreen.this));
		}));
		cancelButton = addRenderableWidget(new NotesButton(10, height - 30, 110, 20, Component.translatable("gui.cancel"), (onPress) -> {
			minecraft.gui.setScreen(prevScreen);
		}));

		viewButton.active = false;
		deleteButton.active = false;
		editButton.active = false;
		copyButton.active = false;
		pinButton.active = false;
		shareButton.active = false;
		inboxButton.visible = !PendingShares.isEmpty();
	}

}
