package aatricks.novelscraper;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LibraryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final List<Object> items = new ArrayList<>();
    private final Map<String, List<LibraryItem>> groupedItems = new HashMap<>();
    private boolean selectionMode = false;

    private final OnItemClickListener listener;
    private final OnItemLongClickListener longClickListener;

    public interface OnItemClickListener {
        void onItemClick(LibraryItem item);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(LibraryItem item);
    }

    public LibraryAdapter(OnItemClickListener listener, OnItemLongClickListener longClickListener) {
        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    public void setData(List<LibraryItem> libraryItems) {
        // Group items by title
        groupedItems.clear();
        for (LibraryItem item : libraryItems) {
            String title = item.getTitle();
            if (title == null || title.isEmpty()) {
                title = "Unknown";
            }

            if (!groupedItems.containsKey(title)) {
                groupedItems.put(title, new ArrayList<>());
            }
            groupedItems.get(title).add(item);
        }

        // Create new display list with headers and items
        items.clear();
        for (Map.Entry<String, List<LibraryItem>> entry : groupedItems.entrySet()) {
            items.add(entry.getKey()); // Add title as header
            items.addAll(entry.getValue()); // Add all items under that title
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_library, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder headerHolder = (HeaderViewHolder) holder;
            String title = (String) items.get(position);
            headerHolder.headerTitle.setText(title);
        } else if (holder instanceof ItemViewHolder) {
            ItemViewHolder itemHolder = (ItemViewHolder) holder;
            LibraryItem item = (LibraryItem) items.get(position);
            String displayTitle = item.getUrl();
            // Extract host name or file name for display
            if (displayTitle.contains("/")) {
                displayTitle = displayTitle.substring(displayTitle.lastIndexOf('/') + 1);
            }

            itemHolder.itemTitle.setText(displayTitle);

            if (selectionMode) {
                itemHolder.itemView.setBackgroundResource(
                        item.isSelected() ? R.color.selectedItem : android.R.color.transparent);
            } else {
                itemHolder.itemView.setBackgroundResource(android.R.color.transparent);
            }

            itemHolder.itemView.setOnClickListener(v -> {
                if (selectionMode) {
                    item.setSelected(!item.isSelected());
                    notifyItemChanged(holder.getAdapterPosition());
                } else {
                    listener.onItemClick(item);
                }
            });

            itemHolder.itemView.setOnLongClickListener(v -> {
                longClickListener.onItemLongClick(item);
                return true;
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_HEADER : TYPE_ITEM;
    }

    public void setSelectionMode(boolean selectionMode) {
        this.selectionMode = selectionMode;
        notifyDataSetChanged();
    }

    public List<LibraryItem> getSelectedItems() {
        List<LibraryItem> selectedItems = new ArrayList<>();
        for (Object obj : items) {
            if (obj instanceof LibraryItem) {
                LibraryItem item = (LibraryItem) obj;
                if (item.isSelected()) {
                    selectedItems.add(item);
                }
            }
        }
        return selectedItems;
    }

    public void clearSelections() {
        for (Object obj : items) {
            if (obj instanceof LibraryItem) {
                ((LibraryItem) obj).setSelected(false);
            }
        }
        notifyDataSetChanged();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView headerTitle;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            headerTitle = itemView.findViewById(R.id.headerTitle);
        }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView itemTitle;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            itemTitle = itemView.findViewById(R.id.itemTitle);
        }
    }
}
