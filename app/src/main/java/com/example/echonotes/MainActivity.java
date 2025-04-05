package com.example.echonotes;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Start SignUpActivity
        Intent intent = new Intent(MainActivity.this, SignupActivity.class);
        startActivity(intent);
        finish(); // Close MainActivity so it's not in back stack
    }
}
