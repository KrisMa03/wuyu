package snow.player.ui.equalizer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.sdsmdg.harjot.crollerTest.Croller;
import com.sdsmdg.harjot.crollerTest.OnCrollerChangeListener;

import java.util.ArrayList;
import java.util.List;

import snow.player.PlayerClient;
import snow.player.PlayerService;
import snow.music.R;
import snow.music.databinding.ActivityEqualizerBinding;
import snow.player.ui.util.AndroidAudioEffectConfigUtil;
import snow.player.ui.util.Preconditions;
import snow.player.ui.widget.EqualizerBandView;

/**
 * 均衡器 Activity，用于配置播放器的音频特效。
 * <p>
 * 该 Activity 提供了以下 3 种音频特效：
 * <ul>
 *     <li>均衡器</li>
 *     <li>低音增强</li>
 *     <li>环绕音</li>
 * </ul>
 * <p>
 * 要使用该音频特效，需要覆盖 {@link PlayerService} 的 {@code onCreateAudioEffectManager()} 方法，
 * 并返回一个 {@link AndroidAudioEffectManager} 对象。
 * <p>
 * {@link AndroidAudioEffectManager} 类用于与当前 Activity 配合使用，共同实现音频特效功能。
 * <p>
 * <b>注意！要启动 {@link EqualizerActivity}，请使用 {@link #start(Context, Class)} 方法，
 * 而不是直接使用 {@code Intent#startActivity()}。</b>
 */
public class EqualizerActivity extends AppCompatActivity {
    private static final String KEY_PLAYER_SERVICE = "PLAYER_SERVICE";

    private EqualizerViewModel mEqualizerViewModel;
    private ActivityEqualizerBinding mBinding;

    /**
     * 启动 {@link EqualizerActivity}。
     *
     * @param context       Context 对象，不能为 null
     * @param playerService 你的 PlayerService 的 Class 对象，不能为 null
     */
    public static void start(@NonNull Context context, @NonNull Class<? extends PlayerService> playerService) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(playerService);

        Intent intent = new Intent(context, EqualizerActivity.class);
        intent.putExtra(KEY_PLAYER_SERVICE, playerService);
        context.startActivity(intent);
    }

    @SuppressWarnings("unchecked")
    private Class<? extends PlayerService> getPlayerServiceClazz() {
        Intent intent = getIntent();
        Class<? extends PlayerService> playerService = (Class<? extends PlayerService>) intent.getSerializableExtra(KEY_PLAYER_SERVICE);

        if (playerService == null) {
            throw new IllegalArgumentException("PlayerService is null.");
        }

        return playerService;
    }

    public void finishSelf(View view) {
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.snow_ui_activity_equalizer);

        initEqualizerViewModel();

        mBinding.setEqualizerViewModel(mEqualizerViewModel);
        mBinding.setLifecycleOwner(this);

        initBandChart();
        initPresetSpinner();
        initEqualizerBands();
        initBassCroller();
        initVirtualizerCroller();

        mBinding.btnBack.setOnClickListener(this::finishSelf);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            Boolean enabled = mEqualizerViewModel.getEnabled().getValue();
            assert enabled != null;
            if (!enabled) {
                Toast.makeText(this, R.string.snow_ui_toast_equalizer_not_enabled, Toast.LENGTH_SHORT).show();
                return true;
            }
        }

        return super.onTouchEvent(event);
    }

    private void initEqualizerViewModel() {
        ViewModelProvider provider = new ViewModelProvider(this);
        mEqualizerViewModel = provider.get(EqualizerViewModel.class);

        if (mEqualizerViewModel.isInitialized()) {
            return;
        }

        PlayerClient playerClient = PlayerClient.newInstance(this, getPlayerServiceClazz());
        mEqualizerViewModel.init(playerClient);
        playerClient.connect();
    }

    private void initBandChart() {
        mBinding.bandChart.init(mEqualizerViewModel);
    }

    private void initPresetSpinner() {
        int numberOfPresets = mEqualizerViewModel.getEqualizerNumberOfPresets();
        List<String> allPresetName = new ArrayList<>(numberOfPresets + 1);

        allPresetName.add(getString(R.string.snow_ui_equalizer_preset_custom));

        for (int preset = 0; preset < numberOfPresets; preset++) {
            String presetName = mEqualizerViewModel.getEqualizerPresetName((short) preset);
            allPresetName.add(AndroidAudioEffectConfigUtil.optimizeEqualizerPresetName(this, presetName));
        }

        PresetAdapter adapter = new PresetAdapter(allPresetName);
        int currentPreset = mEqualizerViewModel.getEqualizerCurrentPreset();

        mBinding.presetSpinner.setAdapter(adapter);
        mBinding.presetSpinner.setSelection(currentPreset + 1);

        mBinding.presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    return;
                }

                mEqualizerViewModel.equalizerUsePreset((short) (position - 1));
                mEqualizerViewModel.applyChanges();

                mBinding.bandChart.notifyEqualizerSettingChanged();
                mBinding.equalizerBands.notifyEqualizerSettingChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // ignore
            }
        });

        mBinding.btnSpinnerArrow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBinding.presetSpinner.performClick();
            }
        });
    }

    private void initEqualizerBands() {
        mBinding.equalizerBands.init(mEqualizerViewModel);

        mBinding.equalizerBands.setOnBandChangeListener(new EqualizerBandView.OnBandChangeListener() {
            @Override
            public void onBandChanged() {
                if (mBinding.presetSpinner.getSelectedItemPosition() != 0) {
                    mBinding.presetSpinner.setSelection(0);
                }
            }
        });

        mBinding.equalizerBands.setOnEqualizerSettingChangeListener(new EqualizerBandView.OnEqualizerSettingChangeListener() {
            @Override
            public void onEqualizerSettingChanged() {
                mBinding.bandChart.notifyEqualizerSettingChanged();
            }
        });
    }

    private void initBassCroller() {
        int strength = mEqualizerViewModel.getBassBoostRoundedStrength();
        double percent = strength * 1.0 / 1000;

        // 因为 Croller 无法滑动到 0，因此将 min 设为 1，将 max 设置为 26
        final int rangeSize = 25;
        final int min = 1;
        final int max = min + rangeSize;
        mBinding.bassCroller.setMin(min);
        mBinding.bassCroller.setMax(max);

        mBinding.bassCroller.setProgress(min + (int) Math.round(percent * rangeSize));

        mBinding.bassCroller.setOnCrollerChangeListener(new OnCrollerChangeListener() {
            @Override
            public void onProgressChanged(Croller croller, int progress) {
                short strength = (short) ((progress - min) * (1000 / rangeSize));

                if (strength < 0) {
                    mEqualizerViewModel.setBassBoostStrength((short) 0);
                } else if (strength > 1000) {
                    mEqualizerViewModel.setBassBoostStrength((short) 1000);
                } else {
                    mEqualizerViewModel.setBassBoostStrength(strength);
                }
            }

            @Override
            public void onStartTrackingTouch(Croller croller) {
                // ignore
            }

            @Override
            public void onStopTrackingTouch(Croller croller) {
                mEqualizerViewModel.applyChanges();
            }
        });
    }

    private void initVirtualizerCroller() {
        int strength = mEqualizerViewModel.getVirtualizerStrength();
        double percent = strength * 1.0 / 1000;

        // 因为 Croller 无法滑动到 0，因此将 min 设为 1，将 max 设置为 26
        final int rangeSize = 25;
        final int min = 1;
        final int max = min + rangeSize;
        mBinding.virtualizerCroller.setMin(min);
        mBinding.virtualizerCroller.setMax(max);

        mBinding.virtualizerCroller.setProgress(min + (int) (percent * mBinding.virtualizerCroller.getMax()));

        mBinding.virtualizerCroller.setOnCrollerChangeListener(new OnCrollerChangeListener() {
            @Override
            public void onProgressChanged(Croller croller, int progress) {
                short strength = (short) ((progress - min) * (1000 / rangeSize));

                if (strength < 0) {
                    mEqualizerViewModel.setVirtualizerStrength((short) 0);
                } else if (strength > 1000) {
                    mEqualizerViewModel.setVirtualizerStrength((short) 1000);
                } else {
                    mEqualizerViewModel.setVirtualizerStrength(strength);
                }
            }

            @Override
            public void onStartTrackingTouch(Croller croller) {
                // ignore
            }

            @Override
            public void onStopTrackingTouch(Croller croller) {
                mEqualizerViewModel.applyChanges();
            }
        });
    }

    private static class PresetAdapter extends BaseAdapter {
        private final List<String> mAllPresetName;

        PresetAdapter(@NonNull List<String> allPresetName) {
            Preconditions.checkNotNull(allPresetName);

            mAllPresetName = new ArrayList<>(allPresetName);
        }

        @Override
        public int getCount() {
            return mAllPresetName.size();
        }

        @Override
        public Object getItem(int position) {
            return mAllPresetName.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            @SuppressLint("ViewHolder") View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.snow_ui_item_preset, parent, false);

            TextView tvText = itemView.findViewById(R.id.tvText);
            tvText.setText(mAllPresetName.get(position));

            return itemView;
        }
    }
}