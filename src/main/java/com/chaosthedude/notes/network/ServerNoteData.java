package com.chaosthedude.notes.network;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import io.netty.buffer.ByteBuf;

/**
 * A note stored on the server and visible to every player. Also used as the on-disk format for server notes.
 */
public record ServerNoteData(UUID id, String title, String text, String author, String lastEditor, long lastModified) {

	public static final StreamCodec<ByteBuf, ServerNoteData> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, ServerNoteData::id,
			ByteBufCodecs.stringUtf8(NotesNetworking.MAX_TITLE_LENGTH), ServerNoteData::title,
			ByteBufCodecs.stringUtf8(NotesNetworking.MAX_TEXT_LENGTH), ServerNoteData::text,
			ByteBufCodecs.stringUtf8(NotesNetworking.MAX_PLAYER_NAME_LENGTH), ServerNoteData::author,
			ByteBufCodecs.stringUtf8(NotesNetworking.MAX_PLAYER_NAME_LENGTH), ServerNoteData::lastEditor,
			ByteBufCodecs.LONG, ServerNoteData::lastModified,
			ServerNoteData::new);

}
