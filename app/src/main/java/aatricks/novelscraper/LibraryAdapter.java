package aatricks.novelscraper;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LibraryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final List<Object> items = new ArrayList<>();
    private final Map<String, List<LibraryItem>> groupedItems = new HashMap<>();
    private boolean selectionMode = false;

    private final OnItemClickListener listener;
    private final OnItemLongClickListener longClickListener;
    private final OnSelectionChangeListener selectionChangeListener;

    public interface OnItemClickListener {
        void onItemClick(LibraryItem item);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(LibraryItem item);
    }

    public interface OnSelectionChangeListener {
        void onSelectionChanged(int selectedCount);
    }

    public LibraryAdapter(OnItemClickListener listener, OnItemLongClickListener longClickListener, OnSelectionChangeListener selectionChangeListener) {
        this.listener = listener;
        this.longClickListener = longClickListener;
        this.selectionChangeListener = selectionChangeListener;
    }

    public void setData(List<LibraryItem> libraryItems) {
        // Group items by title
        groupedItems.clear();
        for (LibraryItem item : libraryItems) {
            String title = extractGroupTitle(item);

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

    /**
     * Extracts a grouping title from the URL's path
     * Intelligently handles different URL formats:
     * - For URLs like https://website.com/book/novel-name/chapter-X, it will extract "novel-name"
     * - For URLs like https://website.com/novel-name/chapter-X, it will extract "novel-name"
     * - For file URLs, it will use the filename or "Unknown"
     */
    private String extractGroupTitle(LibraryItem item) {
        String url = item.getUrl();
        if (url == null || url.isEmpty()) {
            return "Unknown";
        }

        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                URI uri = new URI(url);
                String path = uri.getPath();

                if (path != null && !path.isEmpty()) {
                    // Common patterns for novel URLs
                    Pattern bookPattern = Pattern.compile("/book/([^/]+)/?");
                    Pattern novelPattern = Pattern.compile("/novel/([^/]+)/?");
                    Pattern genericPattern = Pattern.compile("/([^/]+)/(?:chapter|c)-\\d+");

                    // First try to match /book/novel-name pattern
                    Matcher bookMatcher = bookPattern.matcher(path);
                    if (bookMatcher.find()) {
                        return formatTitle(bookMatcher.group(1));
                    }

                    // Then try to match /novel/novel-name pattern
                    Matcher novelMatcher = novelPattern.matcher(path);
                    if (novelMatcher.find()) {
                        return formatTitle(novelMatcher.group(1));
                    }

                    // Try generic pattern where novel name is before chapter
                    Matcher genericMatcher = genericPattern.matcher(path);
                    if (genericMatcher.find()) {
                        return formatTitle(genericMatcher.group(1));
                    }

                    // Fallback: look for the second path segment (index 1) if it exists
                    String[] segments = path.split("/");
                    if (segments.length > 1 && !segments[1].isEmpty()) {
                        return formatTitle(segments[1]);
                    }
                }

                // Fallback to host if no second path segment
                String host = uri.getHost();
                return host != null ? host : "Unknown";
            } else {
                // For file URIs or non-URL strings, extract filename
                String filename = url;
                if (filename.contains("/")) {
                    filename = filename.substring(filename.lastIndexOf('/') + 1);
                }
                if (filename.contains(".")) {
                    filename = filename.substring(0, filename.lastIndexOf('.'));
                }
                return filename.isEmpty() ? "Unknown" : filename;
            }
        } catch (URISyntaxException e) {
            // If URL parsing fails, use the custom title or a fallback
            return item.getTitle() != null && !item.getTitle().isEmpty() ?
                   item.getTitle() : "Unsorted";
        }
    }

    /**
     * Formats a title extracted from URL path:
     * - Replaces hyphens and underscores with spaces
     * - Capitalizes first letter of each word
     */
    private String formatTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.isEmpty()) {
            return "Unknown";
        }

        // Replace hyphens and underscores with spaces
        String spacedTitle = rawTitle.replace('-', ' ').replace('_', ' ');

        // Capitalize first letter of each word
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : spacedTitle.toCharArray()) {
            if (Character.isSpaceChar(c)) {
                capitalizeNext = true;
                result.append(c);
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString();
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

            // Display the last part of the URL (chapter identifier or filename)
            String displayTitle = extractDisplayTitle(item.getUrl());
            itemHolder.itemTitle.setText(displayTitle);

            // Show progress
            itemHolder.itemProgress.setProgress(item.getProgress());
            itemHolder.progressText.setText(item.getProgress() + "%");

            // Show current chapter indicator
            itemHolder.currentChapterIndicator.setVisibility(
                    item.isCurrentlyReading() ? View.VISIBLE : View.INVISIBLE);

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
                    notifySelectionChanged();
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

    /**
     * Extracts a display title from a URL
     * For web URLs, it tries to get the last meaningful path segment
     * For files, it gets the filename
     */
    private String extractDisplayTitle(String url) {
        if (url == null || url.isEmpty()) {
            return "Unknown";
        }

        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                URI uri = new URI(url);
                String path = uri.getPath();

                if (path != null && !path.isEmpty()) {
                    // Split path and take the last non-empty segment
                    String[] segments = path.split("/");
                    for (int i = segments.length - 1; i >= 0; i--) {
                        if (!segments[i].isEmpty()) {
                            return segments[i];
                        }
                    }
                }

                // Fallback to full url if no path segments found
                return url;
            } else {
                // For file URIs, extract filename
                if (url.contains("/")) {
                    return url.substring(url.lastIndexOf('/') + 1);
                }
                return url;
            }
        } catch (URISyntaxException e) {
            // If URL parsing fails, return the raw URL
            return url;
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
        boolean hadSelections = false;
        for (Object obj : items) {
            if (obj instanceof LibraryItem) {
                LibraryItem item = (LibraryItem) obj;
                if (item.isSelected()) {
                    hadSelections = true;
                    item.setSelected(false);
                }
            }
        }

        if (hadSelections) {
            notifyDataSetChanged();
            notifySelectionChanged();
        }
    }

    public void notifySelectionChanged() {
        int selectedCount = 0;
        for (Object obj : items) {
            if (obj instanceof LibraryItem && ((LibraryItem) obj).isSelected()) {
                selectedCount++;
            }
        }
        selectionChangeListener.onSelectionChanged(selectedCount);
    }

    /**
     * Updates the currently reading status across all items
     * @param currentUrl URL of the currently reading chapter
     */
    public void updateCurrentlyReading(String currentUrl) {
        boolean changed = false;

        // Reset all items first
        for (Object obj : items) {
            if (obj instanceof LibraryItem) {
                LibraryItem item = (LibraryItem) obj;
                if (item.isCurrentlyReading()) {
                    item.setCurrentlyReading(false);
                    changed = true;
                }
            }
        }

        // Set the current one
        if (currentUrl != null && !currentUrl.isEmpty()) {
            for (Object obj : items) {
                if (obj instanceof LibraryItem) {
                    LibraryItem item = (LibraryItem) obj;
                    if (item.getUrl().equals(currentUrl)) {
                        item.setCurrentlyReading(true);
                        changed = true;
                    }
                }
            }
        }

        if (changed) {
            notifyDataSetChanged();
        }
    }

    /**
     * Updates reading progress for a specific item
     * @param url URL of the item to update
     * @param progress Progress percentage (0-100)
     * @return true if an item was updated, false otherwise
     */
    public boolean updateReadingProgress(String url, int progress) {
        boolean updated = false;
        for (Object obj : items) {
            if (obj instanceof LibraryItem) {
                LibraryItem item = (LibraryItem) obj;
                if (item.getUrl().equals(url)) {
                    item.setProgress(progress);
                    updated = true;
                }
            }
        }

        if (updated) {
            notifyDataSetChanged();
        }
        return updated;
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
        ProgressBar itemProgress;
        TextView progressText;
        View currentChapterIndicator;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            itemTitle = itemView.findViewById(R.id.itemTitle);
            itemProgress = itemView.findViewById(R.id.itemProgress);
            progressText = itemView.findViewById(R.id.progressText);
            currentChapterIndicator = itemView.findViewById(R.id.currentChapterIndicator);
        }
    }
}
