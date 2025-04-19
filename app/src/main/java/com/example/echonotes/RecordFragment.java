package com.example.echonotes;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
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
import android.widget.ProgressBar;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecordFragment extends Fragment {

    private LinearLayout summaryContainer;
    private LinearLayout transcriptContainer;
    private Button recordButton;
    private TextView timerTextView;
    private boolean isRecording = false;

    private MediaRecorder mediaRecorder;
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

    private static final String SERVER_URL = "https://transcript-api-bouw.onrender.com/upload";
    private static final String TRANSCRIPT_URL = "https://transcript-api-bouw.onrender.com/transcript/";
    private static final int POLL_INTERVAL = 5000;
    private Handler pollHandler = new Handler(Looper.getMainLooper());t

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_record, container, false);
        summaryContainer =view.findViewById(R.id.summary_container);
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

            uploadRecording();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Recording error", Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadRecording() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                if (audioFile == null || !audioFile.exists()) {
                    Log.e("UPLOAD", "Audio file doesn't exist");
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Audio file not found", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                String boundary = "----" + System.currentTimeMillis();

                URL url = new URL(SERVER_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                connection.setRequestProperty("Connection", "Keep-Alive");
                connection.setUseCaches(false);

                java.io.DataOutputStream outputStream = new java.io.DataOutputStream(connection.getOutputStream());

                outputStream.writeBytes("--" + boundary + "\r\n");
                outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" +
                        audioFile.getName() + "\"\r\n");
                outputStream.writeBytes("Content-Type: audio/3gpp\r\n\r\n");

                FileInputStream fileInputStream = new FileInputStream(audioFile);
                int bytesAvailable = fileInputStream.available();
                int maxBufferSize = 1024 * 1024;
                int bufferSize = Math.min(bytesAvailable, maxBufferSize);
                byte[] buffer = new byte[bufferSize];

                int bytesRead = fileInputStream.read(buffer, 0, bufferSize);
                while (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead);
                    bytesAvailable = fileInputStream.available();
                    bufferSize = Math.min(bytesAvailable, maxBufferSize);
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);
                }

                outputStream.writeBytes("\r\n--" + boundary + "\r\n");
                outputStream.writeBytes("Content-Disposition: form-data; name=\"metadata\"\r\n\r\n");
                outputStream.writeBytes("{\"timestamp\":\"" + System.currentTimeMillis() + "\"}\r\n");
                outputStream.writeBytes("--" + boundary + "--\r\n");

                fileInputStream.close();
                outputStream.flush();
                outputStream.close();

                int responseCode = connection.getResponseCode();
                Log.d("UPLOAD", "Server responded with: " + responseCode);

                if (responseCode == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    try {
                        JSONObject jsonResponse = new JSONObject(response.toString());
                        clientId = jsonResponse.getString("client_id");
                        Log.d("UPLOAD", "Client ID: " + clientId);

                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Audio uploaded successfully", Toast.LENGTH_SHORT).show();
                        });

                        startPollingForTranscript();
                        startPollingForSummary();

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
    private void startPollingForSummary() {
        if (clientId == null || clientId.isEmpty()) {
            Log.e("SUMMARY", "No client ID available");
            return;
        }

        requireActivity().runOnUiThread(() -> {
            summaryContainer.removeAllViews();

            ProgressBar progressBar = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleLarge);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 50, 0, 0);
            params.gravity = android.view.Gravity.CENTER;
            progressBar.setLayoutParams(params);
            progressBar.setIndeterminate(true);
            progressBar.setIndeterminateTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.black))
            );
            summaryContainer.addView(progressBar);
        });

        pollForTranscript(); // polling transcript gives both transcript & summary
    }

    private void startPollingForTranscript() {
        if (clientId == null || clientId.isEmpty()) {
            Log.e("TRANSCRIPT", "No client ID available");
            return;
        }

        requireActivity().runOnUiThread(() -> {
            transcriptContainer.removeAllViews();

            ProgressBar progressBar = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleLarge);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 50, 0, 0);
            params.gravity = android.view.Gravity.CENTER;
            progressBar.setLayoutParams(params);
            progressBar.setIndeterminate(true);
            progressBar.setIndeterminateTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.black))
            );
            transcriptContainer.addView(progressBar);
        });

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
                        JSONArray transcript = jsonResponse.getJSONArray("transcript");
                        String summary = jsonResponse.getString("summary");

                        requireActivity().runOnUiThread(() -> {
                            displayTranscript(transcript);
                            displaySummary(summary);
                        });
                        return;
                    }
                }

                pollHandler.postDelayed(this::pollForTranscript, POLL_INTERVAL);

            } catch (Exception e) {
                Log.e("TRANSCRIPT", "Error polling for transcript", e);
                pollHandler.postDelayed(this::pollForTranscript, POLL_INTERVAL);
            }
        }).start();
    }


    private void displayTranscript(JSONArray transcript) {
        requireActivity().runOnUiThread(() -> {
            try {
                transcriptContainer.removeAllViews();

                StringBuilder fullTranscript = new StringBuilder();

                for (int i = 0; i < transcript.length(); i++) {
                    JSONObject utterance = transcript.getJSONObject(i);
                    String speaker = utterance.getString("speaker");
                    String text = utterance.getString("text");

                    fullTranscript.append(speaker).append(": ").append(text).append("\n\n");

                    CardView cardView = new CardView(requireContext());
                    CardView.LayoutParams cardParams = new CardView.LayoutParams(
                            CardView.LayoutParams.MATCH_PARENT,
                            CardView.LayoutParams.WRAP_CONTENT
                    );
                    cardParams.setMargins(0, 0, 0, 16);
                    cardView.setLayoutParams(cardParams);
                    cardView.setRadius(12);
                    cardView.setCardElevation(4);

                    int backgroundColor = speaker.endsWith("A") ?
                            ContextCompat.getColor(requireContext(), android.R.color.darker_gray) :
                            ContextCompat.getColor(requireContext(), android.R.color.white);
                    cardView.setCardBackgroundColor(backgroundColor);

                    LinearLayout contentLayout = new LinearLayout(requireContext());
                    contentLayout.setOrientation(LinearLayout.VERTICAL);
                    contentLayout.setPadding(16, 16, 16, 16);

                    TextView speakerText = new TextView(requireContext());
                    speakerText.setText(speaker);
                    speakerText.setTextSize(14);
                    speakerText.setTypeface(null, android.graphics.Typeface.BOLD);
                    speakerText.setTextColor(getResources().getColor(android.R.color.black));
                    contentLayout.addView(speakerText);

                    TextView contentText = new TextView(requireContext());
                    contentText.setText(text);
                    contentText.setTextSize(16);
                    contentText.setTextColor(getResources().getColor(android.R.color.black));
                    contentText.setPadding(0, 8, 0, 0);
                    contentLayout.addView(contentText);

                    cardView.addView(contentLayout);
                    transcriptContainer.addView(cardView);
                }

                Button downloadButton = new Button(requireContext());
                downloadButton.setText("Download Transcript");
                downloadButton.setOnClickListener(v -> {
                    try {
                        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        if (!downloadsDir.exists()) downloadsDir.mkdirs();

                        File transcriptFile = new File(downloadsDir, "transcript.txt");
                        FileOutputStream fos = new FileOutputStream(transcriptFile);
                        fos.write(fullTranscript.toString().getBytes());
                        fos.close();

                        Log.d("TRANSCRIPT", "Transcript saved to: " + transcriptFile.getAbsolutePath());
                        Toast.makeText(getContext(), "Transcript saved to: " + transcriptFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    } catch (IOException e) {
                        Toast.makeText(getContext(), "Failed to save transcript", Toast.LENGTH_SHORT).show();
                        Log.e("TRANSCRIPT", "Error saving file", e);
                    }
                });

                transcriptContainer.addView(downloadButton);

                Toast.makeText(getContext(), "Transcript ready!", Toast.LENGTH_SHORT).show();

            } catch (JSONException e) {
                Log.e("TRANSCRIPT", "Error displaying transcript", e);
                Toast.makeText(getContext(), "Error displaying transcript", Toast.LENGTH_SHORT).show();
            }
        });
    }




    private void displaySummary(String summary) {
        requireActivity().runOnUiThread(() -> {
            try {
                summaryContainer.removeAllViews();

                // Show spinner while loading
                ProgressBar spinner = new ProgressBar(requireContext());
                spinner.setIndeterminate(true);
                summaryContainer.addView(spinner);

                // Simulate slight delay for UX (optional)
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    summaryContainer.removeAllViews(); // remove spinner

                    // Create CardView for summary
                    CardView cardView = new CardView(requireContext());
                    CardView.LayoutParams cardParams = new CardView.LayoutParams(
                            CardView.LayoutParams.MATCH_PARENT,
                            CardView.LayoutParams.WRAP_CONTENT
                    );
                    cardParams.setMargins(0, 0, 0, 16);
                    cardView.setLayoutParams(cardParams);
                    cardView.setRadius(12);
                    cardView.setCardElevation(4);
                    cardView.setCardBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray));

                    // Content inside CardView
                    LinearLayout contentLayout = new LinearLayout(requireContext());
                    contentLayout.setOrientation(LinearLayout.VERTICAL);
                    contentLayout.setPadding(16, 16, 16, 16);

                    TextView titleText = new TextView(requireContext());
                    titleText.setText("Summary");
                    titleText.setTextSize(16);
                    titleText.setTypeface(null, android.graphics.Typeface.BOLD);
                    titleText.setTextColor(getResources().getColor(android.R.color.white));
                    contentLayout.addView(titleText);

                    TextView summaryText = new TextView(requireContext());
                    summaryText.setText(summary);
                    summaryText.setTextSize(16);
                    summaryText.setTextColor(getResources().getColor(android.R.color.white));
                    summaryText.setPadding(0, 8, 0, 0);
                    contentLayout.addView(summaryText);

                    cardView.addView(contentLayout);
                    summaryContainer.addView(cardView);

                    // Download button
                    Button downloadButton = new Button(requireContext());
                    downloadButton.setText("Download Summary");
                    downloadButton.setOnClickListener(v -> {
                        try {
                            File internalDir = requireContext().getExternalFilesDir(null); // /Android/data/your.package.name/files
                            if (internalDir == null) throw new IOException("Internal directory not accessible");

                            File summaryFile = new File(internalDir, "summary.txt");
                            FileOutputStream fos = new FileOutputStream(summaryFile);
                            fos.write(summary.getBytes());
                            fos.close();

                            Log.d("SUMMARY", "Summary saved to: " + summaryFile.getAbsolutePath());
                            Toast.makeText(getContext(), "Summary saved to: " + summaryFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                        } catch (IOException e) {
                            Toast.makeText(getContext(), "Failed to save summary", Toast.LENGTH_SHORT).show();
                            Log.e("SUMMARY", "Error saving summary", e);
                        }
                    });


                    summaryContainer.addView(downloadButton);
                    Toast.makeText(getContext(), "Summary ready!", Toast.LENGTH_SHORT).show();

                }, 500); // Delay of 500ms for visual effect (optional)

            } catch (Exception e) {
                Log.e("SUMMARY", "Error displaying summary", e);
                Toast.makeText(getContext(), "Error displaying summary", Toast.LENGTH_SHORT).show();
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

        pollHandler.removeCallbacksAndMessages(null);
    }
}
