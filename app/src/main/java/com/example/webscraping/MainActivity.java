package com.example.webscraping;

import android.content.SharedPreferences;
import android.graphics.text.LineBreaker;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

        // Set the retrieved paragraphs to the TextView the same way as in onPostExecute
        if (!data.isEmpty()) {
            String[] paragraphs = data.substring(1, data.length() - 1).split(", ");
            for (String paragraph : paragraphs) {
                TextView textView = new TextView(this);
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

        scrapButton.setOnClickListener(v -> {
            String url1 = urlInput.getText().toString();
            new WebScrapingTask().execute(url1);
        });
    }
        

    //TODO: add image scraping functionality, images are contained in between p tags and should be displayed in between the paragraphs with glide
    private class WebScrapingTask extends AsyncTask<String, Void, List<String>> {
        @Override
        protected List<String> doInBackground(String... strings) {
            String url = strings[0];
            List<String> paragraphs = new ArrayList<>();
            try {
                Document document = Jsoup.connect(url).get();
                Elements Elements = document.select("p, img");
                for (Element Element : Elements) {
                    if (Element.tagName().equals("p")){
                        paragraphs.add(Element.text() + "\n\n");}
                        else if (Element.tagName().equals("img")){
                            paragraphs.add(Element.attr("src"));
                        }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } //save the data for next restart
            SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
            SharedPreferences.Editor myEdit = sharedPreferences.edit();
            myEdit.putString("url", url);
            myEdit.putString("paragraphs", paragraphs.toString());
            myEdit.apply();
            return paragraphs;
        }

        @Override
            protected void onPostExecute(List<String> paragraphs) {
            // Update the text in existing TextViews
            for (int i = 0; i < parentLayout.getChildCount(); i++) { 
                View view = parentLayout.getChildAt(i);
                if (view instanceof TextView) {
                    TextView textView = (TextView) view;
                    if (i < paragraphs.size()) {
                        textView.setText(paragraphs.get(i));
                    } else {
                        textView.setText("");
                    }
                    textView.setLineSpacing(0,1.5f); // Set line spacing to 1.5 times the default

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            textView.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD); // Justify the text}
                        }
                    }

                }
            }
            
            
            // Add new TextViews if needed
            for (int i = parentLayout.getChildCount(); i < paragraphs.size(); i++) {
                TextView textView = new TextView(MainActivity.this);
                textView.setText(paragraphs.get(i));
                textView.setTextSize(20);

                // Set the margin
                LinearLayout.LayoutParams LayoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );

                LayoutParams.setMargins (0,10,0,10); // Top and bottom margins are 10
                textView.setLayoutParams(LayoutParams);

                textView.setLineSpacing (0,1.5f); // Set line spacing to 1.5 times the default

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        textView.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD); // Justify the text
                    }
                }


                parentLayout.addView(textView);
            }
        }
    }
}