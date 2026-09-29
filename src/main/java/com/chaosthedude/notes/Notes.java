package com.chaosthedude.notes;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.chaosthedude.notes.config.NotesConfig;
import com.chaosthedude.notes.gui.SelectNoteScreen;
import com.chaosthedude.notes.note.Note;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class Notes implements ClientModInitializer {

	public static final String MODID = "notes";

	public static final Logger LOGGER = LogManager.getLogger(MODID);

	public static Note pinnedNote;

	private static KeyMapping openNotes;

	@Override
	public void onInitializeClient() {
		NotesConfig.load();
		
		// The category must be registered (not just constructed) or the key won't be sorted into the Controls screen
		openNotes = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.openNotes", InputConstants.KEY_N, KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MODID, "keys"))));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
		    while (openNotes.consumeClick()) {
		    	client.gui.setScreen(new SelectNoteScreen(client.gui.screen()));
		    }
		});
	}

}
