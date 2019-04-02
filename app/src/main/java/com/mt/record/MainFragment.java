package com.mt.record;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.cokus.wavelibrary.draw.WaveCanvas;
import com.cokus.wavelibrary.view.WaveSurfaceView;
import com.cokus.wavelibrary.view.WaveformView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class MainFragment extends Fragment implements View.OnClickListener {

    private static final int TOTAL_TIME = 2 * 60; // 单位：秒
    Button playRecord;
    android.support.design.widget.FloatingActionButton record;
    Button repeatRecord;
    Button saveRecord;
    Chronometer recordTimer;
    TextView pauseTV;
    WaveSurfaceView waveSfv;
    WaveformView waveView;
    private WaveCanvas waveCanvas;

    private RecorderManager recordManager; // 录制音频
    private boolean isRecording = false; // 录音中的标志位
    private long recordTime; // 用于记录计时暂停时的 SystemClock.elapsedRealTime();
    private int audioDuration;   // 录音时长
    private String audioDir;
    private String audioPath;

    private PermissionHelper permissionHelper; // 申请权限

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        playRecord = view.findViewById(R.id.record_play);
        record = view.findViewById(R.id.record_goon);
        repeatRecord = view.findViewById(R.id.record_reset);
        saveRecord = view.findViewById(R.id.record_save);
        recordTimer = view.findViewById(R.id.timer);
        pauseTV = view.findViewById(R.id.pause);
        waveSfv = view.findViewById(R.id.wavesfv);
        waveView = view.findViewById(R.id.waveview);

        if(waveSfv != null) {
            waveSfv.setLine_off(42);
            //解决surfaceView黑色闪动效果
            waveSfv.setZOrderOnTop(true);
            waveSfv.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        }
        waveView.setLine_offset(42);

        playRecord.setOnClickListener(this);
        record.setOnClickListener(this);
        repeatRecord.setOnClickListener(this);
        saveRecord.setOnClickListener(this);

        enableButton(false, playRecord, repeatRecord, saveRecord, record);

        audioDir = FileUtils.getAppRecordDir(getContext()).getAbsolutePath();
        recordManager = RecorderManager.getInstance(audioDir);

        recordTimer.setOnChronometerTickListener(chronometer -> {

            // 超时
            if (timerDuration() >= TOTAL_TIME) {
                // 录音中
                if (recordManager.isStarted()) {
                    if (isRecording) {
                        // 录音暂停
                        pauseRecord();
                    }
                }

                recordTimer.stop();
                enableButton(false, record);

                return;
            }

            if (!MediaManager.isPlaying()) {
                setSeekProgress(TOTAL_TIME);
            } else {
                // 播放中
                setSeekProgress(audioDuration);
            }
        });

        permissionHelper = new PermissionHelper(this);
        checkPermission();


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionHelper.handleRequestPermissionsResult(requestCode, permissions, grantResults);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.record_play:
                playRecord();
                break;

            case R.id.record_goon:
                record();
                break;

            case R.id.record_reset:
                resetRecord();
                break;

            case R.id.record_save:
                saveRecord();
                break;
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        MediaManager.release();

        if (recordManager != null) {
            recordManager.cancel();
            recordManager = null;
        }
    }


    /**
     * 保存并上传录音文件到七牛
     * 上传之后返回前页面
     */
    private void saveRecord() {

        if (MediaManager.isPlaying()) {
            MediaManager.release();
        }

        if (isRecording) {
            pauseRecord();
        }

        recordManager.release();
    }

    /**
     * 重新录制
     */
    private void resetRecord() {
        new AlertDialog.Builder(getContext())
                .setMessage(getString(R.string.alert_reset_record))
                .setPositiveButton(getString(R.string.alert_positive_reset), (dialog, which) -> {
                    recordManager.cancel();
                    resetTimer();
                    enableButton(false, playRecord, saveRecord);
                    enableButton(true, record);
                })
                .setNegativeButton(getString(R.string.cancel_text), null)
                .create()
                .show();
    }

    /**
     * 录制
     * 开始、暂停、继续
     */
    private void record() {
        if (recordManager.isStarted()) {
            if (isRecording) {
                // 录音暂停
                pauseRecord();
            } else {
                // 录音继续
                recordManager.resume();
                isRecording = true;
                setRecordBtn(true);
                goonTimer();
                enableButton(false, repeatRecord, saveRecord, playRecord);
            }
        } else {
            // 录音开始
            recordManager.startRecorder(true);
            startTimer();
            setRecordBtn(true);
            isRecording = true;
            enableButton(false, repeatRecord, saveRecord, saveRecord);
            startAudio();
        }
    }

    /**
     * 开始录音
     */
    private void startAudio(){
        waveCanvas = new WaveCanvas();
        waveCanvas.baseLine = waveSfv.getHeight() / 2;
        waveCanvas.Start(recordManager.getAudioPath(), 8000, waveSfv, recordManager.getAudioPath(), U.DATA_DIRECTORY, new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                return true;
            }
        });
    }

    /**
     * 录音暂停
     */
    private void pauseRecord() {
        pauseTimer();
        recordManager.pause();
        audioDuration = timerDuration();
        setRecordBtn(false);
        isRecording = false;
        enableButton(true, repeatRecord, saveRecord, playRecord);
    }

    /**
     * 播放录音文件
     */
    private void playRecord() {
        resetAudio();

        MediaManager.playSound(recordManager.getAudioPath(), mp -> {
            mp.start();

            startTimer();
            enableButton(false, record, repeatRecord, playRecord);
            enableButton(true, saveRecord);
        }, mp -> {
            MediaManager.release();

            pauseTimer();
            enableButton(true, record, repeatRecord, saveRecord, playRecord);

            if (timerDuration() >= TOTAL_TIME) {
                enableButton(false, record);
            }
        }, getContext());

    }

    private void startTimer() {
        recordTimer.setBase(SystemClock.elapsedRealtime());
        recordTimer.start();
    }

    private void pauseTimer() {
        recordTime = SystemClock.elapsedRealtime();
        recordTimer.stop();
    }

    private void resetTimer() {
        recordTimer.setBase(SystemClock.elapsedRealtime());
        recordTimer.stop();
    }

    private void goonTimer() {
        if (recordTime != 0) {
            recordTimer.setBase(recordTimer.getBase() + (SystemClock.elapsedRealtime() - recordTime));
        } else {
            recordTimer.setBase(SystemClock.elapsedRealtime());
        }
        recordTimer.start();
    }

    private void resetAudio() {
        if (MediaManager.isPlaying()) {
            MediaManager.release();
        }
    }

    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermission();
        } else {
            enableButton(true, record);
        }
    }

    private void requestPermission() {
        permissionHelper.requestPermissions(getString(R.string.request_audio_permission),
                new PermissionHelper.PermissionListener() {
                    @Override
                    public void doAfterGrand(String... permission) {
                        enableButton(true, record);
                    }

                    @Override
                    public void doAfterDenied(String... permission) {
                        userRefusePermissionsDialog();
                    }
                }, Manifest.permission.RECORD_AUDIO);
    }

    private void userRefusePermissionsDialog() {
        new AlertDialog.Builder(getContext())
                .setMessage(getString(R.string.request_audio_permission))
                .setPositiveButton(getString(R.string.setting), (dialog, which) -> {
                    //引导用户到设置中去进行设置
                    Intent intent = new Intent();
                    intent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
                    intent.setData(Uri.fromParts("package", getContext().getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton(getString(R.string.cancel_text), null)
                .create().show();
    }

    private void enableButton(boolean enabled, View... views) {
        for (View view : views) {
            view.setEnabled(enabled);
        }
    }

    private int timerDuration() {
        return (int) ((SystemClock.elapsedRealtime() - recordTimer.getBase()) / 1000);
    }

    private void setRecordBtn(boolean isRecording) {
        if (isRecording) {
            pauseTV.setVisibility(View.INVISIBLE);
            record.setImageResource((R.mipmap.ic_shortcut_pause));
        } else {
            pauseTV.setVisibility(View.VISIBLE);
            record.setImageResource((R.mipmap.ic_shortcut_rec));
        }
    }

    private void setSeekProgress(int totalDuration) {
        int progress = (int) (((float) timerDuration() / totalDuration) * 100);

        if (progress >= 100) {
            recordTimer.stop();
        }
    }

    public void convertAudio(String audioPath, String audioDir) {
        AmrFileDecoder amrFileDecoder = new AmrFileDecoder();
        try {
            String wavFilePath = amrFileDecoder.amrToWav(new FileInputStream(new File(audioPath)), audioDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
