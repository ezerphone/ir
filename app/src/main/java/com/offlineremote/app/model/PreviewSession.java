package com.offlineremote.app.model;

import java.util.List;

/**
 * Candidate code sets the user steps through with הקודם / הבא before tapping "עבד!".
 * Each candidate is built lazily (library remotes load their keys on demand).
 */
public final class PreviewSession {

    public interface Builder { SavedRemote build(); }

    public final List<Builder> builders;
    public int index;
    /** Brand as shown in the title, e.g. "AUX" or "אלקטרה". */
    public String brand = "";
    /** Category name, e.g. "מזגן". */
    public String categoryHe = "";
    /** Banner target, e.g. "המזגן". */
    public String target = "המכשיר";
    /** Category slug ("ac", "tv", "led", ...). */
    public String category = "";

    public PreviewSession(List<Builder> builders, int index) {
        this.builders = builders;
        this.index = Math.max(0, Math.min(index, builders.size() - 1));
    }

    public SavedRemote current() { return builders.get(index).build(); }
    public int size() { return builders.size(); }
    public boolean hasPrev() { return index > 0; }
    public boolean hasNext() { return index < builders.size() - 1; }
}
