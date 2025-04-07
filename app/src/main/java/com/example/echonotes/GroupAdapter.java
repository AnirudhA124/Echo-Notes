package com.example.echonotes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GroupAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_MY_GROUP = 1;
    private static final int VIEW_TYPE_SEARCH_RESULT = 2;

    private List<Group> groupList;
    private OnGroupClickListener listener;
    private OnGroupJoinListener joinListener;
    private SimpleDateFormat timeFormat;
    private SimpleDateFormat dateFormat;
    private Calendar calendar;
    private String currentUserId;
    private boolean isSearchMode = false;

    public GroupAdapter(List<Group> groupList, OnGroupClickListener listener, OnGroupJoinListener joinListener) {
        this.groupList = groupList;
        this.listener = listener;
        this.joinListener = joinListener;
        this.timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        this.dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
        this.calendar = Calendar.getInstance();
    }

    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    public void setSearchMode(boolean searchMode) {
        this.isSearchMode = searchMode;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SEARCH_RESULT) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group_search, parent, false);
            return new SearchResultViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
            return new GroupViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Group group = groupList.get(position);

        if (holder instanceof GroupViewHolder) {
            ((GroupViewHolder) holder).bind(group);
        } else if (holder instanceof SearchResultViewHolder) {
            ((SearchResultViewHolder) holder).bind(group);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (isSearchMode) {
            return VIEW_TYPE_SEARCH_RESULT;
        } else {
            return VIEW_TYPE_MY_GROUP;
        }
    }

    @Override
    public int getItemCount() {
        return groupList.size();
    }

    public interface OnGroupClickListener {
        void onGroupClick(Group group);
    }

    public interface OnGroupJoinListener {
        void onGroupJoin(Group group);
    }

    class GroupViewHolder extends RecyclerView.ViewHolder {
        ImageView ivGroupIcon;
        TextView tvGroupName;
        TextView tvLastMessage;
        TextView tvTimestamp;
        TextView tvUnreadCount;

        public GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            ivGroupIcon = itemView.findViewById(R.id.ivGroupIcon);
            tvGroupName = itemView.findViewById(R.id.tvGroupName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvUnreadCount = itemView.findViewById(R.id.tvUnreadCount);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onGroupClick(groupList.get(position));
                }
            });
        }

        public void bind(Group group) {
            tvGroupName.setText(group.getGroupName());

            // Display last message
            if (group.getLastSender() != null && group.getLastMessage() != null) {
                String senderName = extractNameFromEmail(group.getLastSender());
                tvLastMessage.setText(String.format("%s: %s", senderName, group.getLastMessage()));
            } else {
                tvLastMessage.setText("No messages yet");
            }

            // Format timestamp
            if (group.getLastMessageTime() != null) {
                tvTimestamp.setText(formatTimestamp(group.getLastMessageTime()));
            } else {
                tvTimestamp.setText("");
            }

            // Show unread count if any
            if (group.getUnreadCount() > 0) {
                tvUnreadCount.setVisibility(View.VISIBLE);
                tvUnreadCount.setText(String.valueOf(group.getUnreadCount()));
            } else {
                tvUnreadCount.setVisibility(View.GONE);
            }
        }
    }

    class SearchResultViewHolder extends RecyclerView.ViewHolder {
        TextView tvGroupName;
        TextView tvGroupDescription;
        Button btnJoin;
        Button btnView;

        public SearchResultViewHolder(@NonNull View itemView) {
            super(itemView);
            tvGroupName = itemView.findViewById(R.id.tvGroupName);
            tvGroupDescription = itemView.findViewById(R.id.tvGroupDescription);
            btnJoin = itemView.findViewById(R.id.btnJoin);
            btnView = itemView.findViewById(R.id.btnView);

            btnJoin.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && joinListener != null) {
                    joinListener.onGroupJoin(groupList.get(position));
                }
            });

            btnView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onGroupClick(groupList.get(position));
                }
            });
        }

        public void bind(Group group) {
            tvGroupName.setText(group.getGroupName());

            if (group.getDescription() != null && !group.getDescription().isEmpty()) {
                tvGroupDescription.setText(group.getDescription());
                tvGroupDescription.setVisibility(View.VISIBLE);
            } else {
                tvGroupDescription.setVisibility(View.GONE);
            }
        }
    }

    private String extractNameFromEmail(String email) {
        if (email == null) return "";
        int atIndex = email.indexOf("@");
        if (atIndex > 0) {
            return email.substring(0, atIndex);
        }
        return email;
    }

    private String formatTimestamp(Date date) {
        if (date == null) return "";

        Calendar now = Calendar.getInstance();
        Calendar messageTime = Calendar.getInstance();
        messageTime.setTime(date);

        if (now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR)) {
            // Today, show time
            return timeFormat.format(date);
        } else if (now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR) + 1) {
            // Yesterday
            return "Yesterday";
        } else if (now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) < messageTime.get(Calendar.DAY_OF_YEAR) + 7) {
            // This week, show day name
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);

            if (messageTime.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) &&
                    messageTime.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR)) {
                return "Yesterday";
            }

            SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.getDefault());
            return dayFormat.format(date);
        } else {
            // Older, show date
            return dateFormat.format(date);
        }
    }
}
