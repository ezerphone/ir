package com.offlineremote.app.model;

import java.util.List;

/**
 * A set of candidate remotes the user is trying out before saving. Each candidate is
 * built lazily (TV/LED load their key sets on demand). Swiping in the remote screen
 * moves between candidates of the same device; only "Save" commits one to the store.
 */
public final class PreviewSession {

    public interface Builder { SavedRemote build(); }

    public final List<Builder> builders;
    public int index;

    public PreviewSession(List<Builder> builders, int index) {
        this.builders = builders;
        this.index = Math.max(0, Math.min(index, builders.size() - 1));
    }

    public SavedRemote current() { return builders.get(index).build(); }
    public int size() { return builders.size(); }
    public boolean hasPrev() { return index > 0; }
    public boolean hasNext() { return index < builders.size() - 1; }
}
