package com.voidexiled.magichygarden.features.farming.events;

import com.voidexiled.magichygarden.features.farming.state.MutationEventType;

import javax.annotation.Nullable;
import java.time.Instant;

public record MghgGlobalFarmEventState(
        MutationEventType eventType,
        @Nullable String eventId,
        @Nullable String weatherId,
        @Nullable Instant startedAt,
        @Nullable Instant endsAt
) {

    public boolean isActive(@Nullable Instant now) {
        if (now == null || endsAt == null) return false;
        return now.isBefore(endsAt);
    }

    public static MghgGlobalFarmEventState none() {
        return new MghgGlobalFarmEventState(MutationEventType.WEATHER, null, null, null, null);
    }
}
