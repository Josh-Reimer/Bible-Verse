package com.verse.of.the.day;

import static androidx.core.content.ContextCompat.startActivity;

import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class Bookmark_recyclerview_adapter extends
        RecyclerView.Adapter<Bookmark_recyclerview_adapter.ViewHolder>{

    static ArrayList<Bookmark_recyclerview_model> data = new ArrayList<>();

    @NonNull
    @Override
    public Bookmark_recyclerview_adapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.bookmark_recyclerview_item,parent,false));
    }

    @Override
    public void onBindViewHolder(@NonNull Bookmark_recyclerview_adapter.ViewHolder holder, int position) {
        holder.verse.setText(data.get(position).scripture_text);
        holder.book.setText(data.get(position).book);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder{
        TextView verse;
        TextView book;
        public ViewHolder(@NonNull View itemView){
            super(itemView);
            verse = itemView.findViewById(R.id.text_view_priority);
            book = itemView.findViewById(R.id.bookview);

            // Define click listener for the ViewHolder's View.
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Navigate to VerseLookup activity with verse details
                    Intent intent = new Intent(itemView.getContext(), VerseLookUpActivity.class);
                    intent.putExtra("verse_ref", data.get(getAdapterPosition()).ref);
                    startActivity(itemView.getContext(),intent,null);
                }
            });
    }
    }
    public Bookmark_recyclerview_adapter(ArrayList<Bookmark_recyclerview_model> data) {
        this.data = data;
    }

}