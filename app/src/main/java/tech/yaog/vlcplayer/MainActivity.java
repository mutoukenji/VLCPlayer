package tech.yaog.vlcplayer;

import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import tech.yaog.widgets.VlcVideoView;

public class MainActivity extends AppCompatActivity {

    private VlcVideoView videoView;
//    private VlcVideoView videoView2;
//    private VlcVideoView videoView3;
    private String videoFilePath;
    private String videoFilePath2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        videoView = (VlcVideoView) findViewById(R.id.video);
//        videoView2 = (VlcVideoView) findViewById(R.id.video2);
//        videoView3 = (VlcVideoView) findViewById(R.id.video3);
//        videoView.init("-vvv");

        File dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (dir != null) {
            if (!dir.exists()) {
                dir.mkdirs();
            }
            AssetManager assetManager = getAssets();
            List<String> videoExts = new ArrayList<>();
            videoExts.add("swf");
            videoExts.add("mp4");
            videoExts.add("avi");
            videoExts.add("flv");
            try {
                String[] files = assetManager.list("");
                for (String file : files) {
                    String[] parts = file.split("\\.");
                    if (videoExts.contains(parts[parts.length-1])) {
                        File destFile = new File(dir.getAbsolutePath()+File.separator+file);
                        if (!destFile.exists()) {
                            FileOutputStream fos = new FileOutputStream(destFile);
                            InputStream is = assetManager.open(file);
                            byte[] buffer = new byte[1024*1024];
                            int read;
                            while ((read = is.read(buffer)) >= 0) {
                                fos.write(buffer, 0, read);
                            }
                            is.close();
                            fos.close();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            videoFilePath = dir.getAbsolutePath()+File.separator+"demo.flv";
            videoFilePath2 = dir.getAbsolutePath()+File.separator+"demo.mp4";
        }

        videoView.setVideoPath(videoFilePath2);
//        videoView2.setVideoPath(videoFilePath);
//        videoView3.setVideoPath(videoFilePath2);
    }

    @Override
    protected void onStart() {
        super.onStart();
        videoView.start();
//        videoView2.start();
//        videoView3.start();
    }

    @Override
    protected void onStop() {
        videoView.stopPlayback();
//        videoView2.stopPlayback();
//        videoView3.stopPlayback();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        videoView.resume();
//        videoView2.resume();
//        videoView3.resume();
    }

    @Override
    protected void onPause() {
        videoView.pause();
//        videoView2.pause();
//        videoView3.pause();
        super.onPause();
    }
}
