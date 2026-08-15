package com.verse.of.the.day;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;

/**
 * The chapter number grid shown after a book is picked. Positions are chapters, so
 * position + 1 is the chapter number.
 */
public class BrowseChaptersAdapter extends RecyclerView.Adapter<BrowseChaptersAdapter.ChapterHolder> {

	public interface OnChapterClickListener {
		void onChapterClicked(int chapter);
	}

	private int chapterCount;
	/** The chapter this book was last opened at, or -1 — outlined so it is easy to find again. */
	private int currentChapter = -1;
	private final OnChapterClickListener listener;

	public BrowseChaptersAdapter(OnChapterClickListener listener) {
		this.listener = listener;
	}

	public void submit(int chapterCount, int currentChapter) {
		this.chapterCount = chapterCount;
		this.currentChapter = currentChapter;
		notifyDataSetChanged();
	}

	@NonNull
	@Override
	public ChapterHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.browse_chapter_item, parent, false);
		return new ChapterHolder(view);
	}

	@Override
	public void onBindViewHolder(@NonNull ChapterHolder holder, int position) {
		int chapter = position + 1;
		// Locale.ROOT is deliberate here for the same reason the search path takes it:
		// the digits are a reference, not prose, and must not pick up locale digits.
		holder.number.setText(String.format(Locale.ROOT, "%d", chapter));
		holder.number.setBackgroundResource(chapter == currentChapter
				? R.drawable.browse_chapter_tile_current
				: R.drawable.browse_chapter_tile);
		holder.number.setContentDescription(
				holder.itemView.getContext().getString(R.string.browse_chapter_content_description, chapter));
		holder.itemView.setOnClickListener(v -> listener.onChapterClicked(chapter));
	}

	@Override
	public int getItemCount() {
		return chapterCount;
	}

	static class ChapterHolder extends RecyclerView.ViewHolder {
		final TextView number;

		ChapterHolder(@NonNull View itemView) {
			super(itemView);
			number = itemView.findViewById(R.id.browse_chapter_number);
		}
	}
}
