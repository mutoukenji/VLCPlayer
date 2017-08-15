package tech.yaog.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.util.ArrayList;
import java.util.Arrays;

import tech.yaog.utils.statemachine.Event;
import tech.yaog.utils.statemachine.State;
import tech.yaog.utils.statemachine.StateMachine;

/**
 * VLC 视频播放器
 * Created by ygl_h on 2017/8/14.
 */
public class VlcVideoView extends FrameLayout implements MediaPlayer.EventListener, IVLCVout.Callback {

    private static final String TAG = VlcVideoView.class.getName();

    public void setPlaybackEvent(PlaybackEvent playbackEvent) {
        this.playbackEvent = playbackEvent;
    }

    public interface PlaybackEvent {
        void onStart();
        void onEnded();
        void onError();
        void onBuffering(int percent);
        void onPosition(int msec);
    }

    private LibVLC vlc;
    private Media media;
    private MediaPlayer player;
    private float buffering;
    private int widthMode;
    private int heightMode;
    private PlaybackEvent playbackEvent;
    private SurfaceView videoSurface;
    private SurfaceView subtitleSurface;

    private enum PlayerState {
        WaitingAttach,
        WaitingAttachPlay,
        Attached,
        Buffering,
        Playing,
        Paused
    }

    private enum PlayerEvent {
        Attach,
        AskForPlay,
        Play,
        Stop,
        Buffering,
        Position,
        Pause,
        Resume,
        End,
        Error
    }

    private StateMachine<PlayerState, PlayerEvent> stateMachine = new StateMachine<>();
    {
        State<PlayerState, PlayerEvent> waitingAttach = new State<PlayerState, PlayerEvent>(PlayerState.WaitingAttach)
                .onEvent(PlayerEvent.Attach, PlayerState.Attached)
                .onEvent(PlayerEvent.AskForPlay, PlayerState.WaitingAttachPlay);

        State<PlayerState, PlayerEvent> waitingAttachPlay = new State<PlayerState, PlayerEvent>(PlayerState.WaitingAttachPlay)
                .onEvent(PlayerEvent.Attach, PlayerState.Buffering);

        State<PlayerState, PlayerEvent> attached = new State<PlayerState, PlayerEvent>(PlayerState.Attached)
                .onEvent(PlayerEvent.AskForPlay, PlayerState.Buffering);

        State<PlayerState, PlayerEvent> buffering = new State<PlayerState, PlayerEvent>(PlayerState.Buffering)
                .onEntry(new State.Handler() {
                    @Override
                    public void handle(Object... data) {
                        player.setMedia(media);
                        player.play();
                    }
                })
                .onEvent(PlayerEvent.Buffering, new State.Handler() {
                    @Override
                    public void handle(Object... data) {
                        if (playbackEvent != null) {
                            if (data.length > 0) {
                                playbackEvent.onBuffering((int) data[0]);
                            }
                        }
                    }
                })
                .onEvent(PlayerEvent.Play, PlayerState.Playing)
                .onEvent(PlayerEvent.Pause, PlayerState.Paused)
                .onEvent(PlayerEvent.Error, PlayerState.Attached, new State.Handler() {
                    @Override
                    public void handle(Object... data) {
                        if (playbackEvent != null) {
                            playbackEvent.onError();
                        }
                    }
                });

        State<PlayerState, PlayerEvent> playing = new State<PlayerState, PlayerEvent>(PlayerState.Playing)
                .onEvent(PlayerEvent.End, PlayerState.Attached, new State.Handler() {
                    @Override
                    public void handle(Object... data) {
                        if (playbackEvent != null) {
                            playbackEvent.onEnded();
                        }
                    }
                })
                .onEvent(PlayerEvent.Stop, PlayerState.Attached, new State.Handler() {
                    @Override
                    public void handle(Object... data) {
                        player.stop();
                    }
                })
                .onEvent(PlayerEvent.Position, new State.Handler() {
                    @Override
                    public void handle(Object... data) {
                        if (playbackEvent != null) {
                            if (data.length > 0) {
                                playbackEvent.onPosition((int) data[0]);
                            }
                        }
                    }
                })
                .onEvent(PlayerEvent.Pause, PlayerState.Paused)
                .onEvent(PlayerEvent.Error, PlayerState.Attached, new State.Handler() {
                    @Override
                    public void handle(Object... data) {
                        if (playbackEvent != null) {
                            playbackEvent.onError();
                        }
                    }
                });

        State<PlayerState, PlayerEvent> paused = new State<PlayerState, PlayerEvent>(PlayerState.Paused)
                .onEntry(new State.Handler() {
                    @Override
                    public void handle(Object... data) {
                        player.pause();
                    }
                })
                .onEvent(PlayerEvent.End, PlayerState.Attached)
                .onEvent(PlayerEvent.Stop, PlayerState.Attached, new State.Handler() {
                    @Override
                    public void handle(Object... data) {
                        player.stop();
                    }
                })
                .onEvent(PlayerEvent.Resume, PlayerState.Playing, new State.Handler() {
                    @Override
                    public void handle(Object... data) {
                        player.play();
                    }
                });

        stateMachine.setStates(waitingAttach, waitingAttachPlay, attached, buffering, playing, paused);
        stateMachine.start();
    }

    public VlcVideoView(Context context) {
        super(context);
        generateSurfaceViews();
        init();
    }

    public VlcVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        generateSurfaceViews();
        init();
    }

    public VlcVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        generateSurfaceViews();
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public VlcVideoView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        generateSurfaceViews();
        init();
    }

    private void generateSurfaceViews() {
        videoSurface = new SurfaceView(getContext());
        subtitleSurface = new SurfaceView(getContext());
        addView(videoSurface, 0, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(subtitleSurface, 1, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    public void init(String... options) {
        if (!isInEditMode()) {
            if (vlc != null && !vlc.isReleased()) {
                vlc.release();
            }
            vlc = new LibVLC(getContext(), new ArrayList<>(Arrays.asList(options)));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            if (player != null && !player.isReleased()) {
                player.release();
            }
            player = new MediaPlayer(vlc);
            player.setEventListener(this);
            player.getVLCVout().setVideoView(videoSurface);
            player.getVLCVout().setSubtitlesView(subtitleSurface);
            player.getVLCVout().addCallback(this);
            player.getVLCVout().attachViews();
        }
        stateMachine.event(new Event<>(PlayerEvent.Attach));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (player != null && !player.isReleased()) {
            player.getVLCVout().removeCallback(this);
            player.release();
        }
        if (media != null && !media.isReleased()) {
            media.release();
        }
        if (vlc != null && !vlc.isReleased()) {
            vlc.release();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Log.v(TAG, "widthMode:"+ MeasureSpec.toString(widthMeasureSpec));
        Log.v(TAG, "heightMode:"+ MeasureSpec.toString(heightMeasureSpec));
        widthMode = MeasureSpec.getMode(widthMeasureSpec);
        heightMode = MeasureSpec.getMode(heightMeasureSpec);
        videoSurface.measure(widthMeasureSpec, heightMeasureSpec);
        subtitleSurface.measure(widthMeasureSpec, heightMeasureSpec);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    public boolean canPause() {
        return true;
    }

    public boolean canSeekBackward() {
        return player.isSeekable();
    }

    public boolean canSeekForward() {
        return player.isSeekable() && player.getPosition() < 1;
    }

    public int getBufferPercentage() {
        return (int) (buffering);
    }

    public int getCurrentPosition() {
        return (int) player.getTime();
    }

    public int getDuration() {
        if (media != null) {
            return (int) media.getDuration();
        }
        return -1;
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public void pause() {
        stateMachine.event(new Event<>(PlayerEvent.Pause));
    }

    public void resume() {
        stateMachine.event(new Event<>(PlayerEvent.Resume));
    }

    public void seekTo(int msec) {
        player.setPosition((float) ((double) msec / (double)media.getDuration()));
    }

    public void setVideoPath(String path) {
        media = new Media(vlc, path);
    }

    public void setVideoURI(Uri uri) {
        media = new Media(vlc, uri);
    }

    public void start() {
        if (media != null) {
            stateMachine.event(new Event<>(PlayerEvent.AskForPlay));
        }
    }
    public void stopPlayback() {
        stateMachine.event(new Event<>(PlayerEvent.Stop));
    }

    public void suspend() {
        pause();
    }

    @Override
    public void onEvent(MediaPlayer.Event event) {
        Log.v(TAG, "event:"+Integer.toHexString(event.type));
        switch(event.type) {
            case MediaPlayer.Event.Buffering:
                buffering = event.getBuffering();
                Log.v(TAG, "buffering: "+Math.round(buffering)+"%");
                stateMachine.event(new Event<>(PlayerEvent.Buffering, buffering));
                break;
            case MediaPlayer.Event.Stopped:
                break;
            case MediaPlayer.Event.Paused:
                break;
            case MediaPlayer.Event.Playing:
                stateMachine.event(new Event<>(PlayerEvent.Play));
                break;
            case MediaPlayer.Event.EndReached:
                stateMachine.event(new Event<>(PlayerEvent.End));
                break;
            case MediaPlayer.Event.TimeChanged:
                stateMachine.event(new Event<>(PlayerEvent.Position, (int) event.getTimeChanged()));
                break;
            case MediaPlayer.Event.EncounteredError:
                stateMachine.event(new Event<>(PlayerEvent.Error));
                break;
            default:
                break;
        }
    }

    @Override
    public void onNewLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
        int newWidth = getMeasuredWidth();
        int newHeight = getMeasuredHeight();
        Log.v(TAG, "measuredWH:"+newWidth+","+newHeight);
        Log.v(TAG, "newLayout:"+width+","+height);
        Log.v(TAG, "mode:"+widthMode+","+heightMode);
        if ((widthMode == MeasureSpec.UNSPECIFIED || widthMode == MeasureSpec.AT_MOST) && (heightMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.AT_MOST)) {
            newWidth = width;
            newHeight = height;
        }
        else if (widthMode == MeasureSpec.EXACTLY && (heightMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.AT_MOST)) {
            newHeight = newWidth / width * height;
        }
        else if ((widthMode == MeasureSpec.UNSPECIFIED || widthMode == MeasureSpec.AT_MOST) && heightMode == MeasureSpec.EXACTLY) {
            newWidth = newHeight / height * width;
        }
        Log.v(TAG, "changed:"+newWidth+","+newHeight);
        videoSurface.getHolder().setFixedSize(newWidth, newHeight);
        subtitleSurface.getHolder().setFixedSize(newWidth, newHeight);
        vlcVout.setWindowSize(newWidth, newHeight);
        if (newHeight != getMeasuredHeight() || newWidth != getMeasuredWidth()) {
            ViewGroup.LayoutParams layoutParams = getLayoutParams();
            if (widthMode == MeasureSpec.UNSPECIFIED || widthMode == MeasureSpec.AT_MOST) {
                layoutParams.width = newWidth;
            }
            if (heightMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.AT_MOST) {
                layoutParams.height = newHeight;
            }
            setLayoutParams(layoutParams);
            postInvalidate();
        }
    }

    @Override
    public void onSurfacesCreated(IVLCVout vlcVout) {
    }

    @Override
    public void onSurfacesDestroyed(IVLCVout vlcVout) {
        vlcVout.detachViews();
    }

    @Override
    public void onHardwareAccelerationError(IVLCVout vlcVout) {

    }
}
