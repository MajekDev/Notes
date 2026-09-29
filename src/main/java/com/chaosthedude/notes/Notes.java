package com.chaosthedude.notes;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import org.apache.logging.log4j.Logger;

import com.chaosthedude.notes.config.NotesConfig;
import com.chaosthedude.notes.gui.IncomingShareScreen;
import com.chaosthedude.notes.gui.SelectNoteScreen;
import com.chaosthedude.notes.network.NotesClientNetworking;
import com.chaosthedude.notes.note.Note;
import com.chaosthedude.notes.note.PendingShares;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

public class Notes implements ClientModInitializer {

	public static final String MODID = NotesCommon.MODID;

	public static final Logger LOGGER = NotesCommon.LOGGER;

	public static Note pinnedNote;

	private static KeyMapping openNotes;

	@Override
	public void onInitializeClient() {
		NotesConfig.load();
		NotesClientNetworking.init();

		// The category must be registered (not just constructed) or the key won't be sorted into the Controls screen
		openNotes = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.openNotes", InputConstants.KEY_N, KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MODID, "keys"))));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
		    while (openNotes.consumeClick()) {
		    	Screen screen = new SelectNoteScreen(client.gui.screen());
		    	// Show newly shared notes first, but only once so "Later" doesn't keep popping up
		    	if (PendingShares.hasUnseen()) {
		    		PendingShares.markAllSeen();
		    		screen = new IncomingShareScreen(screen);
		    	}
		    	client.gui.setScreen(screen);
		    }
		});
	}

}
