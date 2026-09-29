package com.chaosthedude.notes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.chaosthedude.notes.network.NotesNetworking;
import com.chaosthedude.notes.server.NotesServer;

import net.fabricmc.api.ModInitializer;

/**
 * Runs on both the client and the server. Must not reference client-only classes.
 */
public class NotesCommon implements ModInitializer {

	public static final String MODID = "notes";

	public static final Logger LOGGER = LogManager.getLogger(MODID);

	@Override
	public void onInitialize() {
		NotesNetworking.registerPayloads();
		NotesServer.init();
	}

}
