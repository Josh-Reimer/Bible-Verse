package com.verse.of.the.day;

import static androidx.core.content.ContextCompat.startActivity;

import android.content.Intent;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;

public class Bookmark_recyclerview_adapter extends
        RecyclerView.Adapter<Bookmark_recyclerview_adapter.ViewHolder>{

    static ArrayList<Bookmark_recyclerview_model> data = new ArrayList<>();

    private final RedLetter redLetter = new RedLetter();

    int selectedPosition = RecyclerView.NO_POSITION;

    void setSelectedPosition(int position){
        if (position == selectedPosition) return;
        int old = selectedPosition;
        selectedPosition = position;
        if (old != RecyclerView.NO_POSITION) notifyItemChanged(old);
        if (position != RecyclerView.NO_POSITION) notifyItemChanged(position);
    }

    public void clearSelection(){
        setSelectedPosition(RecyclerView.NO_POSITION);
    }

    @NonNull
    @Override
    public Bookmark_recyclerview_adapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.bookmark_recyclerview_item,parent,false));
    }

    @Override
    public void onBindViewHolder(@NonNull Bookmark_recyclerview_adapter.ViewHolder holder, int position) {
        Spanned spanned = redLetter.getSpanned(holder.verse.getContext(), data.get(position).ref);
        holder.verse.setText(spanned != null ? spanned : data.get(position).scripture_text);
        holder.book.setText(data.get(position).book);
        // tint the long-pressed card so it is clear which bookmark the fabs act on
        if (position == selectedPosition) {
            holder.card.setCardBackgroundColor(
                    MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorSurfaceVariant));
        } else {
            holder.card.setCardBackgroundColor(holder.defaultCardColor);
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public onBookmarkLongClickListener bookmarkLongClickListener;

    public interface onBookmarkLongClickListener{
        void onBookmarkLongClicked(int position, String str);
    }

    public class ViewHolder extends RecyclerView.ViewHolder{
        TextView verse;
        TextView book;
        MaterialCardView card;
        android.content.res.ColorStateList defaultCardColor;
        public ViewHolder(@NonNull View itemView){
            super(itemView);
            verse = itemView.findViewById(R.id.text_view_priority);
            book = itemView.findViewById(R.id.bookview);
            card = (MaterialCardView) itemView;
            defaultCardColor = card.getCardBackgroundColor();

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
            itemView.setOnLongClickListener(new View.OnLongClickListener(){
                @Override
                public boolean onLongClick(View v){
                    int position = getAdapterPosition();
                    if (position == RecyclerView.NO_POSITION) return true;
                    setSelectedPosition(position);
                    bookmarkLongClickListener.onBookmarkLongClicked(position,data.get(position).ref);
                    return true;
                }
            });
    }
    }
    public Bookmark_recyclerview_adapter(ArrayList<Bookmark_recyclerview_model> data, onBookmarkLongClickListener bookmarkLongClickListener) {
        this.data = data;
        this.bookmarkLongClickListener = bookmarkLongClickListener;
    }

}