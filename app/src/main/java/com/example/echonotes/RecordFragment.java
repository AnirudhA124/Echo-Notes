package com.example.echonotes;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecordFragment extends Fragment {

    private LinearLayout transcriptContainer;
    private Button recordButton;
    private TextView timerTextView;
    private boolean isRecording = false;

    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private File audioFile;
    private String clientId;

    private Handler timerHandler = new Handler();
    private int seconds = 0;

    private Runnable timerRunnable = () -> {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        timerTextView.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, secs));
        seconds++;
        timerHandler.postDelayed(this.timerRunnable, 1000);
    };

    // Server configuration - update with your actual server IP
    private static final String SERVER_URL = "http://192.168.46.197:5000/upload";
    private static final String TRANSCRIPT_URL = "http://192.168.46.197:5000/transcript/";
    private static final int POLL_INTERVAL = 5000; // 5 seconds
    private Handler pollHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_record, container, false);

        transcriptContainer = view.findViewById(R.id.transcript_container);
        recordButton = view.findViewById(R.id.start_recording);
        timerTextView = view.findViewById(R.id.timer_view);

        recordButton.setOnClickListener(v -> {
            if (!isRecording) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, 200);
                } else {
                    startRecording();
                }
            } else {
                stopRecording();
            }
        });

        return view;
    }

    private void startRecording() {
        isRecording = true;
        recordButton.setText("Stop Recording");
        seconds = 0;
        timerHandler.post(timerRunnable);

        File outputDir = new File(requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC), "EchoNotes");
        if (!outputDir.exists()) outputDir.mkdirs();

        String fileName = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        audioFile = new File(outputDir, "audio_" + fileName + ".3gp");

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setOutputFile(audioFile.getAbsolutePath());
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setAudioEncodingBitRate(96000);
        mediaRecorder.setAudioSamplingRate(44100);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            Log.d("DEBUG", "Recording started");
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Recording failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        isRecording = false;
        recordButton.setText("Start Recording");
        timerHandler.removeCallbacks(timerRunnable);

        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;

            Toast.makeText(getContext(), "Recording finished!", Toast.LENGTH_SHORT).show();

            playRecording();  // Play audio
            uploadRecording();  // Upload to server

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Recording error", Toast.LENGTH_SHORT).show();
        }
    }

    private void playRecording() {
        if (audioFile != null && audioFile.exists()) {
            mediaPlayer = new MediaPlayer();
            try {
                mediaPlayer.setDataSource(audioFile.getAbsolutePath());
                mediaPlayer.prepare();
                mediaPlayer.start();
                Toast.makeText(getContext(), "Playing recorded audio", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(getContext(), "Playback failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void uploadRecording() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                // Get the audio file data
                if (audioFile == null || !audioFile.exists()) {
                    Log.e("UPLOAD", "Audio file doesn't exist");
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Audio file not found", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                String boundary = "----" + System.currentTimeMillis();

                // Setup connection
                URL url = new URL(SERVER_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                connection.setRequestProperty("Connection", "Keep-Alive");
                connection.setUseCaches(false);

                // Create output stream
                java.io.DataOutputStream outputStream = new java.io.DataOutputStream(connection.getOutputStream());

                // Start multipart request
                outputStream.writeBytes("--" + boundary + "\r\n");
                outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" +
                        audioFile.getName() + "\"\r\n");
                outputStream.writeBytes("Content-Type: audio/3gpp\r\n\r\n");

                // Read file data
                FileInputStream fileInputStream = new FileInputStream(audioFile);
                int bytesAvailable = fileInputStream.available();
                int maxBufferSize = 1024 * 1024;
                int bufferSize = Math.min(bytesAvailable, maxBufferSize);
                byte[] buffer = new byte[bufferSize];

                // Read file
                int bytesRead = fileInputStream.read(buffer, 0, bufferSize);
                while (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead);
                    bytesAvailable = fileInputStream.available();
                    bufferSize = Math.min(bytesAvailable, maxBufferSize);
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);
                }

                // Send file metadata
                outputStream.writeBytes("\r\n--" + boundary + "\r\n");
                outputStream.writeBytes("Content-Disposition: form-data; name=\"metadata\"\r\n\r\n");
                outputStream.writeBytes("{\"timestamp\":\"" + System.currentTimeMillis() + "\"}\r\n");
                outputStream.writeBytes("--" + boundary + "--\r\n");

                // Close streams
                fileInputStream.close();
                outputStream.flush();
                outputStream.close();

                // Get response
                int responseCode = connection.getResponseCode();
                Log.d("UPLOAD", "Server responded with: " + responseCode);

                if (responseCode == 200) {
                    // Read response
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    // Parse response to get client ID
                    try {
                        JSONObject jsonResponse = new JSONObject(response.toString());
                        clientId = jsonResponse.getString("client_id");
                        Log.d("UPLOAD", "Client ID: " + clientId);

                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Audio uploaded successfully", Toast.LENGTH_SHORT).show();
                        });

                        // Start polling for transcript
                        startPollingForTranscript();

                    } catch (JSONException e) {
                        Log.e("UPLOAD", "Failed to parse response", e);
                    }
                } else {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Upload failed: " + responseCode, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                Log.e("UPLOAD", "Failed to upload audio file", e);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Upload error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private void startPollingForTranscript() {
        if (clientId == null || clientId.isEmpty()) {
            Log.e("TRANSCRIPT", "No client ID available");
            return;
        }

        // Show loading message in UI
        requireActivity().runOnUiThread(() -> {
            // Clear previous transcripts
            transcriptContainer.removeAllViews();

            // Add loading indicator
            TextView loadingText = new TextView(getContext());
            loadingText.setText("Processing transcript...");
            loadingText.setPadding(16, 16, 16, 16);
            loadingText.setTextColor(getResources().getColor(android.R.color.black));
            loadingText.setTextSize(16);
            transcriptContainer.addView(loadingText);
        });

        // Start polling
        pollForTranscript();
    }

    private void pollForTranscript() {
        new Thread(() -> {
            try {
                URL url = new URL(TRANSCRIPT_URL + clientId);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String inputLine;

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String status = jsonResponse.getString("status");

                    if ("completed".equals(status)) {
                        // Process and display transcript
                        JSONArray transcript = jsonResponse.getJSONArray("transcript");
                        displayTranscript(transcript);
                        return; // Done polling
                    }
                }

                // Schedule next poll
                pollHandler.postDelayed(this::pollForTranscript, POLL_INTERVAL);

            } catch (Exception e) {
                Log.e("TRANSCRIPT", "Error polling for transcript", e);
                // Schedule retry
                pollHandler.postDelayed(this::pollForTranscript, POLL_INTERVAL);
            }
        }).start();
    }

    private void displayTranscript(JSONArray transcript) {
        requireActivity().runOnUiThread(() -> {
            try {
                // Clear loading message
                transcriptContainer.removeAllViews();

                // Add each utterance
                for (int i = 0; i < transcript.length(); i++) {
                    JSONObject utterance = transcript.getJSONObject(i);
                    String speaker = utterance.getString("speaker");
                    String text = utterance.getString("text");

                    // Create a CardView for each utterance
                    CardView cardView = new CardView(requireContext());
                    CardView.LayoutParams cardParams = new CardView.LayoutParams(
                            CardView.LayoutParams.MATCH_PARENT,
                            CardView.LayoutParams.WRAP_CONTENT
                    );
                    cardParams.setMargins(0, 0, 0, 16); // bottom margin
                    cardView.setLayoutParams(cardParams);
                    cardView.setRadius(12);
                    cardView.setCardElevation(4);

                    // Determine background color based on speaker
                    // Determine background color based on speaker
                    int backgroundColor;
                    if (speaker.endsWith("A")) {  // Speaker A/1
                        backgroundColor = ContextCompat.getColor(requireContext(), android.R.color.holo_blue_light);
                    } else {  // Speaker B/2
                        backgroundColor = ContextCompat.getColor(requireContext(), android.R.color.holo_green_light);
                    }
                    cardView.setCardBackgroundColor(backgroundColor);


                    // Create content layout for the card
                    LinearLayout contentLayout = new LinearLayout(requireContext());
                    contentLayout.setOrientation(LinearLayout.VERTICAL);
                    contentLayout.setPadding(16, 16, 16, 16);

                    // Create speaker label
                    TextView speakerText = new TextView(requireContext());
                    speakerText.setText(speaker);
                    speakerText.setTextSize(14);
                    speakerText.setTypeface(null, android.graphics.Typeface.BOLD);
                    speakerText.setTextColor(getResources().getColor(android.R.color.black));
                    contentLayout.addView(speakerText);

                    // Create text content
                    TextView contentText = new TextView(requireContext());
                    contentText.setText(text);
                    contentText.setTextSize(16);
                    contentText.setTextColor(getResources().getColor(android.R.color.black));
                    contentText.setPadding(0, 8, 0, 0);
                    contentLayout.addView(contentText);

                    // Add the content layout to the card
                    cardView.addView(contentLayout);

                    // Add the card to the transcript container
                    transcriptContainer.addView(cardView);
                }

                Toast.makeText(getContext(), "Transcript ready!", Toast.LENGTH_SHORT).show();

            } catch (JSONException e) {
                Log.e("TRANSCRIPT", "Error displaying transcript", e);
                Toast.makeText(getContext(), "Error displaying transcript", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 200 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            Toast.makeText(getContext(), "Permission denied.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (Exception ignored) {}
            mediaRecorder.release();
            mediaRecorder = null;
        }

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        // Cancel polling if active
        pollHandler.removeCallbacksAndMessages(null);
    }
}