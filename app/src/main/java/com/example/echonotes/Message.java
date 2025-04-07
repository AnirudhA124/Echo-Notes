package com.example.echonotes;

import java.util.Date;

public class Message {
    private String messageId;
    private String text;
    private String senderId;
    private String senderName;
    private Date timestamp;
    private boolean isSystemMessage;

    // Required empty constructor for Firestore
    public Message() {
    }

    public Message(String messageId, String text, String senderId, String senderName, Date timestamp, boolean isSystemMessage) {
        this.messageId = messageId;
        this.text = text;
        this.senderId = senderId;
        this.senderName = senderName;
        this.timestamp = timestamp;
        this.isSystemMessage = isSystemMessage;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isSystemMessage() {
        return isSystemMessage;
    }

    public void setSystemMessage(boolean systemMessage) {
        isSystemMessage = systemMessage;
    }
}
