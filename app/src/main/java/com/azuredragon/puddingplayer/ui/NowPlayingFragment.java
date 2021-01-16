package com.azuredragon.puddingplayer.ui;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.transition.Slide;
import androidx.transition.Transition;

import com.azuredragon.puddingplayer.R;
import com.azuredragon.puddingplayer.Utils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

public class NowPlayingFragment extends Fragment {
    private View playerView;
    private BottomSheetBehavior<ConstraintLayout> bottomSheetBehavior;

    private MediaControllerCompat mController;
    private Context mContext;

    NowPlayingFragment(MediaControllerCompat controller) {
        mController = controller;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(new Bundle());
        Transition slideIn = new Slide();
        setEnterTransition(slideIn);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        playerView = inflater.inflate(R.layout.fragment_player, container, false);
        return playerView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setBottomSheetBehavior();
        mController.registerCallback(controllerCallback);
        setMetadata(mController.getMetadata());
        setPlaybackState(mController.getPlaybackState());
        showPlayerPosition();
        ((SeekBar)playerView.findViewById(R.id.large_position)).setOnSeekBarChangeListener(seekBarChangeListener);
        playerView.findViewById(R.id.large_btn_next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mController.getTransportControls().skipToNext();
            }
        });
        playerView.findViewById(R.id.large_btn_prev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mController.getTransportControls().skipToPrevious();
            }
        });
    }

    private void setBottomSheetBehavior() {
        ConstraintLayout bottomSheetNowPlayingFragmentLayout = playerView.findViewById(R.id.bottomSheetNowPlayingFragmentLayout);
        bottomSheetNowPlayingFragmentLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED)
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetNowPlayingFragmentLayout);
        bottomSheetBehavior.setHideable(true);
        bottomSheetBehavior.setDraggable(true);
        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if(newState == BottomSheetBehavior.STATE_HIDDEN) {
                    mController.getTransportControls().stop();
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                playerView.findViewById(R.id.sectionSmallPlayer).setAlpha(1 - slideOffset);
                playerView.findViewById(R.id.sectionLargePlayer).setAlpha(slideOffset);
            }
        });
    }

    boolean onBackPressed() {
        if(bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            return true;
        }
        return false;
    }

    private void setMetadata(MediaMetadataCompat metadata) {
        ImageView artwork = playerView.findViewById(R.id.artwork);
        ImageView largeArtwork = playerView.findViewById(R.id.large_artwork);
        TextView title = playerView.findViewById(R.id.title);
        TextView largeTitle = playerView.findViewById(R.id.large_title);
        TextView author = playerView.findViewById(R.id.author);
        TextView largeAuthor = playerView.findViewById(R.id.large_author);
        TextView totalDuration = playerView.findViewById(R.id.total_duration);

        artwork.setImageBitmap(BitmapFactory.decodeFile(mContext.getApplicationInfo().dataDir + "/thumbnail.jpg"));
        largeArtwork.setImageBitmap(BitmapFactory.decodeFile(mContext.getApplicationInfo().dataDir + "/thumbnail.jpg"));
        title.setText(metadata.getText(MediaMetadataCompat.METADATA_KEY_TITLE));
        largeTitle.setText(metadata.getText(MediaMetadataCompat.METADATA_KEY_TITLE));
        author.setText(metadata.getText(MediaMetadataCompat.METADATA_KEY_ARTIST));
        largeAuthor.setText(metadata.getText(MediaMetadataCompat.METADATA_KEY_ARTIST));
        totalDuration.setText(Utils.secondToString(metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000));
    }

    private void setPlaybackState(PlaybackStateCompat state) {
        ImageButton playPause = playerView.findViewById(R.id.btn_play_pause);
        ImageButton largePlayPause = playerView.findViewById(R.id.large_btn_play_pause);
        ProgressBar position = playerView.findViewById(R.id.current_position);

        View.OnClickListener buttonPlay = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mController.getTransportControls().play();
            }
        };
        View.OnClickListener buttonPause = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mController.getTransportControls().pause();
            }
        };

        switch (state.getState()) {
            case PlaybackStateCompat.STATE_PLAYING:
                position.setIndeterminate(false);
                if(bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN)
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                playPause.setImageResource(R.drawable.btn_pause);
                playPause.setOnClickListener(buttonPause);
                largePlayPause.setImageResource(R.drawable.btn_pause);
                largePlayPause.setOnClickListener(buttonPause);
                break;
            case PlaybackStateCompat.STATE_BUFFERING:
                break;
            case PlaybackStateCompat.STATE_CONNECTING:
                position.setProgress(0);
                position.setIndeterminate(true);
                if(bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN)
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                break;
            case PlaybackStateCompat.STATE_ERROR:
                break;
            case PlaybackStateCompat.STATE_NONE:
            case PlaybackStateCompat.STATE_STOPPED:
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                break;
            case PlaybackStateCompat.STATE_PAUSED:
                position.setIndeterminate(false);
                if(bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN)
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                playPause.setImageResource(R.drawable.btn_play);
                playPause.setOnClickListener(buttonPlay);
                largePlayPause.setImageResource(R.drawable.btn_play);
                largePlayPause.setOnClickListener(buttonPlay);
                break;
        }
    }

    private void showPlayerPosition() {
        long duration = mController.getMetadata().getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        if(duration == 0L) return;
        final ProgressBar position = playerView.findViewById(R.id.current_position);
        final SeekBar largePosition = playerView.findViewById(R.id.large_position);
        final TextView currentDuration = playerView.findViewById(R.id.current_duration);

        position.setIndeterminate(false);
        position.setMax((int) duration);
        largePosition.setMax((int) duration);
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(mController.getPlaybackState().getState() != PlaybackStateCompat.STATE_PLAYING &&
                        mController.getPlaybackState().getState() != PlaybackStateCompat.STATE_PAUSED) return;
                position.setProgress((int) mController.getPlaybackState().getPosition());
                largePosition.setSecondaryProgress((int) mController.getPlaybackState().getBufferedPosition());
                if(!isSeekbarTouching) {
                    largePosition.setProgress((int) mController.getPlaybackState().getPosition());
                    currentDuration.setText(Utils.secondToString(mController.getPlaybackState().getPosition() / 1000));
                }
                handler.postDelayed(this, 100);
            }
        }, 100);
    }

    private boolean isSeekbarTouching = false;
    SeekBar.OnSeekBarChangeListener seekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if(fromUser) ((TextView)playerView.findViewById(R.id.current_duration)).setText(Utils.secondToString(progress / 1000));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            isSeekbarTouching = true;
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            isSeekbarTouching = false;
            mController.getTransportControls().seekTo(seekBar.getProgress());
        }
    };

    MediaControllerCompat.Callback controllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);
            setPlaybackState(state);
            showPlayerPosition();
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            super.onMetadataChanged(metadata);
            setMetadata(metadata);
        }
    };
}
