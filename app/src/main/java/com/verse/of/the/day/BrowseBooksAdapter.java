package com.verse.of.the.day;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * The book list on the browse screen: the 66 books under an OLD/NEW TESTAMENT header
 * each. The activity builds the rows (it owns the filter), so this only binds them.
 */
public class BrowseBooksAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

	public interface OnBookClickListener {
		void onBookClicked(int bookIndex);
	}

	static final int TYPE_HEADER = 0;
	static final int TYPE_BOOK = 1;

	/** A section header (bookIndex -1) or one book. */
	public static class Row {
		final int type;
		final String title;
		final int bookIndex;

		Row(int type, String title, int bookIndex) {
			this.type = type;
			this.title = title;
			this.bookIndex = bookIndex;
		}

		static Row header(String title) {
			return new Row(TYPE_HEADER, title, -1);
		}

		static Row book(String title, int bookIndex) {
			return new Row(TYPE_BOOK, title, bookIndex);
		}
	}

	private final List<Row> rows = new ArrayList<>();
	private final OnBookClickListener listener;

	public BrowseBooksAdapter(OnBookClickListener listener) {
		this.listener = listener;
	}

	public void submit(List<Row> newRows) {
		rows.clear();
		rows.addAll(newRows);
		notifyDataSetChanged();
	}

	@Override
	public int getItemViewType(int position) {
		return rows.get(position).type;
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		if (viewType == TYPE_HEADER) {
			return new HeaderHolder(inflater.inflate(R.layout.browse_section_header, parent, false));
		}
		return new BookHolder(inflater.inflate(R.layout.browse_book_item, parent, false));
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		Row row = rows.get(position);
		if (holder instanceof HeaderHolder) {
			((HeaderHolder) holder).title.setText(row.title);
			return;
		}
		BookHolder bookHolder = (BookHolder) holder;
		bookHolder.name.setText(row.title);
		bookHolder.itemView.setOnClickListener(v -> listener.onBookClicked(row.bookIndex));
	}

	@Override
	public int getItemCount() {
		return rows.size();
	}

	static class HeaderHolder extends RecyclerView.ViewHolder {
		final TextView title;

		HeaderHolder(@NonNull View itemView) {
			super(itemView);
			title = itemView.findViewById(R.id.browse_section_title);
		}
	}

	static class BookHolder extends RecyclerView.ViewHolder {
		final TextView name;

		BookHolder(@NonNull View itemView) {
			super(itemView);
			name = itemView.findViewById(R.id.browse_book_name);
		}
	}
}
