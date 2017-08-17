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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import tech.yaog.utils.statemachine.Event;
import tech.yaog.utils.statemachine.State;
import tech.yaog.utils.statemachine.StateMachine;

/**
 * VLC 视频播放器
 * 只包含基础功能，用法类似{@linkplain android.widget.VideoView|VideoView}
 * Created by mutoukenji on 2017/8/14.
 */
public class VlcVideoView extends FrameLayout implements MediaPlayer.EventListener, IVLCVout.Callback {

    private static final String TAG = VlcVideoView.class.getName();
    private LibVLC vlc;
    private Uri subtitle;
    private Media media;
    private MediaPlayer player;
    private float buffering;
    private int widthMode;
    private int heightMode;
    private PlaybackEvent playbackEvent;
    private SurfaceView videoSurface;
    private SurfaceView subtitleSurface;
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
                        if (subtitle != null) {
                            player.addSlave(Media.Slave.Type.Subtitle, subtitle, true);
                        } else {
                            player.setSpuTrack(-1);
                        }
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
                .onEvent(PlayerEvent.SetSubtitle, new State.Handler() {
                    @Override
                    public void handle(Object... data) {
                        if (subtitle != null) {
                            player.addSlave(Media.Slave.Type.Subtitle, subtitle, true);
                        } else {
                            player.setSpuTrack(-1);
                        }
                    }
                })
                .onEvent(PlayerEvent.Play, PlayerState.Playing, new State.Handler() {
                    @Override
                    public void handle(Object... data) {
                        if (playbackEvent != null) {
                            playbackEvent.onStart();
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
                .onEvent(PlayerEvent.SetSubtitle, new State.Handler() {
                    @Override
                    public void handle(Object... data) {
                        if (subtitle != null) {
                            player.addSlave(Media.Slave.Type.Subtitle, subtitle, true);
                        } else {
                            player.setSpuTrack(-1);
                        }
                    }
                })
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
                .onEvent(PlayerEvent.SetSubtitle, new State.Handler() {
                    @Override
                    public void handle(Object... data) {
                        if (subtitle != null) {
                            player.addSlave(Media.Slave.Type.Subtitle, subtitle, true);
                        } else {
                            player.setSpuTrack(-1);
                        }
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

    /**
     * 设置播放回调事件监听
     *
     * @param playbackEvent 播放回调事件
     */
    public void setPlaybackEvent(PlaybackEvent playbackEvent) {
        this.playbackEvent = playbackEvent;
    }

    private void generateSurfaceViews() {
        videoSurface = new SurfaceView(getContext());
        subtitleSurface = new SurfaceView(getContext());
        addView(videoSurface, 0, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(subtitleSurface, 1, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    /**
     * VLC 接口初始化（可选）
     *
     * @param options VLC 参数，详情请参考 https://wiki.videolan.org/VLC_command-line_help/
     */
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

    /**
     * 可否暂停
     *
     * @return 可否暂停
     */
    public boolean canPause() {
        return true;
    }

    /**
     * 可否后倒
     *
     * @return 可否后倒
     */
    public boolean canSeekBackward() {
        return player.isSeekable();
    }

    /**
     * 可否快进
     *
     * @return 可否快进
     */
    public boolean canSeekForward() {
        return player.isSeekable() && player.getPosition() < 1;
    }

    /**
     * 取得当前缓冲比例
     *
     * @return 当前缓冲比例 (0 - 100)
     */
    public int getBufferPercentage() {
        return (int) (buffering);
    }

    /**
     * 取得当前播放位置
     *
     * @return 当前播放位置(ms)
     */
    public int getCurrentPosition() {
        if (player == null) {
            return -1;
        }
        return (int) player.getTime();
    }

    /**
     * 取得视频长度
     *
     * @return 视频长度(ms)
     */
    public int getDuration() {
        if (media != null) {
            return (int) media.getDuration();
        }
        return -1;
    }

    /**
     * 是否正在播放
     *
     * @return 是否正在播放
     */
    public boolean isPlaying() {
        if (player == null) {
            return false;
        }
        return player.isPlaying();
    }

    /**
     * 暂停
     */
    public void pause() {
        stateMachine.event(new Event<>(PlayerEvent.Pause));
    }

    /**
     * 继续播放
     */
    public void resume() {
        stateMachine.event(new Event<>(PlayerEvent.Resume));
    }

    /**
     * 跳转至
     *
     * @param msec 跳转到的位置 (ms)
     */
    public void seekTo(int msec) {
        player.setPosition((float) ((double) msec / (double) media.getDuration()));
    }

    /**
     * 设置视频路径（本地文件）
     *
     * @param path 文件路径
     */
    public void setVideoPath(String path) {
        media = new Media(vlc, path);
    }

    /**
     * 设置视频路径（网络视频）
     *
     * @param uri 视频地址
     */
    public void setVideoURI(Uri uri) {
        media = new Media(vlc, uri);
    }

    /**
     * 开始播放
     */
    public void start() {
        if (media != null) {
            stateMachine.event(new Event<>(PlayerEvent.AskForPlay));
        }
    }

    /**
     * 停止播放
     */
    public void stopPlayback() {
        stateMachine.event(new Event<>(PlayerEvent.Stop));
    }

    /**
     * 暂停
     */
    public void suspend() {
        pause();
    }

    /**
     * 设置字幕文件路径
     *
     * @param path 字幕文件路径
     */
    public void setSubtitle(String path) {
        if (path != null) {
            setSubtitle(Uri.fromFile(new File(path)));
        } else {
            subtitle = null;
            stateMachine.event(new Event<>(PlayerEvent.SetSubtitle));
        }
    }

    /**
     * 设置字幕地址
     *
     * @param uri 在线字幕地址
     */
    public void setSubtitle(Uri uri) {
        subtitle = uri;
        stateMachine.event(new Event<>(PlayerEvent.SetSubtitle));
    }

    @Override
    public void onEvent(MediaPlayer.Event event) {
        switch (event.type) {
            case MediaPlayer.Event.Buffering:
                buffering = event.getBuffering();
                Log.v(TAG, "buffering: " + Math.round(buffering) + "%");
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
        int oldWidth = getMeasuredWidth();
        int oldHeight = getMeasuredHeight();
        int newWidth = oldWidth;
        int newHeight = oldHeight;
        Log.v(TAG, "measuredWH:" + newWidth + "," + newHeight);
        Log.v(TAG, "newLayout:" + width + "," + height);
        Log.v(TAG, "mode:" + (widthMode == MeasureSpec.AT_MOST ? "AT_MOST" : widthMode == MeasureSpec.UNSPECIFIED ? "UNSPECIFIED" : "EX") + "," + (heightMode == MeasureSpec.AT_MOST ? "AT_MOST" : heightMode == MeasureSpec.UNSPECIFIED ? "UNSPECIFIED" : "EX"));

        double widthRate = (double) width / (double) oldWidth;
        double heightRate = (double) height / (double) oldHeight;

        Log.v(TAG, "ratios:" + widthRate + ":" + heightRate);

        if (widthRate > heightRate) {
            if (widthMode == MeasureSpec.AT_MOST) {
                newWidth = Math.min(width, oldWidth);
            } else if (widthMode == MeasureSpec.UNSPECIFIED) {
                newWidth = width;
            }
            if (heightMode == MeasureSpec.AT_MOST || heightMode == MeasureSpec.UNSPECIFIED) {
                newHeight = (int) Math.ceil((double) newWidth / (double) width * (double) height);
            }
        } else {
            if (heightMode == MeasureSpec.AT_MOST) {
                newHeight = Math.min(height, oldHeight);
            } else if (heightMode == MeasureSpec.UNSPECIFIED) {
                newHeight = height;
            }
            if (widthMode == MeasureSpec.AT_MOST || widthMode == MeasureSpec.UNSPECIFIED) {
                newWidth = (int) Math.ceil((double) newHeight / (double) height * (double) width);
            }
        }

        Log.v(TAG, "changed:" + newWidth + "," + newHeight);

        videoSurface.getHolder().setFixedSize(newWidth, newHeight);
        subtitleSurface.getHolder().setFixedSize(newWidth, newHeight);
        vlcVout.setWindowSize(newWidth, newHeight);

        if (newHeight != oldHeight || newWidth != oldWidth) {
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
        SetSubtitle,
        Play,
        Stop,
        Buffering,
        Position,
        Pause,
        Resume,
        End,
        Error
    }

    /**
     * 播放回调事件
     */
    public interface PlaybackEvent {
        /**
         * 开始播放
         */
        void onStart();

        /**
         * 播放完成
         */
        void onEnded();

        /**
         * 视频读取或解码错误
         */
        void onError();

        /**
         * 缓冲比例
         *
         * @param percent 缓冲比例（0-100）
         */
        void onBuffering(int percent);

        /**
         * 当前播放位置
         *
         * @param msec 毫秒数
         */
        void onPosition(int msec);
    }
}
