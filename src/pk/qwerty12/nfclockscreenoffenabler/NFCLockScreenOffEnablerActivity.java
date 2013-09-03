package pk.qwerty12.nfclockscreenoffenabler;

import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.CheckBox;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class NFCLockScreenOffEnablerActivity extends Activity {

	private RadioButton mRadio1 = null;
	private RadioGroup mRadioGroup = null;
	private CheckBox mEnableTagLostCheckBox = null;
	private CheckBox mEnableTagLostSoundCheckBox = null;
	
	@SuppressWarnings("deprecation")
	@SuppressLint("WorldReadableFiles")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_nfclock_screen_off_enabler);
		
		getViews();

		final SharedPreferences prefs = getSharedPreferences(Common.PREFS, Context.MODE_WORLD_READABLE);
		if (!prefs.getBoolean(Common.PREF_LOCKED, true))
			mRadio1.setChecked(true);

		mRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
		    public void onCheckedChanged(RadioGroup rGroup, int checkedId) {
				Editor prefsEditor = prefs.edit();
				prefsEditor.putBoolean(Common.PREF_LOCKED, checkedId == R.id.radio0);
				prefsEditor.commit();
				
				emitSettingsChanged();
		    }
		});

		mEnableTagLostCheckBox.setChecked(prefs.getBoolean(Common.PREF_TAGLOST, true));
		mEnableTagLostCheckBox.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				Editor prefsEditor = prefs.edit();
				prefsEditor.putBoolean(Common.PREF_TAGLOST, isChecked);
				prefsEditor.commit();
				
				emitSettingsChanged();
			}
		});
		
		mEnableTagLostSoundCheckBox.setChecked(prefs.getBoolean(Common.PLAY_TAG_LOST_SOUND, true));
		mEnableTagLostSoundCheckBox.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				Editor prefsEditor = prefs.edit();
				prefsEditor.putBoolean(Common.PLAY_TAG_LOST_SOUND, isChecked);
				prefsEditor.commit();
				
				emitSettingsChanged();
			}
		});
	}

	private void getViews() {
		mEnableTagLostCheckBox = (CheckBox) findViewById(R.id.cbEnableTagLost);
		mEnableTagLostSoundCheckBox = (CheckBox) findViewById(R.id.play_tag_lost_checkbox);
		mRadioGroup = (RadioGroup) findViewById(R.id.radioGroup1);
		mRadio1 = ((RadioButton) findViewById(R.id.radio1));
	}

	protected void emitSettingsChanged() {
		Intent i = new Intent(Common.SETTINGS_UPDATED_INTENT);
		sendBroadcast(i);
	}

}
