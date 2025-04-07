package com.example.echonotes;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_MY_MESSAGE = 1;
    private static final int VIEW_TYPE_OTHER_MESSAGE = 2;
    private static final int VIEW_TYPE_SYSTEM_MESSAGE = 3;
    private static final String TAG = "MessageAdapter";

    private Context context;
    private List<Message> messageList;
    private String currentUserId;
    private SimpleDateFormat timeFormat;
    private FirebaseFirestore db;
    private Map<String, String> userNameCache = new HashMap<>();

    public MessageAdapter(Context context, List<Message> messageList, String currentUserId) {
        this.context = context;
        this.messageList = messageList;
        this.currentUserId = currentUserId;
        this.timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        this.db = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_MY_MESSAGE) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_message_sent, parent, false);
            return new MySentMessageHolder(view);
        } else if (viewType == VIEW_TYPE_SYSTEM_MESSAGE) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_message_system, parent, false);
            return new SystemMessageHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_message_received, parent, false);
            return new ReceivedMessageHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messageList.get(position);

        if (holder instanceof MySentMessageHolder) {
            ((MySentMessageHolder) holder).bind(message);
        } else if (holder instanceof ReceivedMessageHolder) {
            ((ReceivedMessageHolder) holder).bind(message);
        } else if (holder instanceof SystemMessageHolder) {
            ((SystemMessageHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messageList.get(position);

        if (message.isSystemMessage()) {
            return VIEW_TYPE_SYSTEM_MESSAGE;
        } else if (message.getSenderId().equals(currentUserId)) {
            return VIEW_TYPE_MY_MESSAGE;
        } else {
            return VIEW_TYPE_OTHER_MESSAGE;
        }
    }

    private String formatTime(Date date) {
        return date != null ? timeFormat.format(date) : "";
    }

    private void fetchUserName(String email, TextView nameText) {
        // Check if name is already in cache
        if (userNameCache.containsKey(email)) {
            nameText.setText(userNameCache.get(email));
            return;
        }

        // If it's a system message
        if (email.equals("System")) {
            nameText.setText("System");
            return;
        }

        // Query Firestore for user data based on email
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            // Get the name from the user document
                            String name = document.getString("name");
                            if (name != null && !name.isEmpty()) {
                                // Cache the result
                                userNameCache.put(email, name);
                                nameText.setText(name);
                                return;
                            }
                        }
                    }

                    // If name not found, just use the email as is
                    nameText.setText(email);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching user data", e);
                    nameText.setText(email);
                });
    }

    class MySentMessageHolder extends RecyclerView.ViewHolder {
        TextView messageText, timeText;

        MySentMessageHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.text_message_body);
            timeText = itemView.findViewById(R.id.text_message_time);
        }

        void bind(Message message) {
            messageText.setText(message.getText());
            timeText.setText(formatTime(message.getTimestamp()));
        }
    }

    class ReceivedMessageHolder extends RecyclerView.ViewHolder {
        TextView messageText, timeText, nameText;

        ReceivedMessageHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.text_message_body);
            timeText = itemView.findViewById(R.id.text_message_time);
            nameText = itemView.findViewById(R.id.text_message_name);
        }

        void bind(Message message) {
            messageText.setText(message.getText());
            timeText.setText(formatTime(message.getTimestamp()));
            fetchUserName(message.getSenderName(), nameText);
        }
    }

    class SystemMessageHolder extends RecyclerView.ViewHolder {
        TextView messageText;

        SystemMessageHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.text_message_system);
        }

        void bind(Message message) {
            messageText.setText(message.getText());
        }
    }
}
