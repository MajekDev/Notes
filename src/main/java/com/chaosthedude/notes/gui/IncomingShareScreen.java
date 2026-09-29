package com.chaosthedude.notes.gui;

import java.util.List;

import com.chaosthedude.notes.network.NotesClientNetworking;
import com.chaosthedude.notes.note.Note;
import com.chaosthedude.notes.note.PendingShares;
import com.chaosthedude.notes.note.PendingShares.PendingShare;
import com.chaosthedude.notes.note.Scope;
import com.chaosthedude.notes.util.RenderUtils;
import com.chaosthedude.notes.util.StringUtils;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * Lets the player accept or decline notes other players shared with them, one at a time.
 */
public class IncomingShareScreen extends Screen {

	private final Screen parentScreen;
	private final PendingShare share;

	public IncomingShareScreen(Screen parentScreen) {
		super(Component.translatable("notes.incomingShares"));
		this.parentScreen = parentScreen;
		share = PendingShares.peek();
	}

	@Override
	public void init() {
		if (share == null) {
			return;
		}

		addRenderableWidget(new NotesButton(10, 40, 110, 20, Component.translatable("notes.accept"), (onPress) -> {
			new Note(share.getTitle(), share.getText(), Scope.getCurrentScope()).save();
			NotesClientNetworking.showToast(Component.translatable("notes.toast.saved"), Component.literal(share.getTitle()));
			respond(true);
		}));
		addRenderableWidget(new NotesButton(10, 65, 110, 20, Component.translatable("notes.decline"), (onPress) -> {
			respond(false);
		}));
		addRenderableWidget(new NotesButton(10, height - 30, 110, 20, Component.translatable("notes.later"), (onPress) -> {
			onClose();
		}));
	}

	@Override
	public void tick() {
		// Nothing to review, e.g. the last share was handled from another screen
		if (share == null) {
			onClose();
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parentScreen);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
		super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
		if (share == null) {
			return;
		}

		guiGraphics.centeredText(font, I18n.get("notes.noteFrom", share.getSender()), width / 2 + 60, 15, 0xffffffff);
		guiGraphics.text(font, share.getTitle(), 130, 40, 0xffffffff);
		if (PendingShares.size() > 1) {
			guiGraphics.text(font, I18n.get("notes.moreWaiting", PendingShares.size() - 1), 10, 95, 0xff808080);
		}

		List<String> lines = RenderUtils.splitStringToWidthAndHeight(StringUtils.filter(share.getText()), width - 140, height - 95);
		RenderUtils.renderSplitString(guiGraphics, lines, 130, 55, 0xffaaaaaa);
	}

	private void respond(boolean accepted) {
		NotesClientNetworking.respondToShare(share, accepted);
		PendingShares.remove(share);
		minecraft.gui.setScreen(PendingShares.isEmpty() ? parentScreen : new IncomingShareScreen(parentScreen));
	}

}
