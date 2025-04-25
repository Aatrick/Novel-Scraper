package aatricks.novelscraper;

public class LibraryItem {
    private String title;
    private String url;
    private long timestamp;
    private boolean isSelected;
    private String type;
    private int progress; // Store reading progress as percentage (0-100)
    private boolean isCurrentlyReading; // Flag to mark the currently open chapter

    public LibraryItem(String title, String url, long timestamp, String type) {
        this.title = title;
        this.url = url;
        this.timestamp = timestamp;
        this.type = type;
        this.isSelected = false;
        this.progress = 0;
        this.isCurrentlyReading = false;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        // Ensure progress is between 0-100
        this.progress = Math.max(0, Math.min(100, progress));
    }

    public boolean isCurrentlyReading() {
        return isCurrentlyReading;
    }

    public void setCurrentlyReading(boolean currentlyReading) {
        this.isCurrentlyReading = currentlyReading;
    }
}
