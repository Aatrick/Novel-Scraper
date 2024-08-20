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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

public class MainActivity extends AppCompatActivity {
    private boolean hasData = false;
    private static final int PICK_HTML_FILE = 1;
    private LinearLayout parentLayout;
    private EditText urlInput;
    private boolean isLoading = false;
    private static int pageNumber = 0;
    @NonNull
    private static StringBuilder getStringBuilder(List<String> paragraphs, int i) {
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
                if (c==',' && prevChar=='”') {
                    newParagraph.deleteCharAt(newParagraph.length() - 1);
                }
                if (c == '\n' && prevChar == '.') {
                    newParagraph.append("\n\n\n");
                }
                if (c == '\n' && prevChar == '”') {
                    newParagraph.append("\n\n\n");
                }
                if (c == '\n' && prevPrevChar == '.') {
                    newParagraph.append("\n\n\n");
                }
                if (c == '\n' && prevPrevChar == '”') {
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
        String regex = "(Page \\||Page )";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(paragraph);
        return matcher.replaceAll("");
    }
    private String incrementChapterInUrl(String url) {
        Pattern pattern = Pattern.compile("(\\d+)(?!.*\\d)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            int chapterNumber = Integer.parseInt(matcher.group(1));
            chapterNumber++;
            url = matcher.replaceFirst(String.valueOf(chapterNumber));
        }
        return url;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.BLACK));
        ScrollView scrollView = findViewById(R.id.scrollView);
        parentLayout = findViewById(R.id.parentLayout);
        urlInput = findViewById(R.id.urlInput);
        Button scrapButton = findViewById(R.id.scrapButton);
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        String url = sharedPreferences.getString("url", "");
        String data = sharedPreferences.getString("paragraphs", "");
        urlInput.setText(url);


        if (!data.isEmpty()) {
            String[] paragraphs = data.split(" ,");
            if (!url.isEmpty()) {
                for (String paragraph : paragraphs) {
                    if (paragraph.contains("http")) {
                        StringBuilder newParagraph = new StringBuilder();
                        for (int j = 0; j < paragraph.length(); j++) {
                            if (paragraph.charAt(j) == 'h' && paragraph.charAt(j + 1) == 't' && paragraph.charAt(j + 2) == 't' && paragraph.charAt(j + 3) == 'p') {
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
                        LayoutParams.setMargins(0, 10, 0, 10);
                        imageView.setLayoutParams(LayoutParams);
                        parentLayout.addView(imageView);
                    } else {
                        TextView textView = getView(getStringBuilder(Collections.singletonList(paragraph), 0) + "\n");
                        parentLayout.addView(textView);
                    }
                }
            } else {
                TextView textView = getView(Arrays.toString(paragraphs));
                parentLayout.addView(textView);
            }
        }
        Button downloadButton = findViewById(R.id.downloadButton); // replace with your button's ID
        downloadButton.setOnClickListener(v -> {
            String url1 = urlInput.getText().toString();
            for (int i = 0; i < 100; i++) {
                url1 = incrementChapterInUrl(url1);
                new ScrapInAdvance().execute(url1);
            }
        });

        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (!scrollView.canScrollVertically(1) && !isLoading && hasData) {
                isLoading = true;
                String url12 = urlInput.getText().toString();
                url12 = incrementChapterInUrl(url12);
                urlInput.setText(url12);
                new WebScrapingTask().execute(url12);
                scrollView.fullScroll(ScrollView.FOCUS_UP);
            }
        });

        scrapButton.setOnClickListener(v -> {
            String url3 = urlInput.getText().toString();
            if (url3.isEmpty()) {
                openFileSelector();
            } else {
                new WebScrapingTask().execute(url3);
            }
        });
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
                            new ParseFileTask().execute(uri);
                        } else if (type.equals("text/html") || type.equals("application/xhtml+xml")) {
                            new ParseHTMLFileTask().execute(uri);
                        }
                    }
                }
            }
        }
    }

    @NonNull
    private TextView getView(String paragraph) {
        TextView textView = new TextView(MainActivity.this);
        String bionicText = applyBionicReading(paragraph);
        textView.setText(Html.fromHtml(bionicText, Html.FROM_HTML_MODE_LEGACY));
        textView.setTextSize(20);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        layoutParams.setMargins(0, 10, 0, 10);
        textView.setLayoutParams(layoutParams);
        textView.setLineSpacing(0, 1.5f); // Line spacing multiplier
        textView.setPadding(0, 20, 0, 20); // Add padding to create paragraph spacing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                textView.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD);
            }
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
    }

    private String getParagraphsAsString() {
        StringBuilder paragraphs = new StringBuilder();
        for (int i = 0; i < parentLayout.getChildCount(); i++) {
            if (parentLayout.getChildAt(i) instanceof TextView) {
                TextView textView = (TextView) parentLayout.getChildAt(i);
                paragraphs.append(textView.getText().toString()).append(" ,");
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
        if (!data.isEmpty()) {
            String[] paragraphs = data.split(" ,");
            parentLayout.removeAllViews();
            for (String paragraph : paragraphs) {
                TextView textView = getView(paragraph);
                parentLayout.addView(textView);
            }
        }
        final ScrollView scrollView = findViewById(R.id.scrollView);
        scrollView.post(() -> scrollView.scrollTo(0, scrollY));
    }

    @SuppressLint("StaticFieldLeak")
    private class ParseFileTask extends AsyncTask<Uri, Void, List<String>> {
        @Override
        protected List<String> doInBackground(Uri... uris) {
            Uri uri = uris[0];
            List<String> paragraphs = new ArrayList<>();

            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                assert inputStream != null;
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
            myEdit.putString("url", "");
            myEdit.putString("paragraphs", paragraphs.toString());
            myEdit.apply();

            return paragraphs;
        }

        @Override
        protected void onPostExecute(List<String> paragraphs) {
            parentLayout.removeAllViews();
            TextView textView = getView(paragraphs.toString());
            parentLayout.addView(textView);
            ScrollView scrollView = findViewById(R.id.scrollView);
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_UP));
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class ParseHTMLFileTask extends AsyncTask<Uri, Void, List<String>> {
        @Override
        protected List<String> doInBackground(Uri... uris) {
            Uri uri = uris[0];
            List<String> paragraphs = new ArrayList<>();
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                assert inputStream != null;
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
            myEdit.putString("url", "");
            myEdit.putString("paragraphs", paragraphs.toString());
            myEdit.apply();

            return paragraphs;
        }

        @Override
        protected void onPostExecute(List<String> paragraphs) {
            parentLayout.removeAllViews();
            for (String paragraph : paragraphs) {
                if (paragraph.contains("http")) {
                    ImageView imageView = new ImageView(MainActivity.this);
                    Picasso.get().load(paragraph).into(imageView);
                    LinearLayout.LayoutParams LayoutParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    LayoutParams.setMargins(0, 10, 0, 10);
                    imageView.setLayoutParams(LayoutParams);
                    parentLayout.addView(imageView);
                } else {
                    TextView textView = getView(paragraph);
                    parentLayout.addView(textView);
                }
            }
            ScrollView scrollView = findViewById(R.id.scrollView);
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_UP));
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class WebScrapingTask extends AsyncTask<String, Void, List<String>> {
        @Override
        protected List<String> doInBackground(String... strings) {
            String url = strings[0];
            List<String> paragraphs = new ArrayList<>();

            File downloadsDirectory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File htmlFile = new File(downloadsDirectory, url.hashCode() + ".html");

            if (!htmlFile.exists()) {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url).build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful())
                        throw new IOException("Unexpected code " + response);

                    BufferedSink sink = Okio.buffer(Okio.sink(htmlFile));
                    assert response.body() != null;
                    sink.writeAll(response.body().source());
                    sink.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try {
                Document document = Jsoup.parse(htmlFile, "UTF-8");
                Elements elements = document.select("p, img");
                for (Element element : elements) {
                    if (element.tagName().equals("p")) {
                        String paragraph = element.text();
                        StringBuilder styledParagraph = getStringBuilder(Collections.singletonList(paragraph), 0);
                        paragraphs.add(styledParagraph.toString());
                    } else if (element.tagName().equals("img")) {
                        paragraphs.add(element.attr("src"));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
            SharedPreferences.Editor myEdit = sharedPreferences.edit();
            myEdit.putString("url", url);
            myEdit.putString("paragraphs", paragraphs.toString());
            myEdit.apply();

            return paragraphs;
        }

        @Override
        protected void onPostExecute(List<String> paragraphs) {
            parentLayout.removeAllViews();
            hasData = !paragraphs.isEmpty();
            for (String paragraph : paragraphs) {
                if (paragraph.contains("http")) {
                    ImageView imageView = new ImageView(MainActivity.this);
                    Picasso.get().load(paragraph).into(imageView);
                    LinearLayout.LayoutParams LayoutParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    LayoutParams.setMargins(0, 10, 0, 10);
                    imageView.setLayoutParams(LayoutParams);
                    parentLayout.addView(imageView);
                } else {
                    TextView textView = getView(paragraph);
                    parentLayout.addView(textView);
                }
            }
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

            File downloadsDirectory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File htmlFile = new File(downloadsDirectory, url.hashCode() + ".html");

            if (!htmlFile.exists()) {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url).build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful())
                        throw new IOException("Unexpected code " + response);

                    BufferedSink sink = Okio.buffer(Okio.sink(htmlFile));
                    assert response.body() != null;
                    sink.writeAll(response.body().source());
                    sink.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return null;
        }
    }
    private String applyBionicReading(String text) {
        StringBuilder bionicText = new StringBuilder();
        String[] words = text.split("\\s+");
        for (String word : words) {
            int splitIndex = (int) Math.ceil(word.length() * 0.4); // Emphasize the first 40% of the word
            if (splitIndex > 0 && splitIndex < word.length()) {
                bionicText.append("<b>").append(word.substring(0, splitIndex)).append("</b>")
                        .append(word.substring(splitIndex)).append(" ");
            } else {
                bionicText.append(word).append(" ");
            }
        }
        return bionicText.toString().trim();
    }
}