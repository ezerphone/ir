package com.offlineremote.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.offlineremote.app.R;
import com.offlineremote.app.model.SavedRemote;

import java.util.ArrayList;
import java.util.List;

public class RemoteListAdapter extends RecyclerView.Adapter<RemoteListAdapter.VH> {

    public interface OnClick { void onClick(SavedRemote r); }

    private final List<SavedRemote> items = new ArrayList<>();
    private final OnClick onClick;
    private final OnClick onLong;

    public RemoteListAdapter(OnClick onClick, OnClick onLong) {
        this.onClick = onClick;
        this.onLong = onLong;
    }

    public void submit(List<SavedRemote> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_remote, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        SavedRemote r = items.get(pos);
        h.name.setText(r.name == null ? "" : r.name);
        h.sub.setText(subtitle(r));
        h.icon.setText(iconFor(r.category));
        h.itemView.setOnClickListener(v -> onClick.onClick(r));
        h.itemView.setOnLongClickListener(v -> { onLong.onClick(r); return true; });
    }

    private static String subtitle(SavedRemote r) {
        switch (r.source == null ? "" : r.source) {
            case "smartir": return "SmartIR";
            case "lib":     return "ספרייה";
            case "learned": return "למידה";
            case "local":   return "ZaZa";
            default:        return "";
        }
    }

    private static String iconFor(String cat) {
        if (cat == null) return "📡";
        switch (cat) {
            case "ac":          return "❄️";
            case "tv":          return "📺";
            case "led":         return "💡";
            case "speaker":     return "🔊";
            case "soundbar":    return "🔊";
            case "receiver":    return "🎚";
            case "projector":   return "📽";
            case "fan":         return "🌀";
            case "cable":       return "📡";
            case "streaming":   return "▶";
            case "bluray":      return "💿";
            case "dvd":         return "📀";
            case "cd":          return "💿";
            case "heater":      return "🔥";
            case "airpurifier": return "🌬";
            case "humidifier":  return "💧";
            case "vacuum":      return "🤖";
            case "camera":      return "📷";
            case "car":         return "🚗";
            case "fireplace":   return "🔥";
            case "vcr":         return "📼";
            case "monitor":     return "🖥";
            case "console":     return "🎮";
            case "stb":         return "📦";
            default:            return "📡";
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView icon, name, sub;
        VH(View v) {
            super(v);
            icon = v.findViewById(R.id.icon);
            name = v.findViewById(R.id.name);
            sub = v.findViewById(R.id.sub);
        }
    }
}
