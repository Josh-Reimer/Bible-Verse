package com.verse.of.the.day;

import android.graphics.ColorSpace;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class Bookmark_recyclerview_adapter extends
        RecyclerView.Adapter<Bookmark_recyclerview_adapter.ViewHolder>{

    ArrayList<Bookmark_recyclerview_model> data = new ArrayList<>();
    @NonNull
    @Override
    public Bookmark_recyclerview_adapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.bookmark_recyclerview_item,null));
    }

    @Override
    public void onBindViewHolder(@NonNull Bookmark_recyclerview_adapter.ViewHolder               holder, int position) {
        holder.verse.setText(data.get(position).bible_ref);

    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder{
        TextView verse;
        public ViewHolder(@NonNull View itemView){
            super(itemView);
            verse = itemView.findViewById(R.id.text_view_priority);

    }
    }
    public Bookmark_recyclerview_adapter(ArrayList<Bookmark_recyclerview_model> data) {
        this.data = data;
    }

}