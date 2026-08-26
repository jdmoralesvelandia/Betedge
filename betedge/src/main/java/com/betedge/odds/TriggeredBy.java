package com.betedge.odds;

public enum TriggeredBy {
    SCHEDULED,
    MANUAL,
    /** A HotMatchRefreshService trigger - kept distinct from SCHEDULED so its own monthly budget can be counted separately. */
    HOT_REFRESH
}
