package com.offlineremote.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.offlineremote.app.R;
import com.offlineremote.app.model.SavedRemote;

import java.util.ArrayList;
import java.util.List;

/** Home grid of device cards: line icon in a soft ring, name, brand. */
public class RemoteListAdapter extends RecyclerView.Adapter<RemoteListAdapter.VH> {

    public interface OnClick { void onClick(SavedRemote r); }

    private final List<SavedRemote> items = new ArrayList<>();
    private final OnClick onClick, onLong;

    public RemoteListAdapter(OnClick onClick, OnClick onLong) { this.onClick = onClick; this.onLong = onLong; }

    public void submit(List<SavedRemote> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_remote, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        SavedRemote r = items.get(pos);
        h.name.setText(r.name == null ? "" : r.name);
        h.sub.setText(sub(r));
        h.icon.setImageResource(Icons.forCategory("learned".equals(r.source) && !"led".equals(r.category) ? null : r.category));
        h.itemView.setOnClickListener(v -> onClick.onClick(r));
        h.itemView.setOnLongClickListener(v -> { onLong.onClick(r); return true; });
    }

    private static String sub(SavedRemote r) {
        if (r.brand != null && !r.brand.isEmpty()) return r.brand;
        if ("learned".equals(r.source)) return "נלמד משלט";
        return "";
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView icon; final TextView name, sub;
        VH(View v) { super(v); icon = v.findViewById(R.id.icon); name = v.findViewById(R.id.name); sub = v.findViewById(R.id.sub); }
    }
}
