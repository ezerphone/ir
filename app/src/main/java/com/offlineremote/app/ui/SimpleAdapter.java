package com.offlineremote.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.offlineremote.app.R;

import java.util.ArrayList;
import java.util.List;

/** White list rows (optional icon tile, title, subtitle, chevron) with a click action. */
public class SimpleAdapter extends RecyclerView.Adapter<SimpleAdapter.VH> {

    public static final class Row {
        public final String title;
        public final String subtitle;
        public final Runnable action;
        public int icon;          // optional drawable for the leading tile, 0 = none
        public boolean chevron = true;
        public Row(String title, String subtitle, Runnable action) {
            this.title = title; this.subtitle = subtitle; this.action = action;
        }
        public Row icon(int res) { this.icon = res; return this; }
    }

    private final List<Row> rows = new ArrayList<>();

    public void submit(List<Row> newRows) { rows.clear(); rows.addAll(newRows); notifyDataSetChanged(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_simple, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Row r = rows.get(pos);
        h.title.setText(r.title);
        boolean hasSub = r.subtitle != null && !r.subtitle.isEmpty();
        h.subtitle.setVisibility(hasSub ? View.VISIBLE : View.GONE);
        if (hasSub) h.subtitle.setText(r.subtitle);
        h.tile.setVisibility(r.icon != 0 ? View.VISIBLE : View.GONE);
        if (r.icon != 0) h.tileIcon.setImageResource(r.icon);
        h.chevron.setVisibility(r.chevron ? View.VISIBLE : View.GONE);
        h.itemView.setOnClickListener(v -> { if (r.action != null) r.action.run(); });
    }

    @Override public int getItemCount() { return rows.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, subtitle, chevron;
        final View tile;
        final ImageView tileIcon;
        VH(View v) {
            super(v);
            title = v.findViewById(R.id.title);
            subtitle = v.findViewById(R.id.subtitle);
            chevron = v.findViewById(R.id.chevron);
            tile = v.findViewById(R.id.tile);
            tileIcon = v.findViewById(R.id.tileIcon);
        }
    }
}
