package com.example.echonotes;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {
    private static final String TAG = "SignupActivity";

    private EditText nameField, emailField, passwordField, confirmPasswordField;
    private Button signupButton;
    private TextView alreadyHaveAccount;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Initialize Cloud Firestore with explicit check
        try {
            db = FirebaseFirestore.getInstance();
            Log.d(TAG, "Firestore initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firestore", e);
            Toast.makeText(this, "Failed to initialize Firestore: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        // Initialize UI components
        nameField = findViewById(R.id.name);
        emailField = findViewById(R.id.email);
        passwordField = findViewById(R.id.password);
        confirmPasswordField = findViewById(R.id.confirm_password);
        signupButton = findViewById(R.id.signup_button);
        alreadyHaveAccount = findViewById(R.id.already_have_account);



        signupButton.setOnClickListener(view -> registerUser());

        alreadyHaveAccount.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void registerUser() {
        final String name = nameField.getText().toString().trim();
        final String email = emailField.getText().toString().trim();
        final String password = passwordField.getText().toString().trim();
        String confirmPassword = confirmPasswordField.getText().toString().trim();

        // Input validation
        if (TextUtils.isEmpty(name)) {
            nameField.setError("Name is required");
            return;
        }

        if (TextUtils.isEmpty(email)) {
            emailField.setError("Email is required");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordField.setError("Password is required");
            return;
        }

        if (password.length() < 6) {
            passwordField.setError("Password must be at least 6 characters");
            return;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordField.setError("Passwords do not match");
            return;
        }

        // Show loading state
        signupButton.setEnabled(false);
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        // Verify Firestore is initialized
        if (db == null) {
            try {
                db = FirebaseFirestore.getInstance();
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize Firestore", e);
                Toast.makeText(SignupActivity.this,
                        "Cannot connect to database. Check your internet connection.",
                        Toast.LENGTH_LONG).show();
                signupButton.setEnabled(true);
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                return;
            }
        }

        // Register user in Firebase Authentication
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "createUserWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                String uid = user.getUid();

                                // Create a HashMap for user data
                                Map<String, Object> userData = new HashMap<>();
                                userData.put("name", name);
                                userData.put("email", email);
                                userData.put("createdAt", System.currentTimeMillis());

                                // Add user to Firestore with explicit error handling
                                db.collection("users")
                                        .document(uid)
                                        .set(userData)
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d(TAG, "DocumentSnapshot successfully written!");
                                            Toast.makeText(SignupActivity.this, "Account created successfully!",
                                                    Toast.LENGTH_SHORT).show();
                                            // Navigate to home screen
                                            Intent intent = new Intent(SignupActivity.this, HomeActivity.class);
                                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                            startActivity(intent);
                                            finish();
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Error writing document", e);
                                            Toast.makeText(SignupActivity.this,
                                                    "User created but profile data couldn't be saved: " + e.getMessage(),
                                                    Toast.LENGTH_LONG).show();
                                            // Still navigate to home screen as auth was successful
                                            Intent intent = new Intent(SignupActivity.this, HomeActivity.class);
                                            startActivity(intent);
                                            finish();
                                        });
                            }
                        } else {
                            Log.w(TAG, "createUserWithEmail:failure", task.getException());
                            String errorMessage = "Authentication failed";
                            if (task.getException() != null) {
                                errorMessage = task.getException().getMessage();
                            }
                            Toast.makeText(SignupActivity.this, "Signup failed: " + errorMessage,
                                    Toast.LENGTH_LONG).show();
                        }

                        // Re-enable button
                        signupButton.setEnabled(true);
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                    }
                });
    }
}