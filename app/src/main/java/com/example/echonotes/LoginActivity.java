package com.example.echonotes;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    EditText emailEditText, passwordEditText;
    ImageView eyeIcon;
    Button loginButton;
    boolean isPasswordVisible = false;
    FirebaseAuth auth;  // Firebase Authentication instance

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        auth = FirebaseAuth.getInstance(); // Initialize Firebase Auth

        emailEditText = findViewById(R.id.email);
        passwordEditText = findViewById(R.id.password);
        eyeIcon = findViewById(R.id.eye_icon);
        loginButton = findViewById(R.id.login_button);

        // Toggle password visibility
        eyeIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isPasswordVisible) {
                    passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                } else {
                    passwordEditText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                }
                isPasswordVisible = !isPasswordVisible;
                passwordEditText.setSelection(passwordEditText.getText().length());
            }
        });

        TextView signupText = findViewById(R.id.signup_text);
        signupText.setOnClickListener(view -> {
            Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
            startActivity(intent);
        });

        // Login button with Firebase Authentication
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String email = emailEditText.getText().toString().trim();
                String password = passwordEditText.getText().toString().trim();

                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(LoginActivity.this, "Please enter all fields", Toast.LENGTH_SHORT).show();
                } else {
                    loginUser(email, password);
                }
            }
        });
    }


    private void loginUser(String email, String password) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            Toast.makeText(LoginActivity.this, "Login Successful", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                            intent.putExtra("user_email", user.getEmail()); // Pass email to next activity
                            startActivity(intent);
                            finish(); // Close login activity
                        }
                    } else {
                        Toast.makeText(LoginActivity.this, "Login Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}
