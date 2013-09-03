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

	@SuppressWarnings("deprecation")
	@SuppressLint("WorldReadableFiles")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_nfclock_screen_off_enabler);

		final SharedPreferences prefs = getSharedPreferences(Common.PREFS, Context.MODE_WORLD_READABLE);
		if (!prefs.getBoolean(Common.PREF_LOCKED, true))
			((RadioButton) findViewById(R.id.radio1)).setChecked(true);

		((RadioGroup)findViewById(R.id.radioGroup1)).setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener()
		{
		    public void onCheckedChanged(RadioGroup rGroup, int checkedId)
		    {
		    	Editor prefsEditor = prefs.edit();
		    	prefsEditor.putBoolean(Common.PREF_LOCKED, checkedId == R.id.radio0);
		    	prefsEditor.commit();
		    	
		    	emitSettingsChanged();
		    }
		});

		//qlg 2013-08-09
		((CheckBox) findViewById(R.id.cbEnableTagLost)).setChecked(prefs.getBoolean(Common.PREF_TAGLOST, true));
		
		((CheckBox) findViewById(R.id.cbEnableTagLost)).setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener()
		{

			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
		    	Editor prefsEditor = prefs.edit();
		    	prefsEditor.putBoolean(Common.PREF_TAGLOST, isChecked);
		    	prefsEditor.commit();
		    	
		    	emitSettingsChanged();
			}
			
		});

	}

	protected void emitSettingsChanged() {
		Intent i = new Intent(Common.SETTINGS_UPDATED_INTENT);
		sendBroadcast(i);
	}

}
