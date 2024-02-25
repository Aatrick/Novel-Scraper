package com.example.webscraping;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private LinearLayout parentLayout;
    private EditText urlInput;
    private Button scrapButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.BLACK));
        }
    
        parentLayout = findViewById(R.id.parentLayout);
        urlInput = findViewById(R.id.urlInput);
        scrapButton = findViewById(R.id.scrapButton);
    
        // Retrieve saved data
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        String url = sharedPreferences.getString("url", "");
        String data = sharedPreferences.getString("paragraphs", "");
    
        // Set the retrieved URL to the EditText
        urlInput.setText(url);

        // Set the retrieved paragraphs to the TextView
        TextView textView = new TextView(MainActivity.this);
        textView.setText(data);
        textView.setTextSize(20);
        parentLayout.addView(textView);

    
        scrapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = urlInput.getText().toString();
                new WebScrapingTask().execute(url);
            }
        });
    }

    private class WebScrapingTask extends AsyncTask<String, Void, List<String>> {
        @Override
        protected List<String> doInBackground(String... strings) {
            String url = strings[0];
            List<String> paragraphs = new ArrayList<>();
            try {
                Document document = Jsoup.connect(url).get();
                Elements pElements = document.select("p");//select all paragraph tags and add two return to the line in between each paragraph
                for (Element pElement : pElements) {
                    paragraphs.add(pElement.text() + "\n\n");
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
            // Clear the parent layout
            parentLayout.removeAllViews();
        
            for (String paragraph : paragraphs) {
                TextView textView = new TextView(MainActivity.this);
                textView.setText(paragraph);
                textView.setTextSize(20);
            
                // Set the margin
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, 
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                layoutParams.setMargins(0, 10, 0, 10); // Top and bottom margins are 10
                textView.setLayoutParams(layoutParams);
            
                parentLayout.addView(textView);
            }
        }
    }
}