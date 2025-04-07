package com.example.echonotes;


import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupChatActivity extends AppCompatActivity {
    private static final String TAG = "GroupChatActivity";

    private String groupId;
    private String groupName;

    private Toolbar toolbar;
    private TextView tvGroupName;
    private RecyclerView recyclerMessages;
    private EditText etMessage;
    private ImageButton btnSend;
    private View progressBar;

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private List<Message> messageList;
    private MessageAdapter messageAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_chat);

        // Get group info from intent
        groupId = getIntent().getStringExtra("GROUP_ID");
        groupName = getIntent().getStringExtra("GROUP_NAME");

        if (TextUtils.isEmpty(groupId)) {
            Toast.makeText(this, "Invalid group", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        initializeFirebase();
        setupToolbar();
        setupRecyclerView();
        loadMessages();
        markMessagesAsRead();
        setupListeners();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        tvGroupName = findViewById(R.id.tvGroupName);
        recyclerMessages = findViewById(R.id.recyclerMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        progressBar = findViewById(R.id.progressBar);
    }

    private void initializeFirebase() {
        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        tvGroupName.setText(groupName);
    }

    private void setupRecyclerView() {
        messageList = new ArrayList<>();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerMessages.setLayoutManager(layoutManager);
        messageAdapter = new MessageAdapter(this, messageList, currentUser.getUid());
        recyclerMessages.setAdapter(messageAdapter);
    }

    private void loadMessages() {
        showProgressBar(true);

        db.collection("groups").document(groupId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error loading messages", error);
                        showProgressBar(false);
                        return;
                    }

                    if (value != null) {
                        for (DocumentChange dc : value.getDocumentChanges()) {
                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                Message message = dc.getDocument().toObject(Message.class);
                                messageList.add(message);
                            }
                        }

                        messageAdapter.notifyDataSetChanged();
                        if (messageList.size() > 0) {
                            recyclerMessages.smoothScrollToPosition(messageList.size() - 1);
                        }
                    }

                    showProgressBar(false);
                });
    }

    private void markMessagesAsRead() {
        if (currentUser != null) {
            db.collection("groups").document(groupId)
                    .collection("members").document(currentUser.getUid())
                    .update("lastRead", new Date());
        }
    }

    private void setupListeners() {
        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String messageText = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(messageText)) {
            return;
        }

        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to send messages", Toast.LENGTH_SHORT).show();
            return;
        }

        // Clear input field
        etMessage.setText("");

        // Create message document
        String messageId = db.collection("messages").document().getId();
        Date timestamp = new Date();

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("messageId", messageId);
        messageData.put("text", messageText);
        messageData.put("senderId", currentUser.getUid());
        messageData.put("senderName", currentUser.getEmail());
        messageData.put("timestamp", timestamp);
        messageData.put("isSystemMessage", false);

        // Add message to group's messages collection
        db.collection("groups").document(groupId)
                .collection("messages").document(messageId)
                .set(messageData)
                .addOnSuccessListener(aVoid -> {
                    // Update group's last message info
                    Map<String, Object> groupUpdate = new HashMap<>();
                    groupUpdate.put("lastMessage", messageText);
                    groupUpdate.put("lastSender", currentUser.getEmail());
                    groupUpdate.put("lastMessageTime", timestamp);

                    db.collection("groups").document(groupId)
                            .update(groupUpdate)
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error updating group's last message", e);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error sending message", e);
                    Toast.makeText(GroupChatActivity.this, "Failed to send message", Toast.LENGTH_SHORT).show();
                });
    }

    private void showProgressBar(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
