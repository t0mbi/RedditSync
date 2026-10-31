package app.revanced.extension.redditsync;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Helpers for the Reddit Sync "load more comments" patch. Reddit blocks /api/morechildren, so each child id is
 * fetched from /comments/{post}/_/{child}.json instead; one request returns one child, so the remaining ids are
 * chained into follow-up requests.
 */
@SuppressWarnings("unused")
public final class SyncFixes {
    /** Most children fetched per tap on "load more", to stay inside Reddit's rate limit. */
    private static final int MAX_CHILDREN = 20;

    private static final class State {
        String link;
        String sort;
        Object errorListener;
        String rest;
    }

    private static final Map<Object, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    /** The first id of a comma separated child list. */
    public static String first(String children) {
        int comma = children.indexOf(',');
        return comma < 0 ? children : children.substring(0, comma);
    }

    /** Remembers what a follow-up request needs; [children] is the full comma separated list. */
    public static void register(Object request, String link, String sort, Object errorListener, String children) {
        State state = new State();
        state.link = link;
        state.sort = sort;
        state.errorListener = errorListener;

        int comma = children.indexOf(',');
        if (comma < 0) {
            state.rest = "";
        } else {
            String[] ids = children.substring(comma + 1).split(",");
            if (ids.length > MAX_CHILDREN - 1) {
                ids = Arrays.copyOfRange(ids, 0, MAX_CHILDREN - 1);
            }
            state.rest = String.join(",", ids);
        }
        STATES.put(request, state);
    }

    public static String rest(Object request) {
        State state = STATES.get(request);
        return state == null ? "" : state.rest;
    }

    public static String link(Object request) {
        State state = STATES.get(request);
        return state == null ? null : state.link;
    }

    public static String sort(Object request) {
        State state = STATES.get(request);
        return state == null ? null : state.sort;
    }

    public static Object errorListener(Object request) {
        State state = STATES.get(request);
        return state == null ? null : state.errorListener;
    }
}
