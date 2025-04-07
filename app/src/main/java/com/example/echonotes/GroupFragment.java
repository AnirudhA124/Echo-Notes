package com.example.echonotes;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupFragment extends Fragment implements GroupAdapter.OnGroupClickListener, GroupAdapter.OnGroupJoinListener {

    private static final String TAG = "GroupFragment";
    private RecyclerView recyclerGroups;
    private FloatingActionButton fabAddGroup;
    private View progressBar;
    private EditText etSearchGroups;
    private ImageButton btnSearch;

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private GroupAdapter groupAdapter;
    private List<Group> groupList;
    private List<Group> allGroupsList;
    private boolean isSearchMode = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_group, container, false);

        initializeViews(view);
        initializeFirebase();
        setupRecyclerView();

        // Load groups after setup is complete
        loadGroups();
        setupListeners();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload groups every time fragment becomes visible
        if (groupList != null && !isSearchMode) {
            groupList.clear();
            groupAdapter.notifyDataSetChanged();
            loadGroups();
        }
    }

    private void initializeViews(View view) {
        recyclerGroups = view.findViewById(R.id.recyclerGroups);
        fabAddGroup = view.findViewById(R.id.fabAddGroup);
        progressBar = view.findViewById(R.id.progressBar);
        etSearchGroups = view.findViewById(R.id.etSearchGroups);
        btnSearch = view.findViewById(R.id.btnSearch);
    }

    private void initializeFirebase() {
        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
    }

    private void setupRecyclerView() {
        groupList = new ArrayList<>();
        allGroupsList = new ArrayList<>();
        groupAdapter = new GroupAdapter(groupList, this, this);
        recyclerGroups.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerGroups.setAdapter(groupAdapter);
    }

    private void setupListeners() {
        fabAddGroup.setOnClickListener(v -> showCreateGroupDialog());

        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> searchGroups());
        }

        if (etSearchGroups != null) {
            etSearchGroups.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() > 0) {
                        searchGroups();
                    } else {
                        // If search field is cleared, show user's groups
                        isSearchMode = false;
                        loadGroups();
                    }
                }
            });
        }
    }

    private void searchGroups() {
        String query = etSearchGroups.getText().toString().trim().toLowerCase();

        if (TextUtils.isEmpty(query)) {
            // If search is empty, show user's groups
            isSearchMode = false;
            loadGroups();
            return;
        }

        isSearchMode = true;
        showProgressBar(true);

        db.collection("groups")
                .orderBy("groupName")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Group> searchResults = new ArrayList<>();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Group group = document.toObject(Group.class);
                        String groupName = group.getGroupName().toLowerCase();
                        String description = group.getDescription() != null ?
                                group.getDescription().toLowerCase() : "";

                        // Add to results if name or description contains the query
                        if (groupName.contains(query) || description.contains(query)) {
                            searchResults.add(group);
                        }
                    }

                    // Update the displayed list
                    groupList.clear();
                    groupList.addAll(searchResults);
                    groupAdapter.notifyDataSetChanged();

                    showProgressBar(false);

                    if (searchResults.isEmpty()) {
                        Toast.makeText(getContext(), "No groups found matching your search",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    showProgressBar(false);
                    Log.e(TAG, "Error searching groups", e);
                    Toast.makeText(getContext(), "Error searching: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void showCreateGroupDialog() {
        final Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_create_group);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        final EditText etGroupName = dialog.findViewById(R.id.etGroupName);
        final EditText etGroupDescription = dialog.findViewById(R.id.etGroupDescription);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);
        Button btnCreate = dialog.findViewById(R.id.btnCreate);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnCreate.setOnClickListener(v -> {
            String groupName = etGroupName.getText().toString().trim();
            String groupDescription = etGroupDescription.getText().toString().trim();

            if (TextUtils.isEmpty(groupName)) {
                etGroupName.setError("Group name is required");
                return;
            }

            createNewGroup(groupName, groupDescription);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void createNewGroup(String groupName, String groupDescription) {
        // Existing code...
        if (currentUser == null) {
            Toast.makeText(getContext(), "You must be logged in to create groups", Toast.LENGTH_SHORT).show();
            return;
        }

        showProgressBar(true);

        String groupId = db.collection("groups").document().getId();
        Date currentDate = new Date();

        Map<String, Object> groupData = new HashMap<>();
        groupData.put("groupId", groupId);
        groupData.put("groupName", groupName);
        groupData.put("description", groupDescription);
        groupData.put("createdBy", currentUser.getUid());
        groupData.put("createdAt", currentDate);
        groupData.put("lastMessageTime", currentDate);
        groupData.put("lastMessage", "Group created");
        groupData.put("lastSender", currentUser.getEmail());

        // Create the group document
        db.collection("groups").document(groupId)
                .set(groupData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Group document created with ID: " + groupId);

                    // Add current user as a member
                    Map<String, Object> memberData = new HashMap<>();
                    memberData.put("userId", currentUser.getUid());
                    memberData.put("email", currentUser.getEmail());
                    memberData.put("joinedAt", currentDate);
                    memberData.put("role", "admin"); // First user is admin

                    db.collection("groups").document(groupId)
                            .collection("members").document(currentUser.getUid())
                            .set(memberData)
                            .addOnSuccessListener(aVoid1 -> {
                                Log.d(TAG, "User added as member to group: " + groupId);

                                // Add group to user's groups
                                Map<String, Object> userGroupData = new HashMap<>();
                                userGroupData.put("groupId", groupId);
                                userGroupData.put("joinedAt", currentDate);

                                db.collection("users").document(currentUser.getUid())
                                        .collection("groups").document(groupId)
                                        .set(userGroupData)
                                        .addOnSuccessListener(aVoid2 -> {
                                            showProgressBar(false);
                                            Toast.makeText(getContext(), "Group created successfully", Toast.LENGTH_SHORT).show();

                                            // Create welcome message
                                            sendWelcomeMessage(groupId);

                                            // IMPORTANT: Add the new group to the list immediately
                                            Group newGroup = new Group(groupId, groupName, groupDescription, currentUser.getUid(), currentDate);
                                            newGroup.setLastMessage("Group created");
                                            newGroup.setLastSender(currentUser.getEmail());
                                            newGroup.setLastMessageTime(currentDate);

                                            groupList.add(0, newGroup); // Add to beginning of list
                                            allGroupsList.add(0, newGroup);
                                            groupAdapter.notifyItemInserted(0);

                                            // Force a full reload of groups to ensure everything is in sync
                                            isSearchMode = false;
                                            loadGroups();
                                        })
                                        .addOnFailureListener(e -> {
                                            showProgressBar(false);
                                            Log.e(TAG, "Error adding group to user's groups", e);
                                            Toast.makeText(getContext(), "Failed to join group: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                showProgressBar(false);
                                Log.e(TAG, "Error adding user as member", e);
                                Toast.makeText(getContext(), "Failed to add member: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    showProgressBar(false);
                    Log.e(TAG, "Error creating group", e);
                    Toast.makeText(getContext(), "Failed to create group: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void sendWelcomeMessage(String groupId) {
        // Existing code...
        if (currentUser == null) return;

        Map<String, Object> messageData = new HashMap<>();
        String messageId = db.collection("messages").document().getId();
        messageData.put("messageId", messageId);
        messageData.put("text", "Welcome to the group!");
        messageData.put("senderId", currentUser.getUid());
        messageData.put("senderName", currentUser.getEmail());
        messageData.put("timestamp", new Date());
        messageData.put("isSystemMessage", true);

        db.collection("groups").document(groupId)
                .collection("messages").document(messageId)
                .set(messageData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Welcome message sent to group: " + groupId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error sending welcome message", e);
                });
    }

    private void loadGroups() {
        // Existing code...
        if (currentUser == null) {
            Toast.makeText(getContext(), "You must be logged in to view groups", Toast.LENGTH_SHORT).show();
            return;
        }

        showProgressBar(true);
        Log.d(TAG, "Loading groups for user: " + currentUser.getUid());

        // Clear existing groups to prevent duplicates
        groupList.clear();
        allGroupsList.clear();
        groupAdapter.notifyDataSetChanged();

        // Get all groups directly, not just through user's collection
        db.collection("groups")
                .orderBy("lastMessageTime", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    showProgressBar(false);

                    if (queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "No groups found in database");
                        Toast.makeText(getContext(), "No groups found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Log.d(TAG, "Found " + queryDocumentSnapshots.size() + " groups in database");

                    // For each group, check if the current user is a member
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        final Group group = document.toObject(Group.class);

                        if (group != null && group.getGroupId() != null) {
                            Log.d(TAG, "Processing group: " + group.getGroupName() + " (ID: " + group.getGroupId() + ")");

                            // Check if current user is a member of this group
                            db.collection("groups").document(group.getGroupId())
                                    .collection("members").document(currentUser.getUid())
                                    .get()
                                    .addOnSuccessListener(memberDoc -> {
                                        if (memberDoc.exists()) {
                                            Log.d(TAG, "User is a member of group: " + group.getGroupName());

                                            // Only add if not already in the list (prevent duplicates)
                                            if (!groupList.contains(group)) {
                                                groupList.add(group);
                                                allGroupsList.add(group);
                                                groupAdapter.notifyDataSetChanged();

                                                // Get unread count for this group
                                                updateUnreadCount(group);
                                            }
                                        } else {
                                            Log.d(TAG, "User is NOT a member of group: " + group.getGroupName());
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Error checking membership for group: " + group.getGroupId(), e);
                                    });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    showProgressBar(false);
                    Log.e(TAG, "Error loading groups", e);
                    Toast.makeText(getContext(), "Failed to load groups: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void updateUnreadCount(Group group) {
        // Existing code...
        if (currentUser == null) return;

        db.collection("groups").document(group.getGroupId())
                .collection("members").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Date lastRead = documentSnapshot.getDate("lastRead");

                        if (lastRead != null) {
                            db.collection("groups").document(group.getGroupId())
                                    .collection("messages")
                                    .whereGreaterThan("timestamp", lastRead)
                                    .get()
                                    .addOnSuccessListener(queryDocumentSnapshots -> {
                                        int unreadCount = queryDocumentSnapshots.size();
                                        int groupIndex = getGroupIndexById(group.getGroupId());

                                        if (groupIndex != -1) {
                                            groupList.get(groupIndex).setUnreadCount(unreadCount);
                                            groupAdapter.notifyItemChanged(groupIndex);
                                        }
                                    });
                        } else {
                            // If lastRead is null, count all messages as unread
                            db.collection("groups").document(group.getGroupId())
                                    .collection("messages")
                                    .get()
                                    .addOnSuccessListener(queryDocumentSnapshots -> {
                                        int unreadCount = queryDocumentSnapshots.size();
                                        int groupIndex = getGroupIndexById(group.getGroupId());

                                        if (groupIndex != -1) {
                                            groupList.get(groupIndex).setUnreadCount(unreadCount);
                                            groupAdapter.notifyItemChanged(groupIndex);
                                        }
                                    });
                        }
                    }
                });
    }

    private int getGroupIndexById(String groupId) {
        for (int i = 0; i < groupList.size(); i++) {
            if (groupList.get(i).getGroupId().equals(groupId)) {
                return i;
            }
        }
        return -1;
    }

    private void showProgressBar(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onGroupClick(Group group) {
        try {
            if (group == null || getActivity() == null) {
                Log.e(TAG, "Group or activity is null");
                return;
            }

            Log.d(TAG, "Opening chat for group: " + group.getGroupName() + " (ID: " + group.getGroupId() + ")");

            // Check if user is a member of this group
            db.collection("groups").document(group.getGroupId())
                    .collection("members").document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            // User is already a member, open the chat
                            openGroupChat(group);
                        } else {
                            // User is not a member, show join dialog
                            showJoinGroupDialog(group);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error checking membership", e);
                        Toast.makeText(getActivity(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error opening group chat", e);
            Log.e(TAG, "Exception details: " + e.getMessage());
            e.printStackTrace();

            if (group != null) {
                Log.e(TAG, "Group details - Name: " + group.getGroupName() +
                        ", ID: " + group.getGroupId() +
                        ", Other properties: " + group.toString());
            }

            Toast.makeText(getActivity(), "Error opening chat: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openGroupChat(Group group) {
        if (group == null || getActivity() == null) {
            Log.e(TAG, "Group or activity is null");
            return;
        }

        Intent intent = new Intent(getActivity(), GroupChatActivity.class);
        intent.putExtra("GROUP_ID", group.getGroupId());
        intent.putExtra("GROUP_NAME", group.getGroupName());
        startActivity(intent);
    }

    private void showJoinGroupDialog(Group group) {
        if (group == null || getActivity() == null) {
            Log.e(TAG, "Group or activity is null");
            return;
        }

        final Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_join_group);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView tvGroupName = dialog.findViewById(R.id.tvGroupName);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);
        Button btnJoin = dialog.findViewById(R.id.btnJoin);

        tvGroupName.setText("Join " + group.getGroupName() + "?");

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnJoin.setOnClickListener(v -> {
            onGroupJoin(group);
            dialog.dismiss();
        });

        dialog.show();
    }

    @Override
    public void onGroupJoin(Group group) {
        if (currentUser == null) {
            Toast.makeText(getContext(), "You must be logged in to join groups", Toast.LENGTH_SHORT).show();
            return;
        }

        showProgressBar(true);

        // Check if user is already a member
        db.collection("groups").document(group.getGroupId())
                .collection("members").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        showProgressBar(false);
                        Toast.makeText(getContext(), "You are already a member of this group",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Add user as a member
                    Map<String, Object> memberData = new HashMap<>();
                    memberData.put("userId", currentUser.getUid());
                    memberData.put("email", currentUser.getEmail());
                    memberData.put("joinedAt", new Date());
                    memberData.put("role", "member");

                    db.collection("groups").document(group.getGroupId())
                            .collection("members").document(currentUser.getUid())
                            .set(memberData)
                            .addOnSuccessListener(aVoid -> {
                                // Add group to user's groups
                                Map<String, Object> userGroupData = new HashMap<>();
                                userGroupData.put("groupId", group.getGroupId());
                                userGroupData.put("joinedAt", new Date());

                                db.collection("users").document(currentUser.getUid())
                                        .collection("groups").document(group.getGroupId())
                                        .set(userGroupData)
                                        .addOnSuccessListener(aVoid1 -> {
                                            showProgressBar(false);
                                            Toast.makeText(getContext(), "Successfully joined group",
                                                    Toast.LENGTH_SHORT).show();

                                            // Send system message about new member
                                            sendSystemMessage(group.getGroupId(),
                                                    currentUser.getEmail() + " joined the group");

                                            // Open the group chat
                                            openGroupChat(group);

                                            // Refresh the groups list
                                            isSearchMode = false;
                                            loadGroups();
                                        })
                                        .addOnFailureListener(e -> {
                                            showProgressBar(false);
                                            Log.e(TAG, "Error adding group to user's groups", e);
                                            Toast.makeText(getContext(), "Failed to join group: " + e.getMessage(),
                                                    Toast.LENGTH_SHORT).show();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                showProgressBar(false);
                                Log.e(TAG, "Error adding user as member", e);
                                Toast.makeText(getContext(), "Failed to join group: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            });
                });
    }

    private void sendSystemMessage(String groupId, String messageText) {
        Map<String, Object> messageData = new HashMap<>();
        String messageId = db.collection("messages").document().getId();
        Date timestamp = new Date();

        messageData.put("messageId", messageId);
        messageData.put("text", messageText);
        messageData.put("senderId", "system");
        messageData.put("senderName", "System");
        messageData.put("timestamp", timestamp);
        messageData.put("isSystemMessage", true);

        db.collection("groups").document(groupId)
                .collection("messages").document(messageId)
                .set(messageData)
                .addOnSuccessListener(aVoid -> {
                    // Update group's last message
                    Map<String, Object> groupUpdate = new HashMap<>();
                    groupUpdate.put("lastMessage", messageText);
                    groupUpdate.put("lastSender", "System");
                    groupUpdate.put("lastMessageTime", timestamp);

                    db.collection("groups").document(groupId)
                            .update(groupUpdate);
                });
    }
}

