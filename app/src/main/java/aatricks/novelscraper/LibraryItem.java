package aatricks.novelscraper;

public class LibraryItem {
    private String title;
    private String url;
    private long timestamp;
    private boolean isSelected;
    private String type;

    public LibraryItem(String title, String url, long timestamp, String type) {
        this.title = title;
        this.url = url;
        this.timestamp = timestamp;
        this.type = type;
        this.isSelected = false;
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
}
