package com.chaosthedude.notes.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import com.chaosthedude.notes.network.NotesClientNetworking;
import com.chaosthedude.notes.note.Note;
import com.chaosthedude.notes.note.ServerNoteCache;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * Picks who to send a copy of a note to. Offline players who have joined the server before can be picked too; the
 * server delivers the note the next time they join.
 */
public class SharePlayerScreen extends Screen {

	private static final int PLAYER_BUTTON_WIDTH = 100;
	private static final int PLAYER_BUTTON_SPACING = 5;

	private final Screen parentScreen;
	private final Note note;
	private NotesTitleField nameField;
	private NotesButton sendButton;

	public SharePlayerScreen(Screen parentScreen, Note note) {
		super(Component.translatable("notes.shareNote", note.getTitle()));
		this.parentScreen = parentScreen;
		this.note = note;
	}

	@Override
	public void init() {
		sendButton = addRenderableWidget(new NotesButton(10, 40, 110, 20, Component.translatable("notes.send"), (onPress) -> {
			send();
		}));
		addRenderableWidget(new NotesButton(10, height - 30, 110, 20, Component.translatable("gui.cancel"), (onPress) -> {
			onClose();
		}));

		nameField = addRenderableWidget(new NotesTitleField(font, 130, 40, width - 140, 20, Component.empty()));
		nameField.setHint(Component.translatable("notes.playerName"));
		nameField.setResponder(value -> updateSendButton());
		setInitialFocus(nameField);
		updateSendButton();

		setupPlayerButtons();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parentScreen);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
		super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
		guiGraphics.centeredText(font, title.getString(), width / 2 + 60, 15, 0xffffffff);
		guiGraphics.text(font, I18n.get("notes.orPickPlayer"), 130, 70, 0xff808080);
	}

	private void setupPlayerButtons() {
		final int columns = Math.max(1, (width - 140 + PLAYER_BUTTON_SPACING) / (PLAYER_BUTTON_WIDTH + PLAYER_BUTTON_SPACING));
		final int top = 85;
		final int maxRows = Math.max(0, (height - 40 - top) / (20 + PLAYER_BUTTON_SPACING));

		int index = 0;
		for (PlayerOption player : getPlayerOptions()) {
			if (index >= columns * maxRows) {
				// Too many to show; the rest can still be typed in
				break;
			}

			final int x = 130 + (index % columns) * (PLAYER_BUTTON_WIDTH + PLAYER_BUTTON_SPACING);
			final int y = top + (index / columns) * (20 + PLAYER_BUTTON_SPACING);
			addRenderableWidget(new NotesButton(x, y, PLAYER_BUTTON_WIDTH, 20, player.label(), (onPress) -> {
				nameField.setValue(player.name());
			}));
			index++;
		}
	}

	/**
	 * Online players first, then offline players the server knows about, excluding this player.
	 */
	private List<PlayerOption> getPlayerOptions() {
		final String self = minecraft.getUser().getName().toLowerCase(Locale.ROOT);
		final Set<String> online = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		if (minecraft.getConnection() != null) {
			for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
				online.add(info.getProfile().name());
			}
		}

		final Set<String> offline = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		offline.addAll(ServerNoteCache.getKnownPlayers());
		offline.removeAll(online);

		final List<PlayerOption> options = new ArrayList<>();
		for (String name : online) {
			if (!name.toLowerCase(Locale.ROOT).equals(self)) {
				options.add(new PlayerOption(name, true));
			}
		}
		for (String name : offline) {
			if (!name.toLowerCase(Locale.ROOT).equals(self)) {
				options.add(new PlayerOption(name, false));
			}
		}
		return options;
	}

	private record PlayerOption(String name, boolean online) {
		Component label() {
			return online ? Component.literal(name) : Component.literal(name).append(Component.translatable("notes.offlineSuffix").withColor(0xff808080));
		}
	}

	private void updateSendButton() {
		sendButton.active = !nameField.getValue().isBlank();
	}

	private void send() {
		final String name = nameField.getValue().trim();
		if (name.isEmpty() || !NotesClientNetworking.checkFits(note)) {
			return;
		}

		NotesClientNetworking.shareNote(name, note);
		onClose();
	}

}
