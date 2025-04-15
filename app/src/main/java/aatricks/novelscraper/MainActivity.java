package aatricks.novelscraper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.text.LineBreaker;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.Html;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.squareup.picasso.Picasso;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

public class MainActivity extends AppCompatActivity implements LibraryAdapter.OnItemClickListener, LibraryAdapter.OnItemLongClickListener {
    private boolean hasData = false;
    private static final int PICK_HTML_FILE = 1;
    private LinearLayout parentLayout;
    private EditText urlInput;
    private boolean isLoading = false;
    private static int pageNumber = 0;

    private DrawerLayout drawerLayout;
    private LibraryAdapter libraryAdapter;
    private List<LibraryItem> libraryItems = new ArrayList<>();
    private boolean selectionMode = false;
    private Button deleteSelectedButton;
    private Button cancelSelectionButton;
    private GestureDetectorCompat gestureDetector;

    @NonNull
    private static StringBuilder getStringBuilder(List<String> paragraphs, int i) {
        if (paragraphs == null || i < 0 || i >= paragraphs.size() || paragraphs.get(i) == null) {
            return new StringBuilder();
        }

        String paragraph = paragraphs.get(i);
        paragraph = removePageWord(paragraph);
        StringBuilder newParagraph = new StringBuilder();
        Set<Integer> seenNumbers = new HashSet<>();
        for (int j = 0; j < paragraph.length(); j++) {
            char c = paragraph.charAt(j);
            newParagraph.append(c);
            if (j > 1) {
                char prevChar = paragraph.charAt(j - 1);
                char prevPrevChar = paragraph.charAt(j - 2);
                if (c==',' && prevChar=='.') {
                    newParagraph.deleteCharAt(newParagraph.length() - 1);
                }
                if (c==',' && prevChar=='"') {
                    newParagraph.deleteCharAt(newParagraph.length() - 1);
                }
                if (c == '\n' && prevChar == '.') {
                    newParagraph.append("\n\n\n");
                }
                if (c == '\n' && prevChar == '"') {
                    newParagraph.append("\n\n\n");
                }
                if (c == '\n' && prevPrevChar == '.') {
                    newParagraph.append("\n\n\n");
                }
                if (c == '\n' && prevPrevChar == '"') {
                    newParagraph.append("\n\n\n");
                }
                if (c == '\n' && prevChar == ' ') {
                    newParagraph.deleteCharAt(newParagraph.length() - 1);
                }
                if (c == '\n') {
                    newParagraph.deleteCharAt(newParagraph.length() - 1);
                    newParagraph.append(" ");
                }
                if (Character.isDigit(c)) {
                    int number = Character.getNumericValue(c);
                    if (number == pageNumber + 1 || number == pageNumber + 2 || number == pageNumber + 3 || number == pageNumber + 4 ) {
                        if (!seenNumbers.contains(number)) {
                            newParagraph.deleteCharAt(newParagraph.length() - 1);
                            pageNumber = number;
                            seenNumbers.add(number);
                        }
                    } else if (number == pageNumber - 1 || number == pageNumber - 2 || number == pageNumber - 3 || number == pageNumber - 4 ) {
                        if (!seenNumbers.contains(number)) {
                            newParagraph.deleteCharAt(newParagraph.length() - 1);
                            pageNumber = number;
                            seenNumbers.add(number);
                        }
                    }
                }
            }
        }
        return newParagraph;
    }

    private static String removePageWord(String paragraph) {
        if (paragraph == null) {
            return "";
        }
        String regex = "(Page \\||Page )";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(paragraph);
        return matcher.replaceAll("");
    }

    private String incrementChapterInUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        Pattern pattern = Pattern.compile("(\\d+)(?!.*\\d)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            int chapterNumber = Integer.parseInt(Objects.requireNonNull(matcher.group(1)));
            chapterNumber++;
            url = matcher.replaceFirst(String.valueOf(chapterNumber));
        }
        return url;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.BLACK));

        // Initialize views
        drawerLayout = findViewById(R.id.drawerLayout);
        RecyclerView libraryRecyclerView = findViewById(R.id.libraryRecyclerView);
        ScrollView scrollView = findViewById(R.id.scrollView);
        parentLayout = findViewById(R.id.parentLayout);
        urlInput = findViewById(R.id.urlInput);
        Button scrapButton = findViewById(R.id.scrapButton);
        Button downloadButton = findViewById(R.id.downloadButton);
        deleteSelectedButton = findViewById(R.id.deleteSelectedButton);
        cancelSelectionButton = findViewById(R.id.cancelSelectionButton);

        // Setup gesture detector for swipes
        gestureDetector = new GestureDetectorCompat(this, new SwipeGestureListener());

        // Configure drawer to work with ScrollView properly
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                // No need to disable ScrollView - let it function normally
            }
        });

        // Setup RecyclerView for library items
        libraryAdapter = new LibraryAdapter(this, this);
        libraryRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        libraryRecyclerView.setAdapter(libraryAdapter);
        libraryRecyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        // Load library data
        loadLibraryData();

        // Ensure the DrawerLayout is above the ScrollView in z-order
        drawerLayout.bringToFront();
        drawerLayout.requestLayout();

        // Setup touch listeners with improved handling
        scrollView.setOnTouchListener(new View.OnTouchListener() {
            private float startX = 0;
            private static final int EDGE_THRESHOLD = 50; // dp
            private final float density = getResources().getDisplayMetrics().density;
            private final int edgeThresholdPx = (int) (EDGE_THRESHOLD * density);

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Store initial touch position
                        startX = event.getX();
                        // Let ScrollView handle down event for scrolling
                        v.onTouchEvent(event);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        // Handle horizontal swipes near edge for drawer
                        if (startX < edgeThresholdPx && event.getX() > startX + edgeThresholdPx) {
                            // Right swipe from left edge - open drawer
                            drawerLayout.openDrawer(findViewById(R.id.navDrawer));
                            return true;
                        }
                        // Let ScrollView handle move event
                        v.onTouchEvent(event);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // Let ScrollView handle up/cancel event to stop scrolling
                        v.onTouchEvent(event);
                        v.performClick();
                        break;
                }
                // Return true to indicate we've handled the event
                return true;
            }
        });

        // Simplify gesture detection for mainContent
        findViewById(R.id.mainContent).setOnTouchListener((v, event) ->
            gestureDetector.onTouchEvent(event)
        );

        findViewById(R.id.navDrawer).setOnTouchListener((v, event) -> {
            boolean handled = gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return handled;
        });

        // Setup buttons for selection mode
        deleteSelectedButton.setOnClickListener(v -> {
            deleteSelectedItems();
            exitSelectionMode();
        });

        cancelSelectionButton.setOnClickListener(v -> exitSelectionMode());

        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        String url = sharedPreferences.getString("url", "");
        String data = sharedPreferences.getString("paragraphs", "");
        urlInput.setText(url);

        // Check if reader is empty and open drawer if necessary
        if (data.isEmpty()) {
            drawerLayout.openDrawer(findViewById(R.id.navDrawer));
        } else {
            loadExistingContent(data, url);
        }

        downloadButton.setOnClickListener(v -> {
            String url1 = urlInput.getText().toString();
            if (!url1.isEmpty()) {
                for (int i = 0; i < 100; i++) {
                    url1 = incrementChapterInUrl(url1);
                    new ScrapInAdvance().execute(url1);
                }
            }
        });

        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (!scrollView.canScrollVertically(1) && !isLoading && hasData) {
                isLoading = true;
                String url12 = urlInput.getText().toString();
                if (!url12.isEmpty()) {
                    url12 = incrementChapterInUrl(url12);
                    urlInput.setText(url12);
                    new WebScrapingTask().execute(url12);
                    scrollView.fullScroll(ScrollView.FOCUS_UP);
                } else {
                    isLoading = false;
                }
            }
        });

        scrapButton.setOnClickListener(v -> {
            String url3 = urlInput.getText().toString();
            if (url3.isEmpty()) {
                openFileSelector();
            } else {
                new WebScrapingTask().execute(url3);
                drawerLayout.closeDrawers();
            }
        });
    }

    private void loadExistingContent(String data, String url) {
        if (data != null && !data.isEmpty()) {
            String[] paragraphs = data.split(" ,");
            if (url != null && !url.isEmpty()) {
                for (String paragraph : paragraphs) {
                    if (paragraph != null && paragraph.contains("http")) {
                        StringBuilder newParagraph = new StringBuilder();
                        for (int j = 0; j < paragraph.length(); j++) {
                            if (paragraph.charAt(j) == 'h' && j + 3 < paragraph.length() &&
                                paragraph.charAt(j + 1) == 't' &&
                                paragraph.charAt(j + 2) == 't' &&
                                paragraph.charAt(j + 3) == 'p') {

                                for (int k = j; k < paragraph.length(); k++) {
                                    if (paragraph.charAt(k) == ' ') {
                                        break;
                                    }
                                    newParagraph.append(paragraph.charAt(k));
                                }
                                break;
                            }
                        }
                        ImageView imageView = new ImageView(MainActivity.this);
                        Picasso.get().load(String.valueOf(newParagraph)).into(imageView);
                        LinearLayout.LayoutParams LayoutParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        );
                        imageView.setLayoutParams(LayoutParams);
                        parentLayout.addView(imageView);
                    } else if (paragraph != null) {
                        TextView textView = getView(getStringBuilder(Collections.singletonList(paragraph), 0) + "\n");
                        parentLayout.addView(textView);
                    }
                }
            } else {
                TextView textView = getView(Arrays.toString(paragraphs));
                parentLayout.addView(textView);
            }
            hasData = true;
        }
    }

    private void loadLibraryData() {
        SharedPreferences sharedPreferences = getSharedPreferences("LibraryItems", MODE_PRIVATE);
        String jsonLibrary = sharedPreferences.getString("items", "");

        if (!jsonLibrary.isEmpty()) {
            Gson gson = new Gson();
            Type type = new TypeToken<List<LibraryItem>>() {}.getType();
            libraryItems = gson.fromJson(jsonLibrary, type);

            if (libraryItems == null) {
                libraryItems = new ArrayList<>();
            }

            libraryAdapter.setData(libraryItems);
        }
    }

    private void saveLibraryData() {
        SharedPreferences sharedPreferences = getSharedPreferences("LibraryItems", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Gson gson = new Gson();
        String jsonLibrary = gson.toJson(libraryItems);
        editor.putString("items", jsonLibrary);
        editor.apply();
    }

    private void addToLibrary(String url, String title, String type) {
        // Check if URL already exists in the library
        for (LibraryItem item : libraryItems) {
            if (item.getUrl().equals(url)) {
                return; // Already exists, don't add again
            }
        }

        // Extract title from URL if not provided
        if (title == null || title.isEmpty()) {
            if (url.contains("/")) {
                String[] parts = url.split("/");
                for (int i = parts.length - 1; i >= 0; i--) {
                    if (!parts[i].isEmpty()) {
                        title = parts[i];
                        break;
                    }
                }
            } else {
                title = url;
            }
        }

        LibraryItem newItem = new LibraryItem(title, url, System.currentTimeMillis(), type);
        libraryItems.add(newItem);
        libraryAdapter.setData(libraryItems);
        saveLibraryData();
    }

    private void deleteSelectedItems() {
        List<LibraryItem> selectedItems = libraryAdapter.getSelectedItems();
        if (selectedItems.isEmpty()) {
            return;
        }

        libraryItems.removeAll(selectedItems);
        libraryAdapter.setData(libraryItems);
        saveLibraryData();
    }

    private void exitSelectionMode() {
        selectionMode = false;
        libraryAdapter.setSelectionMode(false);
        libraryAdapter.clearSelections();
        deleteSelectedButton.setVisibility(Button.GONE);
        cancelSelectionButton.setVisibility(Button.GONE);
    }

    @Override
    public void onItemClick(LibraryItem item) {
        urlInput.setText(item.getUrl());
        loadItemContent(item);
        drawerLayout.closeDrawers();
    }

    @Override
    public void onItemLongClick(LibraryItem item) {
        if (!selectionMode) {
            selectionMode = true;
            libraryAdapter.setSelectionMode(true);
            item.setSelected(true);
            libraryAdapter.notifyDataSetChanged();
            deleteSelectedButton.setVisibility(Button.VISIBLE);
            cancelSelectionButton.setVisibility(Button.VISIBLE);
        }
    }

    private void loadItemContent(LibraryItem item) {
        String url = item.getUrl();
        if (url == null || url.isEmpty()) {
            return;
        }

        switch (item.getType()) {
            case "web":
                new WebScrapingTask().execute(url);
                break;
            case "pdf": {
                Uri uri = Uri.parse(url);
                new ParseFileTask().execute(uri);
                break;
            }
            case "html": {
                Uri uri = Uri.parse(url);
                new ParseHTMLFileTask().execute(uri);
                break;
            }
        }
    }

    private void openFileSelector() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimetypes = {"text/html", "application/xhtml+xml", "application/pdf", "application/epub"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
        startActivityForResult(intent, PICK_HTML_FILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == PICK_HTML_FILE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                Uri uri = resultData.getData();
                if (uri != null) {
                    String type = getContentResolver().getType(uri);
                    if (type != null) {
                        if (type.equals("application/pdf")) {
                            addToLibrary(uri.toString(), getFileName(uri), "pdf");
                            new ParseFileTask().execute(uri);
                        } else if (type.equals("text/html") || type.equals("application/xhtml+xml")) {
                            addToLibrary(uri.toString(), getFileName(uri), "html");
                            new ParseHTMLFileTask().execute(uri);
                        }
                    }
                    drawerLayout.closeDrawers();
                }
            }
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (Objects.equals(uri.getScheme(), "content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    @NonNull
    private TextView getView(String paragraph) {
        TextView textView = new TextView(MainActivity.this);
        if (paragraph != null) {
//            String bionicText = applyBionicReading(paragraph);
//            textView.setText(Html.fromHtml(bionicText, Html.FROM_HTML_MODE_LEGACY));
            textView.setText(Html.fromHtml(paragraph, Html.FROM_HTML_MODE_LEGACY));
        } else {
            textView.setText("");
        }
        textView.setTextSize(20);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        textView.setLayoutParams(layoutParams);
        textView.setLineSpacing(0, 1.5f); // Line spacing multiplier
        textView.setPadding(0, 20, 0, 20); // Add padding to create paragraph spacing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            textView.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD);
        }
        return textView;
    }

    @Override
    protected void onPause() {
        super.onPause();
        ScrollView scrollView = findViewById(R.id.scrollView);
        int scrollY = scrollView.getScrollY();
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("scrollY", scrollY);
        editor.putString("paragraphs", getParagraphsAsString());
        editor.apply();

        // Also save library data
        saveLibraryData();
    }

    private String getParagraphsAsString() {
        StringBuilder paragraphs = new StringBuilder();
        for (int i = 0; i < parentLayout.getChildCount(); i++) {
            if (parentLayout.getChildAt(i) instanceof TextView) {
                TextView textView = (TextView) parentLayout.getChildAt(i);
                if (textView.getText() != null) {
                    paragraphs.append(textView.getText().toString()).append(" ,");
                }
            }
        }
        return paragraphs.toString();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        int scrollY = sharedPreferences.getInt("scrollY", 0);
        String data = sharedPreferences.getString("paragraphs", "");
        parentLayout.removeAllViews();

        loadExistingContent(data, sharedPreferences.getString("url", ""));

        final ScrollView scrollView = findViewById(R.id.scrollView);
        scrollView.post(() -> scrollView.scrollTo(0, scrollY));

        // Also refresh library data
        loadLibraryData();
    }

    private class SwipeGestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;
        private static final int EDGE_SIZE = 50; // Edge size in pixels for drawer activation

        @Override
        public boolean onDown(@NonNull MotionEvent e) {
            // Only return true to indicate we're interested in subsequent events
            return false;
        }

        @Override
        public boolean onFling(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            boolean result = false;
            try {
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                float startX = e1.getX();
                boolean isNearLeftEdge = startX < EDGE_SIZE * getResources().getDisplayMetrics().density;

                // Only handle drawer opening from left edge
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0 && isNearLeftEdge) {
                            // Right swipe from left edge - open drawer
                            drawerLayout.openDrawer(findViewById(R.id.navDrawer));
                            result = true;
                        } else if (diffX < 0 && drawerLayout.isDrawerOpen(findViewById(R.id.navDrawer))) {
                            // Left swipe - close drawer if open
                            drawerLayout.closeDrawers();
                            result = true;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return result;
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class ParseFileTask extends AsyncTask<Uri, Void, List<String>> {
        private Uri fileUri;

        @Override
        protected List<String> doInBackground(Uri... uris) {
            fileUri = uris[0];
            List<String> paragraphs = new ArrayList<>();

            try {
                InputStream inputStream = getContentResolver().openInputStream(fileUri);
                if (inputStream == null) {
                    return paragraphs;
                }

                PdfReader reader = new PdfReader(inputStream);
                PdfDocument pdfDoc = new PdfDocument(reader);
                StringBuilder text = new StringBuilder();

                int numberOfPages = pdfDoc.getNumberOfPages();
                for (int i = 1; i <= numberOfPages; i++) {
                    text.append(PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i)));
                }

                pdfDoc.close();
                paragraphs.add(text.toString());
                for (int i = 0; i < paragraphs.size(); i++) {
                    StringBuilder newParagraph = getStringBuilder(paragraphs, i);
                    paragraphs.set(i, newParagraph.toString());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
            SharedPreferences.Editor myEdit = sharedPreferences.edit();
            myEdit.putString("url", fileUri.toString());
            myEdit.putString("paragraphs", String.join(" ,", paragraphs));
            myEdit.apply();

            return paragraphs;
        }

        @Override
        protected void onPostExecute(List<String> paragraphs) {
            parentLayout.removeAllViews();
            hasData = paragraphs != null && !paragraphs.isEmpty();
            if (paragraphs != null && !paragraphs.isEmpty()) {
                TextView textView = getView(paragraphs.get(0));
                parentLayout.addView(textView);
            }
            ScrollView scrollView = findViewById(R.id.scrollView);
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_UP));

            // Add to library if not already present
            addToLibrary(fileUri.toString(), getFileName(fileUri), "pdf");
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class ParseHTMLFileTask extends AsyncTask<Uri, Void, List<String>> {
        private Uri htmlUri;

        @Override
        protected List<String> doInBackground(Uri... uris) {
            htmlUri = uris[0];
            List<String> paragraphs = new ArrayList<>();
            try {
                InputStream inputStream = getContentResolver().openInputStream(htmlUri);
                if (inputStream == null) {
                    return paragraphs;
                }

                Document document = Jsoup.parse(inputStream, "UTF-8", "");
                Elements elements = document.select("p, img");
                for (Element element : elements) {
                    if (element.tagName().equals("p")) {
                        paragraphs.add(element.text() + "\n\n");
                    } else if (element.tagName().equals("img")) {
                        paragraphs.add(element.attr("src"));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
            SharedPreferences.Editor myEdit = sharedPreferences.edit();
            myEdit.putString("url", htmlUri.toString());
            myEdit.putString("paragraphs", String.join(" ,", paragraphs));
            myEdit.apply();

            return paragraphs;
        }

        @Override
        protected void onPostExecute(List<String> paragraphs) {
            parentLayout.removeAllViews();
            hasData = paragraphs != null && !paragraphs.isEmpty();
            if (paragraphs != null) {
                for (String paragraph : paragraphs) {
                    if (paragraph != null && paragraph.contains("http")) {
                        ImageView imageView = new ImageView(MainActivity.this);
                        Picasso.get().load(paragraph).into(imageView);
                        LinearLayout.LayoutParams LayoutParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        );
                        imageView.setLayoutParams(LayoutParams);
                        parentLayout.addView(imageView);
                    } else if (paragraph != null) {
                        TextView textView = getView(paragraph);
                        parentLayout.addView(textView);
                    }
                }
            }
            ScrollView scrollView = findViewById(R.id.scrollView);
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_UP));

            // Add to library if not already present
            addToLibrary(htmlUri.toString(), getFileName(htmlUri), "html");
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class WebScrapingTask extends AsyncTask<String, Void, List<String>> {
        private String webUrl;

        @Override
        protected List<String> doInBackground(String... strings) {
            webUrl = strings[0];
            List<String> paragraphs = new ArrayList<>();

            if (webUrl == null || webUrl.isEmpty()) {
                return paragraphs;
            }

            File downloadsDirectory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDirectory == null) {
                return paragraphs;
            }

            File htmlFile = new File(downloadsDirectory, webUrl.hashCode() + ".html");

            if (!htmlFile.exists()) {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(webUrl).build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful())
                        throw new IOException("Unexpected code " + response);

                    BufferedSink sink = Okio.buffer(Okio.sink(htmlFile));
                    if (response.body() != null) {
                        sink.writeAll(response.body().source());
                        sink.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return paragraphs;
                }
            }

            try {
                Document document = Jsoup.parse(htmlFile, "UTF-8");

                // Try to extract title
                String title = document.title();
                if (title.isEmpty()) {
                    Element titleElement = document.selectFirst("h1");
                    if (titleElement != null) {
                        title = titleElement.text();
                    }
                }

                if (!title.isEmpty()) {
                    // Add title as the first paragraph
                    paragraphs.add("# " + title);
                }

                Elements elements = document.select("p, img");
                for (Element element : elements) {
                    if (element.tagName().equals("p")) {
                        String paragraph = element.text();
                        if (!paragraph.isEmpty()) {
                            StringBuilder styledParagraph = getStringBuilder(Collections.singletonList(paragraph), 0);
                            paragraphs.add(styledParagraph.toString());
                            paragraphs.add("\n\n\n\n");
                        }
                    } else if (element.tagName().equals("img")) {
                        String imgSrc = element.attr("src");
                        if (!imgSrc.isEmpty()) {
                            paragraphs.add(imgSrc);
                        }
                    }
                }

                // Extract title for library
                if (!title.isEmpty()) {
                    // Store title for use in onPostExecute
                    document.setBaseUri(title);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
            SharedPreferences.Editor myEdit = sharedPreferences.edit();
            myEdit.putString("url", webUrl);
            myEdit.putString("paragraphs", String.join(" ,", paragraphs));
            myEdit.apply();

            return paragraphs;
        }

        @Override
        protected void onPostExecute(List<String> paragraphs) {
            parentLayout.removeAllViews();
            hasData = paragraphs != null && !paragraphs.isEmpty();

            String title = null;
            if (paragraphs != null && !paragraphs.isEmpty() && paragraphs.get(0).startsWith("# ")) {
                title = paragraphs.get(0).substring(2);
            }

            if (paragraphs != null) {
                for (String paragraph : paragraphs) {
                    if (paragraph != null && paragraph.contains("http")) {
                        ImageView imageView = new ImageView(MainActivity.this);
                        Picasso.get().load(paragraph).into(imageView);
                        LinearLayout.LayoutParams LayoutParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        );
                        imageView.setLayoutParams(LayoutParams);
                        parentLayout.addView(imageView);
                    } else if (paragraph != null) {
                        TextView textView = getView(paragraph);
                        parentLayout.addView(textView);
                    }
                }
            }

            // Add to library if not already present
            addToLibrary(webUrl, title, "web");

            new Handler().postDelayed(() -> isLoading = false, 2000); // 2000 milliseconds = 2 seconds
            ScrollView scrollView = findViewById(R.id.scrollView);
            scrollView.fullScroll(ScrollView.FOCUS_UP);
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class ScrapInAdvance extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... strings) {
            String url = strings[0];
            if (url == null || url.isEmpty()) {
                return null;
            }

            File downloadsDirectory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDirectory == null) {
                return null;
            }

            File htmlFile = new File(downloadsDirectory, url.hashCode() + ".html");

            if (!htmlFile.exists()) {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url).build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful())
                        throw new IOException("Unexpected code " + response);

                    if (response.body() != null) {
                        BufferedSink sink = Okio.buffer(Okio.sink(htmlFile));
                        sink.writeAll(response.body().source());
                        sink.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return null;
        }
    }

    private String applyBionicReading(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder bionicText = new StringBuilder();
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word != null && !word.isEmpty()) {
                int splitIndex = (int) Math.ceil(word.length() * 0.4); // Emphasize the first 40% of the word
                if (splitIndex > 0 && splitIndex < word.length()) {
                    bionicText.append("<b>").append(word.substring(0, splitIndex)).append("</b>")
                            .append(word.substring(splitIndex)).append(" ");
                } else {
                    bionicText.append(word).append(" ");
                }
            }
        }
        return bionicText.toString().trim();
    }
}
