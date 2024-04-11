package com.example.webscraping;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.text.LineBreaker;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

public class MainActivity extends AppCompatActivity {
    private LinearLayout parentLayout;
    private EditText urlInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main);
        Window window = getWindow();
        window.addFlags (WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags (WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor (ContextCompat.getColor(this, R.color.BLACK));

        parentLayout = findViewById(R.id.parentLayout);
        urlInput=findViewById(R.id.urlInput);
        Button scrapButton = findViewById(R.id.scrapButton);
        // Retrieve saved data
        SharedPreferences sharedPreferences = getSharedPreferences ("MySharedPref", MODE_PRIVATE);
        String url = sharedPreferences.getString("url", "");
        String data = sharedPreferences.getString("paragraphs", "");
        // Set the retrieved URL to the EditText
        urlInput.setText(url);

        // Set the retrieved paragraphs and images to the parent layout as TextViews and ImageViews as in the onPostExecute method
        if (!data.isEmpty()) {
            String[] paragraphs = data.substring(1, data.length() - 1).split(", ");
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
                    TextView textView = new TextView(MainActivity.this);
                    textView.setText(paragraph);
                    textView.setTextSize(20);
                    LinearLayout.LayoutParams LayoutParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    LayoutParams.setMargins(0, 10, 0, 10);
                    textView.setLayoutParams(LayoutParams);
                    textView.setLineSpacing(0, 1.5f);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            textView.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD);
                        }
                    }
                    parentLayout.addView(textView);
                }
            }
        }

        scrapButton.setOnClickListener(v -> {
            String url1 = urlInput.getText().toString();
            if (url1.isEmpty()) {
                openFileSelector();
            } else {
                new WebScrapingTask().execute(url1);
            }
        });
    }
//TODO: Fix the issue of the app crashing when the user selects a file
    private static final int PICK_HTML_FILE = 1;

    private void openFileSelector() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimetypes = {"text/html", "application/xhtml+xml", "application/pdf"};
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
                    String uriString = uri.toString();
                    if (uriString.endsWith(".pdf")) {
                        parsePDF(uri);
                    } else {
                        new WebScrapingTask().execute(uriString);
                    }
                }
            }
        }
    }

    private void parsePDF(Uri pdfUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(pdfUri);
            PdfReader reader = new PdfReader(inputStream);
            PdfDocument pdfDoc = new PdfDocument(reader);
            String text = "";

            int numberOfPages = pdfDoc.getNumberOfPages();
            for (int i = 1; i <= numberOfPages; i++) {
                text += PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i));
            }

            pdfDoc.close();

            // Now you have the text of the PDF file. You can process it as needed.
            // For example, you can split it into paragraphs and add them to the parent layout.
            String[] paragraphs = text.split("\n\n");
            for (String paragraph : paragraphs) {
                TextView textView = new TextView(MainActivity.this);
                textView.setText(paragraph);
                textView.setTextSize(20);
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                layoutParams.setMargins(0, 10, 0, 10);
                textView.setLayoutParams(layoutParams);
                textView.setLineSpacing(0, 1.5f);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        textView.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD);
                    }
                }
                parentLayout.addView(textView);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //TODO: Add multi chapter support
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
                    if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                    BufferedSink sink = Okio.buffer(Okio.sink(htmlFile));
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
            myEdit.putString("url", url);
            myEdit.putString("paragraphs", paragraphs.toString());
            myEdit.apply();

            return paragraphs;
        }

        @Override
        protected void onPostExecute(List<String> paragraphs) {
            // Remove all existing TextViews and ImageViews
            for (int i = 0; i < parentLayout.getChildCount(); i++) {
                View view = parentLayout.getChildAt(i);
                if (view instanceof TextView || view instanceof ImageView) {
                    parentLayout.removeView(view);
                    i--; // Decrement the counter as the child count has changed
                }
            }
            //if the paragraph is an image, display it with picasso else display it as a text
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
                    TextView textView = new TextView(MainActivity.this);
                    textView.setText(paragraph);
                    textView.setTextSize(20);
                    LinearLayout.LayoutParams LayoutParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    LayoutParams.setMargins(0, 10, 0, 10);
                    textView.setLayoutParams(LayoutParams);
                    textView.setLineSpacing(0, 1.5f);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            textView.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD);
                        }
                    }
                    parentLayout.addView(textView);
                }
            }
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        // Get the ScrollView
        ScrollView scrollView = findViewById(R.id.scrollView);
        // Get the current scroll position
        int scrollY = scrollView.getScrollY();
        // Save the scroll position in SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("scrollY", scrollY);
        editor.apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Get the saved scroll position from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        int scrollY = sharedPreferences.getInt("scrollY", 0);
        // Get the ScrollView
        final ScrollView scrollView = findViewById(R.id.scrollView);
        // Set the scroll position
        scrollView.post(new Runnable() {
            public void run() {
                scrollView.scrollTo(0, scrollY);
            }
        });
    }
}