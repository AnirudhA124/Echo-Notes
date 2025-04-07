package com.example.echonotes;

import java.io.Serializable;
import java.util.Date;

public class Group implements Serializable {
    private String groupId;
    private String groupName;
    private String description;
    private String createdBy;
    private Date createdAt;
    private String lastMessage;
    private String lastSender;
    private Date lastMessageTime;
    private int unreadCount;

    // Required empty constructor for Firestore
    public Group() {
    }

    public Group(String groupId, String groupName, String description, String createdBy, Date createdAt) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.description = description;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.unreadCount = 0;
    }

    // Getters and setters
    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getLastSender() {
        return lastSender;
    }

    public void setLastSender(String lastSender) {
        this.lastSender = lastSender;
    }

    public Date getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(Date lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Group group = (Group) obj;
        return groupId != null && groupId.equals(group.groupId);
    }

    @Override
    public int hashCode() {
        return groupId != null ? groupId.hashCode() : 0;
    }
}