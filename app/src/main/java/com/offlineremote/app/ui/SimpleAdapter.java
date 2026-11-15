package com.offlineremote.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.offlineremote.app.R;

import java.util.ArrayList;
import java.util.List;

/** Generic title/subtitle row list with a click action per row. */
public class SimpleAdapter extends RecyclerView.Adapter<SimpleAdapter.VH> {

    public static final class Row {
        public final String title;
        public final String subtitle;
        public final Runnable action;
        public int bg;   // optional fill color (0 = default); used for LED colour keys
        public Row(String title, String subtitle, Runnable action) {
            this.title = title; this.subtitle = subtitle; this.action = action;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final int layoutRes;

    public SimpleAdapter() { this(R.layout.item_simple); }
    public SimpleAdapter(int layoutRes) { this.layoutRes = layoutRes; }

    public void submit(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Row r = rows.get(pos);
        h.title.setText(r.title);
        if (h.subtitle != null) {
            if (r.subtitle != null && !r.subtitle.isEmpty()) {
                h.subtitle.setVisibility(View.VISIBLE);
                h.subtitle.setText(r.subtitle);
            } else {
                h.subtitle.setVisibility(View.GONE);
            }
        }
        if (r.bg != 0) {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setCornerRadius(h.itemView.getResources().getDisplayMetrics().density * 14);
            g.setColor(r.bg);
            h.itemView.setBackground(g);
            double lum = (0.299 * ((r.bg >> 16) & 0xff) + 0.587 * ((r.bg >> 8) & 0xff) + 0.114 * (r.bg & 0xff));
            h.title.setTextColor(lum > 150 ? 0xFF101417 : 0xFFFFFFFF);
        }
        h.itemView.setOnClickListener(v -> { if (r.action != null) r.action.run(); });
    }

    @Override
    public int getItemCount() { return rows.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, subtitle;
        VH(View v) { super(v); title = v.findViewById(R.id.title); subtitle = v.findViewById(R.id.subtitle); }
    }
}
