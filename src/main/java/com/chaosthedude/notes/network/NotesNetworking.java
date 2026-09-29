package com.chaosthedude.notes.network;

import java.util.List;
import java.util.UUID;

import com.chaosthedude.notes.NotesCommon;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import io.netty.buffer.ByteBuf;

/**
 * Payloads shared by the client and server. Registration must happen on both sides.
 */
public final class NotesNetworking {

	public static final int MAX_TITLE_LENGTH = 128;
	public static final int MAX_TEXT_LENGTH = 65536;
	public static final int MAX_PLAYER_NAME_LENGTH = 64;
	public static final int MAX_KNOWN_PLAYERS = 1024;

	// Payloads that carry note text can exceed the vanilla 32 KiB serverbound limit, so they are registered as large payloads
	private static final int MAX_LARGE_PAYLOAD_SIZE = 512 * 1024;

	private NotesNetworking() {
	}

	public static boolean fitsInPayload(String title, String text) {
		return title.length() <= MAX_TITLE_LENGTH && text.length() <= MAX_TEXT_LENGTH;
	}

	public static void registerPayloads() {
		PayloadTypeRegistry.serverboundPlay().registerLarge(ShareNote.TYPE, ShareNote.STREAM_CODEC, MAX_LARGE_PAYLOAD_SIZE);
		PayloadTypeRegistry.serverboundPlay().register(RespondToShare.TYPE, RespondToShare.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().registerLarge(SaveServerNote.TYPE, SaveServerNote.STREAM_CODEC, MAX_LARGE_PAYLOAD_SIZE);
		PayloadTypeRegistry.serverboundPlay().register(DeleteServerNote.TYPE, DeleteServerNote.STREAM_CODEC);

		PayloadTypeRegistry.clientboundPlay().register(ServerHello.TYPE, ServerHello.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().registerLarge(IncomingShare.TYPE, IncomingShare.STREAM_CODEC, MAX_LARGE_PAYLOAD_SIZE);
		PayloadTypeRegistry.clientboundPlay().registerLarge(ServerNoteUpdated.TYPE, ServerNoteUpdated.STREAM_CODEC, MAX_LARGE_PAYLOAD_SIZE);
		PayloadTypeRegistry.clientboundPlay().register(ServerNoteDeleted.TYPE, ServerNoteDeleted.STREAM_CODEC);
	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> createType(String path) {
		return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(NotesCommon.MODID, path));
	}

	// Client -> server

	/** Share a copy of a note with another player, by name. The player may be offline. */
	public record ShareNote(String recipient, String title, String text) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ShareNote> TYPE = createType("share_note");
		public static final StreamCodec<ByteBuf, ShareNote> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(MAX_PLAYER_NAME_LENGTH), ShareNote::recipient,
				ByteBufCodecs.stringUtf8(MAX_TITLE_LENGTH), ShareNote::title,
				ByteBufCodecs.stringUtf8(MAX_TEXT_LENGTH), ShareNote::text,
				ShareNote::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Accept or decline a note another player shared. Removes it from the recipient's server-side inbox. */
	public record RespondToShare(UUID shareId, boolean accepted) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<RespondToShare> TYPE = createType("respond_to_share");
		public static final StreamCodec<ByteBuf, RespondToShare> STREAM_CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, RespondToShare::shareId,
				ByteBufCodecs.BOOL, RespondToShare::accepted,
				RespondToShare::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Create or update a server note. The server fills in the author, last editor, and timestamp. */
	public record SaveServerNote(UUID id, String title, String text) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<SaveServerNote> TYPE = createType("save_server_note");
		public static final StreamCodec<ByteBuf, SaveServerNote> STREAM_CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, SaveServerNote::id,
				ByteBufCodecs.stringUtf8(MAX_TITLE_LENGTH), SaveServerNote::title,
				ByteBufCodecs.stringUtf8(MAX_TEXT_LENGTH), SaveServerNote::text,
				SaveServerNote::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record DeleteServerNote(UUID id) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<DeleteServerNote> TYPE = createType("delete_server_note");
		public static final StreamCodec<ByteBuf, DeleteServerNote> STREAM_CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, DeleteServerNote::id,
				DeleteServerNote::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	// Server -> client

	/**
	 * Sent when a player joins. Tells the client the server supports sharing, resets its server note cache, and lists
	 * players who have joined before so offline players can be picked when sharing. Server notes follow as
	 * {@link ServerNoteUpdated} payloads.
	 */
	public record ServerHello(List<String> knownPlayers) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ServerHello> TYPE = createType("server_hello");
		public static final StreamCodec<ByteBuf, ServerHello> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(MAX_PLAYER_NAME_LENGTH).apply(ByteBufCodecs.list(MAX_KNOWN_PLAYERS)), ServerHello::knownPlayers,
				ServerHello::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record IncomingShare(UUID shareId, String sender, String title, String text) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<IncomingShare> TYPE = createType("incoming_share");
		public static final StreamCodec<ByteBuf, IncomingShare> STREAM_CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, IncomingShare::shareId,
				ByteBufCodecs.stringUtf8(MAX_PLAYER_NAME_LENGTH), IncomingShare::sender,
				ByteBufCodecs.stringUtf8(MAX_TITLE_LENGTH), IncomingShare::title,
				ByteBufCodecs.stringUtf8(MAX_TEXT_LENGTH), IncomingShare::text,
				IncomingShare::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record ServerNoteUpdated(ServerNoteData note) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ServerNoteUpdated> TYPE = createType("server_note_updated");
		public static final StreamCodec<ByteBuf, ServerNoteUpdated> STREAM_CODEC = StreamCodec.composite(
				ServerNoteData.STREAM_CODEC, ServerNoteUpdated::note,
				ServerNoteUpdated::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record ServerNoteDeleted(UUID id) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ServerNoteDeleted> TYPE = createType("server_note_deleted");
		public static final StreamCodec<ByteBuf, ServerNoteDeleted> STREAM_CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, ServerNoteDeleted::id,
				ServerNoteDeleted::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

}
