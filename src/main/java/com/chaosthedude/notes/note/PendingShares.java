package com.chaosthedude.notes.note;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Notes other players have shared with this player that haven't been accepted or declined yet. The server keeps these
 * until a response is sent, and resends them on every join. Only accessed on the render thread.
 */
public final class PendingShares {

	private static final List<PendingShare> SHARES = new ArrayList<>();

	public static final class PendingShare {
		private final UUID id;
		private final String sender;
		private final String title;
		private final String text;
		private boolean seen;

		public PendingShare(UUID id, String sender, String title, String text) {
			this.id = id;
			this.sender = sender;
			this.title = title;
			this.text = text;
		}

		public UUID getId() {
			return id;
		}

		public String getSender() {
			return sender;
		}

		public String getTitle() {
			return title;
		}

		public String getText() {
			return text;
		}
	}

	private PendingShares() {
	}

	public static void add(PendingShare share) {
		if (SHARES.stream().noneMatch(existing -> existing.id.equals(share.id))) {
			SHARES.add(share);
		}
	}

	public static void remove(PendingShare share) {
		SHARES.remove(share);
	}

	public static void clear() {
		SHARES.clear();
	}

	public static boolean isEmpty() {
		return SHARES.isEmpty();
	}

	public static int size() {
		return SHARES.size();
	}

	public static PendingShare peek() {
		return SHARES.isEmpty() ? null : SHARES.get(0);
	}

	/**
	 * Whether any share arrived that the player hasn't been shown yet.
	 */
	public static boolean hasUnseen() {
		return SHARES.stream().anyMatch(share -> !share.seen);
	}

	public static void markAllSeen() {
		SHARES.forEach(share -> share.seen = true);
	}

}
