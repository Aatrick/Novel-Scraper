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
import android.util.Log;
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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class MainActivity
    extends AppCompatActivity
    implements
        LibraryAdapter.OnItemClickListener,
        LibraryAdapter.OnItemLongClickListener,
        LibraryAdapter.OnSelectionChangeListener {

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
    private GestureDetectorCompat gestureDetector;

    // Variables for half-screen scroll security
    private boolean isBottomReached = false;
    private float QuarterScreenHeight;
    private float initialTouchY;
    private float totalScrollDistance = 0;
    private boolean securityScrollEnabled = false;

    private String currentUrl = "";
    private boolean isAtTop = false;
    private String previousChapterUrl = "";

    @NonNull
    private static StringBuilder getStringBuilder(
        List<String> paragraphs,
        int i,
        boolean isPdfContent
    ) {
        if (
            paragraphs == null ||
            i < 0 ||
            i >= paragraphs.size() ||
            paragraphs.get(i) == null
        ) {
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

                // Handle punctuation combinations
                if (c == ',' && (prevChar == '.' || prevChar == '"')) {
                    newParagraph.deleteCharAt(newParagraph.length() - 1);
                }

                // Handle newlines
                if (c == '\n') {
                    // Add paragraph breaks after sentences
                    if (
                        prevChar == '.' ||
                        prevChar == '"' ||
                        prevPrevChar == '.' ||
                        prevPrevChar == '"'
                    ) {
                        newParagraph.append("\n\n\n");
                    }

                    // Remove space before newline
                    if (prevChar == ' ') {
                        newParagraph.deleteCharAt(newParagraph.length() - 2);
                    }

                    // Convert single newlines to spaces for better flow
                    newParagraph.deleteCharAt(newParagraph.length() - 1);
                    newParagraph.append(" ");
                }

                // Handle potential page numbers (stricter check) - only for PDF content
                if (Character.isDigit(c) && isPdfContent) {
                    int numStartPos = j;
                    int fullNumber = Character.getNumericValue(c);
                    int digitCount = 1;
                    boolean hasComma = false;
                    boolean hasPeriod = false;

                    // Check if number is in brackets/parentheses
                    boolean isInBrackets = false;
                    if (numStartPos > 0) {
                        prevChar = paragraph.charAt(numStartPos - 1);
                        isInBrackets =
                            prevChar == '(' ||
                            prevChar == '[' ||
                            prevChar == '{' ||
                            prevChar == '<' ||
                            prevChar == '"' ||
                            prevChar == '\'';
                    }

                    // Collect the complete number with potential commas
                    StringBuilder numberStr = new StringBuilder();
                    numberStr.append(c);

                    // Store the start position to check if we need to preserve the entire number
                    int originalNumStartPos = j;

                    while (
                        j + 1 < paragraph.length() &&
                        (Character.isDigit(paragraph.charAt(j + 1)) ||
                            paragraph.charAt(j + 1) == ',' ||
                            paragraph.charAt(j + 1) == '.')
                    ) {
                        j++;
                        char nextChar = paragraph.charAt(j);
                        numberStr.append(nextChar);

                        if (nextChar == ',') {
                            hasComma = true;
                        } else if (nextChar == '.') {
                            hasPeriod = true;
                        } else {
                            fullNumber =
                                fullNumber * 10 +
                                Character.getNumericValue(nextChar);
                            digitCount++;
                        }
                    }

                    // Check for suffixes like "th", "rd", "nd", etc.
                    boolean hasOrdinalSuffix = false;
                    if (j + 2 < paragraph.length()) {
                        String possibleSuffix = paragraph.substring(
                            j + 1,
                            Math.min(j + 3, paragraph.length())
                        );
                        hasOrdinalSuffix =
                            possibleSuffix.startsWith("th") ||
                            possibleSuffix.startsWith("st") ||
                            possibleSuffix.startsWith("nd") ||
                            possibleSuffix.startsWith("rd");
                    }

                    // Check for text directly before or after the number
                    boolean inTextualContext = false;
                    if (numStartPos > 1) {
                        String preceding = paragraph
                            .substring(
                                Math.max(0, numStartPos - 10),
                                numStartPos
                            )
                            .toLowerCase()
                            .trim();
                        inTextualContext =
                            preceding.endsWith("the ") ||
                            preceding.endsWith("at ") ||
                            preceding.endsWith("to ") ||
                            preceding.endsWith("level ") ||
                            preceding.endsWith("floor ");
                    }

                    // Check if number is followed by a closing bracket/parenthesis
                    boolean hasClosingBracket = false;
                    if (j + 1 < paragraph.length()) {
                        char nextChar = paragraph.charAt(j + 1);
                        hasClosingBracket =
                            nextChar == ')' ||
                            nextChar == ']' ||
                            nextChar == '}' ||
                            nextChar == '>' ||
                            nextChar == '"' ||
                            nextChar == '\'';
                    }

                    // Check for mathematical operators before or after the number
                    boolean hasOperator = false;
                    if (numStartPos > 0) {
                        prevChar = paragraph.charAt(numStartPos - 1);
                        hasOperator =
                            prevChar == '+' ||
                            prevChar == '-' ||
                            prevChar == '×' ||
                            prevChar == 'x' ||
                            prevChar == '*' ||
                            prevChar == '/' ||
                            prevChar == '=' ||
                            prevChar == '÷';
                    }

                    if (j + 1 < paragraph.length()) {
                        char nextChar = paragraph.charAt(j + 1);
                        hasOperator =
                            hasOperator ||
                            nextChar == '+' ||
                            nextChar == '-' ||
                            nextChar == '×' ||
                            nextChar == 'x' ||
                            nextChar == '*' ||
                            nextChar == '/' ||
                            nextChar == '=' ||
                            nextChar == '÷';
                    }

                    // Also check for space + operator patterns
                    boolean hasSpacedOperator = false;
                    if (
                        numStartPos > 1 &&
                        paragraph.charAt(numStartPos - 1) == ' '
                    ) {
                        prevPrevChar = paragraph.charAt(numStartPos - 2);
                        hasSpacedOperator =
                            prevPrevChar == '+' ||
                            prevPrevChar == '-' ||
                            prevPrevChar == '×' ||
                            prevPrevChar == 'x' ||
                            prevPrevChar == '*' ||
                            prevPrevChar == '/' ||
                            prevPrevChar == '=' ||
                            prevPrevChar == '÷';
                    }

                    if (
                        j + 2 < paragraph.length() &&
                        paragraph.charAt(j + 1) == ' '
                    ) {
                        char nextNextChar = paragraph.charAt(j + 2);
                        hasSpacedOperator =
                            hasSpacedOperator ||
                            nextNextChar == '+' ||
                            nextNextChar == '-' ||
                            nextNextChar == '×' ||
                            nextNextChar == 'x' ||
                            nextNextChar == '*' ||
                            nextNextChar == '/' ||
                            nextNextChar == '=' ||
                            nextNextChar == '÷';
                    }

                    // Check if number is preceded or followed by quotes
                    if (numStartPos > 0) {
                        boolean isInQuotes = paragraph.charAt(numStartPos - 1) == '"' ||
                                paragraph.charAt(numStartPos - 1) == '\'' ||
                                (j + 1 < paragraph.length() &&
                                        (paragraph.charAt(j + 1) == '"' ||
                                                paragraph.charAt(j + 1) == '\''));
                    }

                    // Check for space + colon/semicolon pattern
                    boolean hasSpacedPunctuation = false;
                    if (j + 2 < paragraph.length()) {
                        hasSpacedPunctuation =
                            paragraph.charAt(j + 1) == ' ' &&
                            (paragraph.charAt(j + 2) == ':' ||
                                paragraph.charAt(j + 2) == ';');
                    }

                    // Only consider as page number if surrounded by whitespace or punctuation
                    boolean leftBoundary = !Character.isLetterOrDigit(
                        paragraph.charAt(numStartPos - 1)
                    );
                    boolean rightBoundary =
                        (j == paragraph.length() - 1) ||
                        (!Character.isLetterOrDigit(paragraph.charAt(j + 1)) &&
                            !hasOrdinalSuffix);

                    // Check for "page" keyword before the number
                    boolean hasPageKeyword = false;
                    if (numStartPos >= 5) {
                        String preceding = paragraph
                            .substring(numStartPos - 5, numStartPos)
                            .toLowerCase();
                        hasPageKeyword =
                            preceding.equals("page ") ||
                            preceding.endsWith("pg. ") ||
                            preceding.endsWith("pg ") ||
                            preceding.endsWith("p a g e ");
                    }

                    boolean isReasonablePageNumber =
                        fullNumber > 0 && fullNumber < 1000;
                    boolean isSequential =
                        Math.abs(fullNumber - pageNumber) <= 2;

                    // Don't remove the number if:
                    // 1. It has a comma (like 3,000)
                    // 2. It has a period followed by a digit (like 77.0)
                    // 3. It has an ordinal suffix (like 3rd, 77th)
                    // 4. It's in a textual context (like "the 77 floor")
                    // 5. It's followed by punctuation and text (like "at 77, the...")
                    // 6. It's in brackets/parentheses like (1), [1], {1}, etc.
                    // 7. It's preceded by "..." or followed by "..."
                    // 8. It has a colon or semicolon before or after: 79;, 79:, etc.
                    // 9. It's part of a mathematical expression: +2, 2+, 2 + 3, etc.
                    boolean hasEllipsisBefore =
                        numStartPos >= 3 &&
                        paragraph
                            .substring(numStartPos - 3, numStartPos)
                            .equals("...");
                    boolean hasEllipsisAfter =
                        j + 3 < paragraph.length() &&
                        paragraph.substring(j + 1, j + 4).equals("...");

                    // Check for colons/semicolons
                    boolean hasColon = false;
                    boolean hasSemicolon = false;
                    if (j + 1 < paragraph.length()) {
                        char nextChar = paragraph.charAt(j + 1);
                        hasColon = nextChar == ':';
                        hasSemicolon = nextChar == ';';
                    }

                    hasSpacedPunctuation = false;
                    if (
                        j + 2 < paragraph.length() &&
                        paragraph.charAt(j + 1) == ' '
                    ) {
                        char nextNextChar = paragraph.charAt(j + 2);
                        hasSpacedPunctuation =
                            nextNextChar == ':' || nextNextChar == ';';
                    }

                    // Check if the number is quoted
                    boolean isQuoted = false;
                    if (numStartPos > 0) {
                        isQuoted =
                            paragraph.charAt(numStartPos - 1) == '"' ||
                            paragraph.charAt(numStartPos - 1) == '\'' ||
                            (j + 1 < paragraph.length() &&
                                (paragraph.charAt(j + 1) == '"' ||
                                    paragraph.charAt(j + 1) == '\''));
                    }

                    boolean shouldPreserve =
                        hasComma ||
                        (hasPeriod &&
                            j + 1 < paragraph.length() &&
                            Character.isDigit(paragraph.charAt(j + 1))) ||
                        hasOrdinalSuffix ||
                        inTextualContext ||
                        (isInBrackets && hasClosingBracket) ||
                        hasEllipsisBefore ||
                        hasEllipsisAfter ||
                        hasOperator ||
                        hasSpacedOperator ||
                        hasColon ||
                        hasSemicolon ||
                        hasSpacedPunctuation ||
                        isQuoted ||
                        (j + 2 < paragraph.length() &&
                            (paragraph.charAt(j + 1) == '.' ||
                                paragraph.charAt(j + 1) == ',') &&
                            Character.isLetter(paragraph.charAt(j + 2)));

                    boolean isPossiblePageNumber =
                        isReasonablePageNumber &&
                        (hasPageKeyword ||
                            (leftBoundary && rightBoundary && isSequential)) &&
                        !shouldPreserve;

                    if (
                        isPossiblePageNumber &&
                        !seenNumbers.contains(fullNumber)
                    ) {
                        // Only delete if it's a clear page number (not part of a word)
                        for (int k = 0; k < numberStr.length(); k++) {
                            if (
                                Build.VERSION.SDK_INT >=
                                Build.VERSION_CODES.VANILLA_ICE_CREAM
                            ) {
                                if (!newParagraph.isEmpty()) {
                                    newParagraph.deleteCharAt(
                                        newParagraph.length() - 1
                                    );
                                }
                            }
                        }
                        pageNumber = fullNumber;
                        seenNumbers.add(fullNumber);
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
            int chapterNumber = Integer.parseInt(
                Objects.requireNonNull(matcher.group(1))
            );
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
        window.addFlags(
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        );
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.BLACK));

        // Initialize views
        drawerLayout = findViewById(R.id.drawerLayout);
        RecyclerView libraryRecyclerView = findViewById(
            R.id.libraryRecyclerView
        );
        ScrollView scrollView = findViewById(R.id.scrollView);
        parentLayout = findViewById(R.id.parentLayout);
        urlInput = findViewById(R.id.urlInput);
        Button scrapButton = findViewById(R.id.scrapButton);
        Button downloadButton = findViewById(R.id.downloadButton);
        deleteSelectedButton = findViewById(R.id.deleteSelectedButton);

        // Setup gesture detector for swipes
        gestureDetector = new GestureDetectorCompat(
            this,
            new SwipeGestureListener()
        );

        // Configure drawer to work with ScrollView properly
        drawerLayout.addDrawerListener(
            new DrawerLayout.SimpleDrawerListener() {
                @Override
                public void onDrawerSlide(View drawerView, float slideOffset) {
                    // No need to disable ScrollView - let it function normally
                }

                @Override
                public void onDrawerOpened(View drawerView) {
                    // Save reading progress when drawer is opened
                    updateReadingProgress(
                        findViewById(R.id.scrollView).getScrollY()
                    );
                }
            }
        );

        // Setup RecyclerView for library items
        libraryAdapter = new LibraryAdapter(this, this, this);
        libraryRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        libraryRecyclerView.setAdapter(libraryAdapter);
        libraryRecyclerView.addItemDecoration(
            new DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        );

        // Load library data
        loadLibraryData();

        // Ensure the DrawerLayout is above the ScrollView in z-order
        drawerLayout.bringToFront();
        drawerLayout.requestLayout();

        // Setup touch listeners with improved handling
        scrollView.setOnTouchListener(
            new View.OnTouchListener() {
                private float startX = 0;
                private static final int EDGE_THRESHOLD = 50; // dp
                private final float density = getResources()
                    .getDisplayMetrics()
                    .density;
                private final int edgeThresholdPx = (int) (EDGE_THRESHOLD *
                    density);

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
                            if (
                                startX < edgeThresholdPx &&
                                event.getX() > startX + edgeThresholdPx
                            ) {
                                // Right swipe from left edge - open drawer
                                drawerLayout.openDrawer(
                                    findViewById(R.id.navDrawer)
                                );
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
            }
        );

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

        // Setup delete button
        deleteSelectedButton.setOnClickListener(v -> {
            deleteSelectedItems();
            exitSelectionMode();
        });

        SharedPreferences sharedPreferences = getSharedPreferences(
            "MySharedPref",
            MODE_PRIVATE
        );
        String url = sharedPreferences.getString("url", "");
        String data = sharedPreferences.getString("paragraphs", "");
        urlInput.setText(url);

        // Set current URL
        if (!url.isEmpty()) {
            currentUrl = url;
            libraryAdapter.updateCurrentlyReading(url);
        }

        // Check if reader is empty and open drawer if necessary
        if (data.isEmpty()) {
            drawerLayout.openDrawer(findViewById(R.id.navDrawer));
        } else {
            loadExistingContent(data, url);
        }

        downloadButton.setOnClickListener(v -> {
            String url1 = urlInput.getText().toString();
            if (!url1.isEmpty()) {
                for (int i = 0; i < 10; i++) {
                    url1 = incrementChapterInUrl(url1);
                    new ScrapInAdvance().execute(url1);
                }
            }
        });

        // Calculate quarter screen height for security scroll feature
        QuarterScreenHeight =
            getResources().getDisplayMetrics().heightPixels / 4.0f;

        // Setup scroll security mechanism
        scrollView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialTouchY = event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float currentY = event.getY();
                    float deltaY = initialTouchY - currentY;

                    if (isBottomReached && securityScrollEnabled) {
                        // Track scroll distance when bottom security scroll is active
                        totalScrollDistance += Math.abs(deltaY);
                        initialTouchY = currentY;

                        // Check if user has scrolled quarter screen height
                        if (totalScrollDistance >= QuarterScreenHeight) {
                            // Trigger loading next chapter
                            loadNextChapter();
                            resetScrollSecurity();
                        }
                    } else if (isAtTop && deltaY < 0 && securityScrollEnabled) {
                        // Track upward scroll distance when at top
                        totalScrollDistance += Math.abs(deltaY);
                        initialTouchY = currentY;

                        // Check if user has scrolled quarter screen height upward
                        if (totalScrollDistance >= QuarterScreenHeight) {
                            // Trigger loading previous chapter
                            loadPreviousChapter();
                            resetScrollSecurity();
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (
                        (isBottomReached || isAtTop) && !securityScrollEnabled
                    ) {
                        // First time reaching edge, enable security
                        securityScrollEnabled = true;
                        totalScrollDistance = 0;
                    }
                    break;
            }

            // Let the ScrollView handle the event
            return v.onTouchEvent(event);
        });

        scrollView
            .getViewTreeObserver()
            .addOnScrollChangedListener(() -> {
                if (
                    !scrollView.canScrollVertically(1) && !isLoading && hasData
                ) {
                    // Reached bottom, activate security scroll
                    isBottomReached = true;
                    isAtTop = false;
                } else if (
                    !scrollView.canScrollVertically(-1) && !isLoading && hasData
                ) {
                    // Reached top, activate security scroll
                    isAtTop = true;
                    isBottomReached = false;
                } else {
                    // Not at edges anymore, reset security
                    resetScrollSecurity();
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

    @Override
    public void onSelectionChanged(int selectedCount) {
        if (selectedCount > 0) {
            deleteSelectedButton.setVisibility(View.VISIBLE);
            selectionMode = true;
        } else {
            deleteSelectedButton.setVisibility(View.GONE);
            selectionMode = false;
        }
    }

    private void loadExistingContent(String data, String url) {
        if (data != null && !data.isEmpty()) {
            String[] paragraphs = data.split(" ,");
            if (url != null && !url.isEmpty()) {
                for (String paragraph : paragraphs) {
                    if (paragraph != null && paragraph.contains("http")) {
                        StringBuilder newParagraph = new StringBuilder();
                        for (int j = 0; j < paragraph.length(); j++) {
                            if (
                                paragraph.charAt(j) == 'h' &&
                                j + 3 < paragraph.length() &&
                                paragraph.charAt(j + 1) == 't' &&
                                paragraph.charAt(j + 2) == 't' &&
                                paragraph.charAt(j + 3) == 'p'
                            ) {
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
                        Picasso.get()
                            .load(String.valueOf(newParagraph))
                            .into(imageView);
                        LinearLayout.LayoutParams LayoutParams =
                            new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            );
                        imageView.setLayoutParams(LayoutParams);
                        parentLayout.addView(imageView);
                    } else if (paragraph != null) {
                        TextView textView = getView(
                            getStringBuilder(
                                Collections.singletonList(paragraph),
                                0,
                                false
                            ) +
                            "\n"
                        );
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
        SharedPreferences sharedPreferences = getSharedPreferences(
            "LibraryItems",
            MODE_PRIVATE
        );
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
        SharedPreferences sharedPreferences = getSharedPreferences(
            "LibraryItems",
            MODE_PRIVATE
        );
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

        LibraryItem newItem = new LibraryItem(
            title,
            url,
            System.currentTimeMillis(),
            type
        );
        libraryItems.add(newItem);
        libraryAdapter.setData(libraryItems);
        saveLibraryData();
    }

    private void deleteSelectedItems() {
        List<LibraryItem> selectedItems = libraryAdapter.getSelectedItems();
        if (selectedItems.isEmpty()) {
            return;
        }

        // Check if currently reading chapter is being deleted
        boolean deletingCurrentChapter = false;
        for (LibraryItem item : selectedItems) {
            if (item.getUrl().equals(currentUrl)) {
                deletingCurrentChapter = true;
                break;
            }
        }

        // Save URLs of deleted items to clean their data
        Set<String> deletedUrls = new HashSet<>();
        for (LibraryItem item : selectedItems) {
            deletedUrls.add(item.getUrl());
        }

        // Remove items from library
        libraryItems.removeAll(selectedItems);
        libraryAdapter.setData(libraryItems);
        saveLibraryData();

        // Clean up cached HTML files and progress for all deleted items
        cleanupDeletedChapterData(deletedUrls);

        // If currently reading chapter was deleted, clear the reader
        if (deletingCurrentChapter) {
            parentLayout.removeAllViews();
            hasData = false;
            currentUrl = "";

            // Clear stored paragraph data
            SharedPreferences sharedPreferences = getSharedPreferences(
                "MySharedPref",
                MODE_PRIVATE
            );
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("paragraphs", "");
            editor.putString("url", "");
            editor.apply();

            // Update UI
            urlInput.setText("");
        }
    }

    /**
     * Cleans up all data associated with deleted chapters
     * @param deletedUrls Set of URLs of deleted chapters
     */
    private void cleanupDeletedChapterData(Set<String> deletedUrls) {
        if (deletedUrls.isEmpty()) {
            return;
        }

        // 1. Clean any cached HTML files
        File downloadsDirectory = getExternalFilesDir(
            Environment.DIRECTORY_DOWNLOADS
        );
        if (downloadsDirectory != null && downloadsDirectory.exists()) {
            for (String url : deletedUrls) {
                File htmlFile = new File(
                    downloadsDirectory,
                    url.hashCode() + ".html"
                );
                if (htmlFile.exists()) {
                    boolean deleted = htmlFile.delete();
                    if (!deleted) {
                        Log.w(
                            "MainActivity",
                            "Failed to delete cache file for " + url
                        );
                    }
                }
            }
        }

        // 2. Clean any stored progress data in SharedPreferences
        // Retrieve all chapters that have progress stored
        SharedPreferences progressPrefs = getSharedPreferences(
            "ChapterProgress",
            MODE_PRIVATE
        );
        SharedPreferences.Editor progressEditor = progressPrefs.edit();

        // Remove progress data for deleted chapters
        for (String url : deletedUrls) {
            progressEditor.remove(url);
            progressEditor.remove(url + "_timestamp");
        }

        progressEditor.apply();
    }

    private void exitSelectionMode() {
        selectionMode = false;
        libraryAdapter.setSelectionMode(false);
        libraryAdapter.clearSelections();
        deleteSelectedButton.setVisibility(View.GONE);
    }

    @Override
    public void onItemClick(LibraryItem item) {
        if (selectionMode) {
            // In selection mode, clicking toggles selection
            item.setSelected(!item.isSelected());
            libraryAdapter.notifySelectionChanged();
        } else {
            // Save progress in current chapter before loading new one
            updateReadingProgress(findViewById(R.id.scrollView).getScrollY());
            saveLibraryData();

            // Normal mode, load content
            urlInput.setText(item.getUrl());
            currentUrl = item.getUrl();
            loadItemContent(item);
            libraryAdapter.updateCurrentlyReading(currentUrl);
            drawerLayout.closeDrawers();
        }
    }

    @Override
    public void onItemLongClick(LibraryItem item) {
        if (!selectionMode) {
            selectionMode = true;
            libraryAdapter.setSelectionMode(true);
            item.setSelected(true);
            libraryAdapter.notifySelectionChanged();
        }
    }

    private void loadItemContent(LibraryItem item) {
        String url = item.getUrl();
        if (url == null || url.isEmpty()) {
            return;
        }

        // Update currently reading status
        libraryAdapter.updateCurrentlyReading(url);
        currentUrl = url;

        // Save item's progress for later scrolling
        final int savedProgress = item.getProgress();

        switch (item.getType()) {
            case "web":
                new WebScrapingTask(false, savedProgress).execute(url);
                break;
            case "pdf": {
                Uri uri = Uri.parse(url);
                new ParseFileTask(false, savedProgress).execute(uri);
                break;
            }
            case "html": {
                Uri uri = Uri.parse(url);
                new ParseHTMLFileTask(false, savedProgress).execute(uri);
                break;
            }
        }
    }

    private void openFileSelector() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimetypes = {
            "text/html",
            "application/xhtml+xml",
            "application/pdf",
            "application/epub",
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
        startActivityForResult(intent, PICK_HTML_FILE);
    }

    @Override
    public void onActivityResult(
        int requestCode,
        int resultCode,
        Intent resultData
    ) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == PICK_HTML_FILE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                Uri uri = resultData.getData();
                if (uri != null) {
                    String type = getContentResolver().getType(uri);
                    if (type != null) {
                        if (type.equals("application/pdf")) {
                            addToLibrary(
                                uri.toString(),
                                getFileName(uri),
                                "pdf"
                            );
                            new ParseFileTask().execute(uri);
                        } else if (
                            type.equals("text/html") ||
                            type.equals("application/xhtml+xml")
                        ) {
                            addToLibrary(
                                uri.toString(),
                                getFileName(uri),
                                "html"
                            );
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
            try (
                android.database.Cursor cursor = getContentResolver()
                    .query(uri, null, null, null, null)
            ) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(
                        android.provider.OpenableColumns.DISPLAY_NAME
                    );
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
            textView.setText(
                Html.fromHtml(paragraph, Html.FROM_HTML_MODE_LEGACY)
            );
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
            textView.setJustificationMode(
                LineBreaker.JUSTIFICATION_MODE_INTER_WORD
            );
        }
        return textView;
    }

    private String getParagraphsAsString() {
        StringBuilder paragraphs = new StringBuilder();
        for (int i = 0; i < parentLayout.getChildCount(); i++) {
            if (parentLayout.getChildAt(i) instanceof TextView textView) {
                if (textView.getText() != null) {
                    paragraphs
                        .append(textView.getText().toString())
                        .append(" ,");
                }
            }
        }
        return paragraphs.toString();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ScrollView scrollView = findViewById(R.id.scrollView);
        int scrollY = scrollView.getScrollY();

        // Calculate reading progress
        updateReadingProgress(scrollY);

        SharedPreferences sharedPreferences = getSharedPreferences(
            "MySharedPref",
            MODE_PRIVATE
        );
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("scrollY", scrollY);
        editor.putString("paragraphs", getParagraphsAsString());
        editor.putString("url", currentUrl);
        editor.apply();

        // Also save library data
        saveLibraryData();
    }

    /**
     * Calculate and update reading progress for the current chapter
     */
    private void updateReadingProgress(int scrollY) {
        if (currentUrl == null || currentUrl.isEmpty()) {
            return;
        }

        ScrollView scrollView = findViewById(R.id.scrollView);
        if (scrollView == null || scrollView.getChildCount() == 0) {
            return;
        }

        int viewHeight = scrollView.getHeight();
        int contentHeight = scrollView.getChildAt(0).getHeight();

        // Don't update progress if content is shorter than view
        if (contentHeight <= viewHeight) {
            return;
        }

        // Calculate progress percentage
        int progress = Math.min(
            100,
            Math.max(
                0,
                (int) ((100.0f * scrollY) / (contentHeight - viewHeight))
            )
        );

        // Update the appropriate library item
        for (LibraryItem item : libraryItems) {
            if (item.getUrl().equals(currentUrl)) {
                item.setProgress(progress);
                break;
            }
        }

        // Also update the adapter to refresh the UI
        libraryAdapter.updateReadingProgress(currentUrl, progress);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getSharedPreferences(
            "MySharedPref",
            MODE_PRIVATE
        );
        int scrollY = sharedPreferences.getInt("scrollY", 0);
        String data = sharedPreferences.getString("paragraphs", "");
        currentUrl = sharedPreferences.getString("url", "");
        parentLayout.removeAllViews();

        loadExistingContent(data, currentUrl);

        // Update reading status in library
        libraryAdapter.updateCurrentlyReading(currentUrl);

        final ScrollView scrollView = findViewById(R.id.scrollView);
        scrollView.post(() -> scrollView.scrollTo(0, scrollY));

        // Also refresh library data
        loadLibraryData();
    }

    private class SwipeGestureListener
        extends GestureDetector.SimpleOnGestureListener {

        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;
        private static final int EDGE_SIZE = 50; // Edge size in pixels for drawer activation

        @Override
        public boolean onDown(@NonNull MotionEvent e) {
            // Only return true to indicate we're interested in subsequent events
            return false;
        }

        @Override
        public boolean onFling(
            @NonNull MotionEvent e1,
            @NonNull MotionEvent e2,
            float velocityX,
            float velocityY
        ) {
            boolean result = false;
            try {
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                float startX = e1.getX();
                boolean isNearLeftEdge =
                    startX <
                    EDGE_SIZE * getResources().getDisplayMetrics().density;

                // Only handle drawer opening from left edge
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (
                        Math.abs(diffX) > SWIPE_THRESHOLD &&
                        Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                    ) {
                        if (diffX > 0 && isNearLeftEdge) {
                            // Right swipe from left edge - open drawer
                            drawerLayout.openDrawer(
                                findViewById(R.id.navDrawer)
                            );
                            result = true;
                        } else if (
                            diffX < 0 &&
                            drawerLayout.isDrawerOpen(
                                findViewById(R.id.navDrawer)
                            )
                        ) {
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
        private final boolean scrollToBottom;
        private final int savedProgress;

        public ParseFileTask() {
            this.scrollToBottom = false;
            this.savedProgress = 0;
        }

        public ParseFileTask(boolean scrollToBottom, int savedProgress) {
            this.scrollToBottom = scrollToBottom;
            this.savedProgress = savedProgress;
        }

        @Override
        protected List<String> doInBackground(Uri... uris) {
            fileUri = uris[0];
            List<String> paragraphs = new ArrayList<>();

            try {
                InputStream inputStream = getContentResolver()
                    .openInputStream(fileUri);
                if (inputStream == null) {
                    return paragraphs;
                }

                PdfReader reader = new PdfReader(inputStream);
                PdfDocument pdfDoc = new PdfDocument(reader);
                StringBuilder text = new StringBuilder();

                int numberOfPages = pdfDoc.getNumberOfPages();
                for (int i = 1; i <= numberOfPages; i++) {
                    text.append(
                        PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i))
                    );
                }

                pdfDoc.close();
                paragraphs.add(text.toString());
                for (int i = 0; i < paragraphs.size(); i++) {
                    // This is PDF content - apply page number processing
                    StringBuilder newParagraph = getStringBuilder(
                        paragraphs,
                        i,
                        true
                    );
                    paragraphs.set(i, newParagraph.toString());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            SharedPreferences sharedPreferences = getSharedPreferences(
                "MySharedPref",
                MODE_PRIVATE
            );
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

            // Update current reading status
            currentUrl = fileUri.toString();
            libraryAdapter.updateCurrentlyReading(currentUrl);

            if (paragraphs != null && !paragraphs.isEmpty()) {
                TextView textView = getView(paragraphs.get(0));
                parentLayout.addView(textView);
            }

            // Handle scrolling based on context
            ScrollView scrollView = findViewById(R.id.scrollView);

            if (scrollToBottom && hasData) {
                scrollView.post(() ->
                    scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                );
            } else if (savedProgress > 0 && hasData) {
                scrollView.post(() ->
                    scrollToSavedPosition(scrollView, savedProgress)
                );
            } else {
                scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_UP)
                );
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class ParseHTMLFileTask extends AsyncTask<Uri, Void, List<String>> {

        private Uri htmlUri;
        private final boolean scrollToBottom;
        private final int savedProgress;

        public ParseHTMLFileTask() {
            this.scrollToBottom = false;
            this.savedProgress = 0;
        }

        public ParseHTMLFileTask(boolean scrollToBottom, int savedProgress) {
            this.scrollToBottom = scrollToBottom;
            this.savedProgress = savedProgress;
        }

        @Override
        protected List<String> doInBackground(Uri... uris) {
            htmlUri = uris[0];
            List<String> paragraphs = new ArrayList<>();
            try {
                InputStream inputStream = getContentResolver()
                    .openInputStream(htmlUri);
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

            SharedPreferences sharedPreferences = getSharedPreferences(
                "MySharedPref",
                MODE_PRIVATE
            );
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

            // Update current reading status
            currentUrl = htmlUri.toString();
            libraryAdapter.updateCurrentlyReading(currentUrl);

            if (paragraphs != null) {
                for (String paragraph : paragraphs) {
                    if (paragraph != null && paragraph.contains("http")) {
                        ImageView imageView = new ImageView(MainActivity.this);
                        Picasso.get().load(paragraph).into(imageView);
                        LinearLayout.LayoutParams LayoutParams =
                            new LinearLayout.LayoutParams(
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

            // Handle scrolling based on context
            if (scrollToBottom && hasData) {
                scrollView.post(() ->
                    scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                );
            } else if (savedProgress > 0 && hasData) {
                scrollView.post(() ->
                    scrollToSavedPosition(scrollView, savedProgress)
                );
            } else {
                scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_UP)
                );
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class WebScrapingTask
        extends AsyncTask<String, Void, List<String>> {

        private String webUrl;
        private final boolean scrollToBottom;
        private final int savedProgress;

        public WebScrapingTask() {
            this.scrollToBottom = false;
            this.savedProgress = 0;
        }

        public WebScrapingTask(boolean scrollToBottom) {
            this.scrollToBottom = scrollToBottom;
            this.savedProgress = 0;
        }

        public WebScrapingTask(boolean scrollToBottom, int savedProgress) {
            this.scrollToBottom = scrollToBottom;
            this.savedProgress = savedProgress;
        }

        @Override
        protected List<String> doInBackground(String... strings) {
            webUrl = strings[0];
            List<String> paragraphs = new ArrayList<>();

            if (webUrl == null || webUrl.isEmpty()) {
                return paragraphs;
            }

            File downloadsDirectory = getExternalFilesDir(
                Environment.DIRECTORY_DOWNLOADS
            );
            if (downloadsDirectory == null) {
                return paragraphs;
            }

            File htmlFile = new File(
                downloadsDirectory,
                webUrl.hashCode() + ".html"
            );

            if (!htmlFile.exists()) {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(webUrl).build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) throw new IOException(
                        "Unexpected code " + response
                    );

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
                            StringBuilder styledParagraph = getStringBuilder(
                                Collections.singletonList(paragraph),
                                0,
                                false
                            );
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

            SharedPreferences sharedPreferences = getSharedPreferences(
                "MySharedPref",
                MODE_PRIVATE
            );
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
            if (
                paragraphs != null &&
                !paragraphs.isEmpty() &&
                paragraphs.get(0).startsWith("# ")
            ) {
                title = paragraphs.get(0).substring(2);
            }

            if (paragraphs != null) {
                for (String paragraph : paragraphs) {
                    if (paragraph != null && paragraph.contains("http")) {
                        ImageView imageView = new ImageView(MainActivity.this);
                        Picasso.get().load(paragraph).into(imageView);
                        LinearLayout.LayoutParams LayoutParams =
                            new LinearLayout.LayoutParams(
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

            // Update current reading status
            currentUrl = webUrl;
            libraryAdapter.updateCurrentlyReading(webUrl);

            // Delayed actions
            new Handler()
                .postDelayed(
                    () -> {
                        isLoading = false;

                        ScrollView scrollView = findViewById(R.id.scrollView);

                        if (scrollToBottom && hasData) {
                            // For previous chapter loading, scroll to bottom
                            scrollView.post(() ->
                                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                            );
                        } else if (savedProgress > 0 && hasData) {
                            // For loading from library with saved progress
                            scrollView.post(() ->
                                scrollToSavedPosition(scrollView, savedProgress)
                            );
                        } else {
                            // Default - scroll to top
                            scrollView.post(() ->
                                scrollView.fullScroll(ScrollView.FOCUS_UP)
                            );
                        }
                    },
                    1000
                ); // Shorter delay for better UX
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

            File downloadsDirectory = getExternalFilesDir(
                Environment.DIRECTORY_DOWNLOADS
            );
            if (downloadsDirectory == null) {
                return null;
            }

            File htmlFile = new File(
                downloadsDirectory,
                url.hashCode() + ".html"
            );

            if (!htmlFile.exists()) {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url).build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) throw new IOException(
                        "Unexpected code " + response
                    );

                    if (response.body() != null) {
                        BufferedSink sink = Okio.buffer(Okio.sink(htmlFile));
                        sink.writeAll(response.body().source());
                        sink.close();

                        // Extract title from downloaded HTML
                        String title = null;
                        try {
                            Document document = Jsoup.parse(htmlFile, "UTF-8");
                            title = document.title();
                            if (title.isEmpty()) {
                                Element titleElement = document.selectFirst(
                                    "h1"
                                );
                                if (titleElement != null) {
                                    title = titleElement.text();
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        // Add chapter to library in background
                        final String finalTitle = title;
                        // Need to do this on UI thread to avoid concurrency issues
                        runOnUiThread(() -> {
                            // Check if URL already exists before adding
                            boolean exists = false;
                            for (LibraryItem item : libraryItems) {
                                if (item.getUrl().equals(url)) {
                                    exists = true;
                                    break;
                                }
                            }

                            if (!exists) {
                                LibraryItem newItem = new LibraryItem(
                                    finalTitle,
                                    url,
                                    System.currentTimeMillis(),
                                    "web"
                                );
                                libraryItems.add(newItem);
                                saveLibraryData();
                            }
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            // Update the library UI with any new chapters
            libraryAdapter.setData(libraryItems);
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
                    bionicText
                        .append("<b>")
                        .append(word.substring(0, splitIndex))
                        .append("</b>")
                        .append(word.substring(splitIndex))
                        .append(" ");
                } else {
                    bionicText.append(word).append(" ");
                }
            }
        }
        return bionicText.toString().trim();
    }

    /**
     * Reset the scroll security mechanism
     */
    private void resetScrollSecurity() {
        isBottomReached = false;
        securityScrollEnabled = false;
        totalScrollDistance = 0;
    }

    /**
     * Load next chapter when scroll security check passes
     */
    private void loadNextChapter() {
        isLoading = true;
        String url = urlInput.getText().toString();
        if (!url.isEmpty()) {
            // Store current chapter URL
            previousChapterUrl = url;

            // Save progress for current chapter
            updateReadingProgress(findViewById(R.id.scrollView).getScrollY());
            saveLibraryData();

            // Get next chapter URL
            url = incrementChapterInUrl(url);
            urlInput.setText(url);

            // Update current URL
            currentUrl = url;
            libraryAdapter.updateCurrentlyReading(url);

            // Save current content before loading new chapter
            SharedPreferences sharedPreferences = getSharedPreferences(
                "MySharedPref",
                MODE_PRIVATE
            );
            SharedPreferences.Editor myEdit = sharedPreferences.edit();
            myEdit.putString("url", url);
            myEdit.apply();

            new WebScrapingTask().execute(url);
        } else {
            isLoading = false;
        }
    }

    /**
     * Decrement chapter in URL - inverse of incrementChapterInUrl
     */
    private String decrementChapterInUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        Pattern pattern = Pattern.compile("(\\d+)(?!.*\\d)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            int chapterNumber = Integer.parseInt(
                Objects.requireNonNull(matcher.group(1))
            );
            if (chapterNumber > 1) { // Don't go below chapter 1
                chapterNumber--;
                url = matcher.replaceFirst(String.valueOf(chapterNumber));
            }
        }
        return url;
    }

    /**
     * Load the previous chapter when scrolling up at the top
     */
    private void loadPreviousChapter() {
        isLoading = true;
        String url = urlInput.getText().toString();

        if (!url.isEmpty()) {
            // Store the current chapter URL before changing
            previousChapterUrl = url;

            // Save current progress for current chapter
            updateReadingProgress(findViewById(R.id.scrollView).getScrollY());
            saveLibraryData();

            // Decrement to previous chapter
            url = decrementChapterInUrl(url);

            // If URL didn't change (likely at chapter 1), don't try to load
            if (url.equals(previousChapterUrl)) {
                isLoading = false;
                return;
            }

            urlInput.setText(url);
            currentUrl = url;

            // Load the previous chapter
            new WebScrapingTask(true).execute(url);
        } else {
            isLoading = false;
        }
    }

    /**
     * Scrolls to a position based on saved progress percentage
     */
    private void scrollToSavedPosition(
        ScrollView scrollView,
        int progressPercentage
    ) {
        if (
            scrollView == null ||
            scrollView.getChildCount() == 0 ||
            progressPercentage <= 0
        ) {
            return;
        }

        // Get content height
        int contentHeight = scrollView.getChildAt(0).getHeight();
        int viewHeight = scrollView.getHeight();

        // Don't scroll if content fits within view
        if (contentHeight <= viewHeight) {
            return;
        }

        // Calculate scroll position from progress percentage
        int maxScroll = contentHeight - viewHeight;
        int scrollPosition = (progressPercentage * maxScroll) / 100;

        // Scroll to position
        scrollView.scrollTo(0, scrollPosition);
    }
}
