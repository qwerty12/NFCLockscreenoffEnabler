package pk.qwerty12.nfclockscreenoffenabler;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.SharedPreferences;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import de.robv.android.xposed.XC_MethodHook;

public class NFCLockScreenOffEnabler implements IXposedHookZygoteInit, IXposedHookLoadPackage
{

	//Thanks to Tungstwenty for the preferences code, which I have taken from his Keyboard42DictInjector and made a bad job of it
	private static final String MY_PACKAGE_NAME = NFCLockScreenOffEnabler.class.getPackage().getName();

	public static final String PREFS = "NFCModSettings";
	public static final String PREF_LOCKED = "On_Locked";

	private SharedPreferences prefs;

	/* -- */
	private static final String PACKAGE_NFC = "com.android.nfc";

	private int mScreenState = -1;

	// Taken from NfcService.java, Copyright (C) 2010 The Android Open Source Project, Licensed under the Apache License, Version 2.0
	// Screen state, used by mScreenState
	static final int SCREEN_STATE_UNKNOWN = 0;
	static final int SCREEN_STATE_OFF = 1;
	static final int SCREEN_STATE_ON_LOCKED = 2;
	static final int SCREEN_STATE_ON_UNLOCKED = 3;
	/* -- */

	// Thanks to rovo89 for his suggested improvements: http://forum.xda-developers.com/showpost.php?p=35790508&postcount=185
	@Override
	public void initZygote(StartupParam startupParam) throws Throwable
	{
		prefs = AndroidAppHelper.getSharedPreferencesForPackage(MY_PACKAGE_NAME, PREFS, Context.MODE_PRIVATE);
	}

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable
	{
		AndroidAppHelper.reloadSharedPreferencesIfNeeded(prefs);

		if (lpparam.packageName.equals(PACKAGE_NFC))
		{
			try
			{
				XposedHelpers.findAndHookMethod(PACKAGE_NFC + ".NfcService", lpparam.classLoader, "applyRouting", boolean.class,
					new XC_MethodHook()
					{
						@Override
						protected void beforeHookedMethod(MethodHookParam param) throws Throwable
						{
							synchronized (param.thisObject)   //Not sure if this is correct, but NfcService.java insists on having accesses to the mScreenState variable synchronized, so I'm doing the same here
							{
								if (prefs.getBoolean(PREF_LOCKED, true) && (Integer) XposedHelpers.callMethod(param.thisObject, "checkScreenState") != SCREEN_STATE_ON_LOCKED)
								{
									mScreenState = -1;
									return;
								}

								mScreenState = XposedHelpers.getIntField(param.thisObject, "mScreenState");
								XposedHelpers.setIntField(param.thisObject, "mScreenState", SCREEN_STATE_ON_UNLOCKED);
							}
						}

						@Override
						protected void afterHookedMethod(MethodHookParam param) throws Throwable
						{
							synchronized (param.thisObject)
							{
								if (mScreenState != -1)
									XposedHelpers.setIntField(param.thisObject, "mScreenState", mScreenState);
							}
						}

				    });
			}
			catch (Throwable t)
			{
				XposedBridge.log(t);
			}
		}
	}

}
