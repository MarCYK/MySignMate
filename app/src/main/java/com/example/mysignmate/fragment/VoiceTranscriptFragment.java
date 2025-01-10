package com.example.mysignmate.fragment;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.mysignmate.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.example.mysignmate.asr.Player;
import com.example.mysignmate.utils.WaveUtil;
import com.example.mysignmate.asr.Recorder;
import com.example.mysignmate.asr.Whisper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class VoiceTranscriptFragment extends Fragment {
    private static final String TAG = "VoiceTranscriptFragment";

    // whisper-tiny.tflite and whisper-base-nooptim.en.tflite works well
    private static final String DEFAULT_MODEL_TO_USE = "Malay.tflite";
    // language model ends with extension ".tflite"
    private static final String CHINESE_MODEL = "Chinese.tflite";
    private static final String ENGLISH_MODEL = "English.tflite";
    private static final String MALAY_MODEL = "Malay.tflite";
    // English only model ends with extension ".en.tflite"
    private static final String ENGLISH_ONLY_MODEL_EXTENSION = ".en.tflite";
    private static final String ENGLISH_ONLY_VOCAB_FILE = "filters_vocab_en.bin";
    private static final String MULTILINGUAL_VOCAB_FILE = "filters_vocab_multilingual.bin";
    private static final String[] EXTENSIONS_TO_COPY = {"tflite", "bin", "wav", "pcm"};

    private TextView tvStatus;
    private TextView tvResult;
    private FloatingActionButton fabCopy;
    private Button btnRecord;
    private Button btnPlay;
    private Button btnTranscribe;

    private Player mPlayer = null;
    private Recorder mRecorder = null;
    private Whisper mWhisper = null;

    private File sdcardDataFolder = null;
    private File selectedWaveFile = null;
    private File selectedTfliteFile = null;

    private long startTime = 0;
    private final boolean loopTesting = false;
    private final SharedResource transcriptionSync = new SharedResource();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_transcript, container, false);

        // Call the method to copy specific file types from assets to data folder
        sdcardDataFolder = requireContext().getExternalFilesDir(null);
        copyAssetsToSdcard(requireContext(), sdcardDataFolder, EXTENSIONS_TO_COPY);

        ArrayList<File> tfliteFiles = getFilesWithExtension(sdcardDataFolder, ".tflite");
        ArrayList<File> waveFiles = getFilesWithExtension(sdcardDataFolder, ".wav");

        // Initialize default model to use
//        selectedTfliteFile = new File(sdcardDataFolder, DEFAULT_MODEL_TO_USE);

        // Ensure "Malay.tflite" is at the top
        File malayModel = new File(sdcardDataFolder, MALAY_MODEL);
        File englishModel = new File(sdcardDataFolder, ENGLISH_MODEL);
        tfliteFiles.remove(malayModel);
        tfliteFiles.remove(englishModel);
        tfliteFiles.add(0, malayModel);
        tfliteFiles.add(1, englishModel);

        // Initialize default model to use
        selectedTfliteFile = malayModel;

        Spinner spinnerTflite = view.findViewById(R.id.spnrTfliteFiles);
        spinnerTflite.setAdapter(getFileArrayAdapter(tfliteFiles));
        spinnerTflite.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                deinitModel();
                selectedTfliteFile = (File) parent.getItemAtPosition(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        Spinner spinnerWave = view.findViewById(R.id.spnrWaveFiles);
        spinnerWave.setAdapter(getFileArrayAdapter(waveFiles));
        spinnerWave.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Cast item to File and get the file name
                selectedWaveFile = (File) parent.getItemAtPosition(position);

                // Check if the selected file is the recording file
                if (selectedWaveFile.getName().equals(WaveUtil.RECORDING_FILE)) {
                    btnRecord.setVisibility(View.VISIBLE);
                } else {
                    btnRecord.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Implementation of record button functionality
        btnRecord = view.findViewById(R.id.btnRecord);
        btnRecord.setOnClickListener(v -> {
            if (mRecorder != null && mRecorder.isInProgress()) {
                Log.d(TAG, "Recording is in progress... stopping...");
                stopRecording();
            } else {
                Log.d(TAG, "Start recording...");
                startRecording();
            }
        });

        // Implementation of Play button functionality
        btnPlay = view.findViewById(R.id.btnPlay);
        btnPlay.setOnClickListener(v -> {
            if (!mPlayer.isPlaying()) {
                mPlayer.initializePlayer(selectedWaveFile.getAbsolutePath());
                mPlayer.startPlayback();
            } else {
                mPlayer.stopPlayback();
            }
        });

        // Implementation of transcribe button functionality
        btnTranscribe = view.findViewById(R.id.btnTranscb);
        btnTranscribe.setOnClickListener(v -> {
            if (mRecorder != null && mRecorder.isInProgress()) {
                Log.d(TAG, "Recording is in progress... stopping...");
                stopRecording();
            }

            if (mWhisper == null)
                initModel(selectedTfliteFile);

            if (!mWhisper.isInProgress()) {
                Log.d(TAG, "Start transcription...");
                startTranscription(selectedWaveFile.getAbsolutePath());

                if (loopTesting) {
                    new Thread(() -> {
                        for (int i = 0; i < 1000; i++) {
                            if (!mWhisper.isInProgress())
                                startTranscription(selectedWaveFile.getAbsolutePath());
                            else
                                Log.d(TAG, "Whisper is already in progress...!");

                            boolean wasNotified = transcriptionSync.waitForSignalWithTimeout(15000);
                            Log.d(TAG, wasNotified ? "Transcription Notified...!" : "Transcription Timeout...!");
                        }
                    }).start();
                }
            } else {
                Log.d(TAG, "Whisper is already in progress...!");
                stopTranscription();
            }
        });

        tvStatus = view.findViewById(R.id.tvStatus);
        tvResult = view.findViewById(R.id.tvResult);
        fabCopy = view.findViewById(R.id.fabCopy);
        fabCopy.setOnClickListener(v -> {
            // Get the text from tvResult
            String textToCopy = tvResult.getText().toString();

            // Copy the text to the clipboard
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Copied Text", textToCopy);
            clipboard.setPrimaryClip(clip);
        });

        // Audio recording functionality
        mRecorder = new Recorder(requireContext());
        mRecorder.setListener(new Recorder.RecorderListener() {
            @Override
            public void onUpdateReceived(String message) {
                Log.d(TAG, "Update is received, Message: " + message);
                handler.post(() -> tvStatus.setText(message));

                if (message.equals(Recorder.MSG_RECORDING)) {
                    handler.post(() -> tvResult.setText(""));
                    handler.post(() -> btnRecord.setText(R.string.stop));
                } else if (message.equals(Recorder.MSG_RECORDING_DONE)) {
                    handler.post(() -> btnRecord.setText(R.string.record));
                }
            }

            @Override
            public void onDataReceived(float[] samples) {
            }
        });

        // Audio playback functionality
        mPlayer = new Player(requireContext());
        mPlayer.setListener(new Player.PlaybackListener() {
            @Override
            public void onPlaybackStarted() {
                handler.post(() -> btnPlay.setText(R.string.stop));
            }

            @Override
            public void onPlaybackStopped() {
                handler.post(() -> btnPlay.setText(R.string.play));
            }
        });

        // Assume this Activity is the current activity, check record permission
        checkRecordPermission();

        return view;
    }

    private void initModel(File modelFile) {
        boolean isMultilingualModel = !(modelFile.getName().endsWith(ENGLISH_ONLY_MODEL_EXTENSION));
        String vocabFileName = isMultilingualModel ? MULTILINGUAL_VOCAB_FILE : ENGLISH_ONLY_VOCAB_FILE;
        File vocabFile = new File(sdcardDataFolder, vocabFileName);

//        boolean isMultilingualModel = true; // Force the model to be multilingual
//        File vocabFile = new File(sdcardDataFolder, MULTILINGUAL_VOCAB_FILE); // Use the multilingual vocabulary file

        mWhisper = new Whisper(requireContext());
        mWhisper.loadModel(modelFile, vocabFile, isMultilingualModel);
        mWhisper.setListener(new Whisper.WhisperListener() {
            @Override
            public void onUpdateReceived(String message) {
                Log.d(TAG, "Update is received, Message: " + message);

                if (message.equals(Whisper.MSG_PROCESSING)) {
                    handler.post(() -> tvStatus.setText(message));
                    handler.post(() -> tvResult.setText(""));
                    startTime = System.currentTimeMillis();
                } if (message.equals(Whisper.MSG_PROCESSING_DONE)) {
                    if (loopTesting)
                        transcriptionSync.sendSignal();
                } else if (message.equals(Whisper.MSG_FILE_NOT_FOUND)) {
                    handler.post(() -> tvStatus.setText(message));
                    Log.d(TAG, "File not found error...!");
                }
            }

            @Override
            public void onResultReceived(String result) {
                long timeTaken = System.currentTimeMillis() - startTime;
                handler.post(() -> tvStatus.setText("Processing done in " + timeTaken + "ms"));

                Log.d(TAG, "Result: " + result);
                handler.post(() -> tvResult.append(result));
            }
        });
    }

    private void deinitModel() {
        if (mWhisper != null) {
            mWhisper.unloadModel();
            mWhisper = null;
        }
    }

    private @NonNull ArrayAdapter<File> getFileArrayAdapter(ArrayList<File> waveFiles) {
        ArrayAdapter<File> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, waveFiles) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);
                File file = (File) getItem(position);  // Cast to File
                if (file != null) {
                    textView.setText(file.getName());  // Show only the file name
                }
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);
                File file = (File) getItem(position);  // Cast to File
                if (file != null) {
                    textView.setText(file.getName());  // Show only the file name
                }
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }


    private void checkRecordPermission() {
        int permission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO);
        if (permission == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Record permission is granted");
        } else {
            Log.d(TAG, "Requesting record permission");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Record permission is granted");
        } else {
            Log.d(TAG, "Record permission is not granted");
        }
    }

    private void startRecording() {
        checkRecordPermission();

        File waveFile = new File(sdcardDataFolder, WaveUtil.RECORDING_FILE);
        mRecorder.setFilePath(waveFile.getAbsolutePath());
        mRecorder.start();
    }

    private void stopRecording() {
        mRecorder.stop();
    }

    // Transcription calls
    private void startTranscription(String waveFilePath) {
        mWhisper.setFilePath(waveFilePath);
        mWhisper.setAction(Whisper.ACTION_TRANSCRIBE);
        mWhisper.start();
    }

    private void stopTranscription() {
        mWhisper.stop();
    }

    // Copy assets with specified extensions to destination folder
    private static void copyAssetsToSdcard(Context context, File destFolder, String[] extensions) {
        AssetManager assetManager = context.getAssets();

        try {
            String[] assetFiles = assetManager.list("");
            if (assetFiles == null) return;

            for (String assetFileName : assetFiles) {
                for (String extension : extensions) {
                    if (assetFileName.endsWith("." + extension)) {
                        File outFile = new File(destFolder, assetFileName);

                        if (outFile.exists()) break;

                        try (InputStream inputStream = assetManager.open(assetFileName);
                             OutputStream outputStream = new FileOutputStream(outFile)) {

                            byte[] buffer = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }
                        }
                        break; // No need to check further extensions
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ArrayList<File> getFilesWithExtension(File directory, String extension) {
        ArrayList<File> filteredFiles = new ArrayList<>();

        // Check if the directory is accessible
        if (directory != null && directory.exists()) {
            File[] files = directory.listFiles();

            // Filter files by the provided extension
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(extension)) {
                        filteredFiles.add(file);
                    }
                }
            }
        }

        return filteredFiles;
    }

    static class SharedResource {
        // Synchronized method for Thread 1 to wait for a signal with a timeout
        public synchronized boolean waitForSignalWithTimeout(long timeoutMillis) {
            long startTime = System.currentTimeMillis();

            try {
                wait(timeoutMillis); // Wait for the given timeout
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
                return false; // Thread interruption as timeout
            }

            long elapsedTime = System.currentTimeMillis() - startTime;

            // Check if wait returned due to notify or timeout
            return elapsedTime < timeoutMillis; // True if notified, False if timeout
        }

        // Synchronized method for Thread 2 to send a signal
        public synchronized void sendSignal() {
            notify(); // Notifies the waiting thread
        }
    }
}