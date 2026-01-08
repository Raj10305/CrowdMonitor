package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.models.HistoryItem;
import com.squareup.picasso.Picasso;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final List<HistoryItem> items;
    private final String BASE_URL = "http://192.168.1.6:5000";   // ⚠ match server

    public HistoryAdapter(List<HistoryItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.item_history,
                parent,
                false
        );
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        HistoryItem item = items.get(position);

        // Text fields
        h.countText.setText("Count: " + item.count);
        h.timeText.setText(item.timestamp);

        // Build image URL
        if (item.url != null && !item.url.isEmpty()) {
            String fullUrl = BASE_URL + item.url; // e.g: /static/uploads/xxx.jpg

            Picasso.get()
                    .load(fullUrl)
                    .into(h.thumb);

        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView thumb;
        TextView countText, timeText;

        ViewHolder(View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            countText = v.findViewById(R.id.countText);
            timeText = v.findViewById(R.id.timeText);
        }
    }
}
