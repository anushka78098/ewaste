package com.ewaste.server.service;

import com.ewaste.model.EWasteItem;
import com.ewaste.model.ItemStatus;

/** Small shared definitions. */
public final class Rules {
    private Rules() {}

    /** The facility has physically received the item (so it counts towards weight and rewards). */
    public static boolean received(EWasteItem i) {
        return i.status != ItemStatus.REJECTED && i.status != ItemStatus.SUBMITTED && i.status != ItemStatus.PICKUP_SCHEDULED;
    }
}
