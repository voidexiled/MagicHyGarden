package com.voidexiled.magichygarden.features.farming.parcels;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.util.BsonUtil;
import com.voidexiled.magichygarden.features.farming.storage.MghgStoragePaths;
import org.bson.BsonDocument;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class MghgParcelInviteService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long INVITE_TTL_SECONDS = 15L * 60L;
    private static final long CLEANUP_INTERVAL_SECONDS = 60L;
    private static final long SAVE_DEBOUNCE_MILLIS = 500L;
    private static final Map<UUID, List<Invite>> PENDING_BY_TARGET = new HashMap<>();
    private static volatile ScheduledFuture<?> cleanupTask;
    private static volatile ScheduledFuture<?> pendingSave;

    private MghgParcelInviteService() {
    }

    public static synchronized void start() {
        load();
        startCleanupTask();
    }

    public static synchronized void stop() {
        stopCleanupTask();
        clearExpired();
        save();
    }

    public static synchronized void load() {
        PENDING_BY_TARGET.clear();

        Path primary = storePath();
        Path source = primary;
        BsonDocument document = Files.exists(primary) ? BsonUtil.readDocumentNow(primary) : null;
        if (document == null) {
            for (Path candidate : legacyCandidates(primary)) {
                if (candidate.equals(primary)) {
                    continue;
                }
                if (!Files.exists(candidate)) {
                    continue;
                }
                document = BsonUtil.readDocumentNow(candidate);
                if (document != null) {
                    source = candidate;
                    LOGGER.atInfo().log("[MGHG|PARCEL_INVITES] Loaded legacy invite store from %s", candidate);
                    break;
                }
            }
        }
        if (document == null) {
            return;
        }

        try {
            ExtraInfo extraInfo = new ExtraInfo();
            MghgParcelInviteState state = MghgParcelInviteState.CODEC.decode(document, extraInfo);
            extraInfo.getValidationResults().logOrThrowValidatorExceptions(LOGGER);

            long now = Instant.now().getEpochSecond();
            boolean droppedExpired = false;
            for (MghgParcelInviteState.Entry entry : state.getEntries()) {
                if (entry == null
                        || entry.getOwnerId() == null
                        || entry.getInviterId() == null
                        || entry.getTargetId() == null) {
                    continue;
                }
                Invite invite = new Invite(
                        entry.getOwnerId(),
                        safeName(entry.getOwnerName(), entry.getOwnerId()),
                        entry.getInviterId(),
                        safeName(entry.getInviterName(), entry.getInviterId()),
                        entry.getTargetId(),
                        safeName(entry.getTargetName(), entry.getTargetId()),
                        entry.getCreatedAtEpochSecond()
                );
                if (invite.isExpired(now)) {
                    droppedExpired = true;
                    continue;
                }
                upsertInvite(invite.targetId, invite);
            }

            if (!source.equals(primary) || droppedExpired) {
                save();
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|PARCEL_INVITES] Failed to decode invite store: %s", e.getMessage());
            PENDING_BY_TARGET.clear();
        }
    }

    public static synchronized void save() {
        try {
            BsonUtil.writeSync(storePath(), MghgParcelInviteState.CODEC, toState(), LOGGER);
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|PARCEL_INVITES] Failed to save invite store: %s", e.getMessage());
        }
    }

    public static Path getStorePath() {
        return storePath();
    }

    public static synchronized void createInvite(
            @Nonnull UUID ownerId,
            @Nonnull String ownerName,
            @Nonnull UUID inviterId,
            @Nonnull String inviterName,
            @Nonnull UUID targetId,
            @Nonnull String targetName
    ) {
        cleanupExpired(targetId);
        upsertInvite(targetId, new Invite(
                ownerId,
                ownerName,
                inviterId,
                inviterName,
                targetId,
                targetName,
                Instant.now().getEpochSecond()
        ));
        saveSoon();
    }

    public static synchronized @Nullable Invite acceptLatest(@Nonnull UUID targetId) {
        Invite invite = pollLatest(targetId);
        if (invite == null) {
            return null;
        }
        removeInvite(targetId, invite.ownerId);
        return invite;
    }

    public static synchronized @Nullable Invite denyLatest(@Nonnull UUID targetId) {
        Invite invite = pollLatest(targetId);
        if (invite == null) {
            return null;
        }
        removeInvite(targetId, invite.ownerId);
        return invite;
    }

    public static synchronized boolean removeInvite(@Nonnull UUID targetId, @Nonnull UUID ownerId) {
        cleanupExpired(targetId);
        List<Invite> pending = PENDING_BY_TARGET.get(targetId);
        if (pending == null || pending.isEmpty()) {
            return false;
        }
        boolean removed = pending.removeIf(invite -> ownerId.equals(invite.ownerId));
        if (pending.isEmpty()) {
            PENDING_BY_TARGET.remove(targetId);
        }
        if (removed) {
            saveSoon();
        }
        return removed;
    }

    public static synchronized @Nonnull List<Invite> getPending(@Nonnull UUID targetId) {
        cleanupExpired(targetId);
        List<Invite> pending = PENDING_BY_TARGET.get(targetId);
        if (pending == null || pending.isEmpty()) {
            return List.of();
        }
        List<Invite> copy = new ArrayList<>(pending);
        copy.sort(Comparator.comparingLong(invite -> -invite.createdAtEpochSecond));
        return copy;
    }

    public static synchronized int clearExpired() {
        int removed = 0;
        List<UUID> empty = new ArrayList<>();
        long now = Instant.now().getEpochSecond();
        for (Map.Entry<UUID, List<Invite>> entry : PENDING_BY_TARGET.entrySet()) {
            List<Invite> invites = entry.getValue();
            int before = invites.size();
            invites.removeIf(invite -> invite.isExpired(now));
            removed += Math.max(0, before - invites.size());
            if (invites.isEmpty()) {
                empty.add(entry.getKey());
            }
        }
        for (UUID key : empty) {
            PENDING_BY_TARGET.remove(key);
        }
        if (removed > 0) {
            saveSoon();
        }
        return removed;
    }

    private static void cleanupExpired(@Nonnull UUID targetId) {
        List<Invite> pending = PENDING_BY_TARGET.get(targetId);
        if (pending == null || pending.isEmpty()) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        int before = pending.size();
        pending.removeIf(invite -> invite.isExpired(now));
        if (pending.isEmpty()) {
            PENDING_BY_TARGET.remove(targetId);
        }
        if (before != pending.size()) {
            saveSoon();
        }
    }

    private static @Nullable Invite pollLatest(@Nonnull UUID targetId) {
        cleanupExpired(targetId);
        List<Invite> pending = PENDING_BY_TARGET.get(targetId);
        if (pending == null || pending.isEmpty()) {
            return null;
        }
        return pending.stream()
                .max(Comparator.comparingLong(invite -> invite.createdAtEpochSecond))
                .orElse(null);
    }

    private static void upsertInvite(@Nonnull UUID targetId, @Nonnull Invite invite) {
        List<Invite> pending = PENDING_BY_TARGET.computeIfAbsent(targetId, ignored -> new ArrayList<>());
        pending.removeIf(existing -> invite.ownerId.equals(existing.ownerId));
        pending.add(invite);
    }

    private static MghgParcelInviteState toState() {
        List<MghgParcelInviteState.Entry> entries = new ArrayList<>();
        for (List<Invite> invites : PENDING_BY_TARGET.values()) {
            if (invites == null || invites.isEmpty()) {
                continue;
            }
            for (Invite invite : invites) {
                if (invite == null) {
                    continue;
                }
                entries.add(new MghgParcelInviteState.Entry(
                        invite.ownerId,
                        invite.ownerName,
                        invite.inviterId,
                        invite.inviterName,
                        invite.targetId,
                        invite.targetName,
                        invite.createdAtEpochSecond
                ));
            }
        }
        return new MghgParcelInviteState(entries.toArray(new MghgParcelInviteState.Entry[0]));
    }

    private static void startCleanupTask() {
        stopCleanupTask();
        cleanupTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                MghgParcelInviteService::clearExpiredSafe,
                CLEANUP_INTERVAL_SECONDS,
                CLEANUP_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private static void stopCleanupTask() {
        ScheduledFuture<?> current = cleanupTask;
        cleanupTask = null;
        if (current != null) {
            current.cancel(false);
        }
        ScheduledFuture<?> save = pendingSave;
        pendingSave = null;
        if (save != null) {
            save.cancel(false);
        }
    }

    private static void clearExpiredSafe() {
        try {
            clearExpired();
        } catch (Exception e) {
            LOGGER.atWarning().log("[MGHG|PARCEL_INVITES] Failed to clear expired invites: %s", e.getMessage());
        }
    }

    private static synchronized void saveSoon() {
        ScheduledFuture<?> current = pendingSave;
        if (current != null && !current.isDone()) {
            return;
        }
        pendingSave = HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                save();
            } finally {
                pendingSave = null;
            }
        }, SAVE_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static String safeName(@Nullable String name, @Nonnull UUID fallbackId) {
        if (name == null || name.isBlank()) {
            return fallbackId.toString();
        }
        return name;
    }

    private static Path storePath() {
        return MghgStoragePaths.resolveInDataRoot("parcels", "invites.json");
    }

    private static List<Path> legacyCandidates(@Nonnull Path primary) {
        LinkedHashSet<Path> set = new LinkedHashSet<>();
        set.add(primary);
        set.addAll(MghgStoragePaths.legacyCandidates("parcels", "invites.json"));
        set.addAll(MghgStoragePaths.legacyCandidates("parcel_invites.json"));
        set.addAll(MghgStoragePaths.legacyCandidates("invites.json"));
        return new ArrayList<>(set);
    }

    public static final class Invite {
        private final UUID ownerId;
        private final String ownerName;
        private final UUID inviterId;
        private final String inviterName;
        private final UUID targetId;
        private final String targetName;
        private final long createdAtEpochSecond;

        private Invite(
                UUID ownerId,
                String ownerName,
                UUID inviterId,
                String inviterName,
                UUID targetId,
                String targetName,
                long createdAtEpochSecond
        ) {
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.inviterId = inviterId;
            this.inviterName = inviterName;
            this.targetId = targetId;
            this.targetName = targetName;
            this.createdAtEpochSecond = createdAtEpochSecond;
        }

        public UUID getOwnerId() {
            return ownerId;
        }

        public String getOwnerName() {
            return ownerName;
        }

        public UUID getInviterId() {
            return inviterId;
        }

        public String getInviterName() {
            return inviterName;
        }

        public UUID getTargetId() {
            return targetId;
        }

        public String getTargetName() {
            return targetName;
        }

        public long getCreatedAtEpochSecond() {
            return createdAtEpochSecond;
        }

        public long getExpiresAtEpochSecond() {
            return createdAtEpochSecond + INVITE_TTL_SECONDS;
        }

        public long getRemainingSeconds(long nowEpochSecond) {
            return Math.max(0L, getExpiresAtEpochSecond() - nowEpochSecond);
        }

        private boolean isExpired(long nowEpochSecond) {
            return getExpiresAtEpochSecond() <= nowEpochSecond;
        }
    }
}
