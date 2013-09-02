package pk.qwerty12.nfclockscreenoffenabler;

import java.util.List;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.nfc.NfcAdapter;
import android.util.Log;
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
	public static final String PREF_TAGLOST = "TagLostDetecting";
	private SharedPreferences prefs;

	/* -- */
	private static final String PACKAGE_NFC = "com.android.nfc";

	// Taken from NfcService.java, Copyright (C) 2010 The Android Open Source Project, Licensed under the Apache License, Version 2.0
	// Screen state, used by mScreenState
	//private static final int SCREEN_STATE_OFF = 1;
	private static final int SCREEN_STATE_ON_LOCKED = 2;
	private static final int SCREEN_STATE_ON_UNLOCKED = 3;

	//qlg 2013-08-11
	//Hook for NfcNativeTag$PresenceCheckWatchdog.run()
	class PresenceCheckWatchdogRunHook extends XC_MethodHook
	{
		@Override
		protected void afterHookedMethod(MethodHookParam param) throws Throwable
		{
			//Change to preference only takes effect when this is called here
			AndroidAppHelper.reloadSharedPreferencesIfNeeded(prefs);

			if (!prefs.getBoolean(PREF_TAGLOST, true))
				return;

			//broadcast tag lost message
			try {  
				Class<?> activityManagerNative = Class.forName("android.app.ActivityManagerNative");  

				if (activityManagerNative != null){
					Object am = activityManagerNative.getMethod("getDefault").invoke(activityManagerNative);  

					if (am != null)
						am.getClass().getMethod("resumeAppSwitches").invoke(am);
				}
			}catch (Exception e) {  
				e.printStackTrace();  
			}  

			Context context = (Context) XposedHelpers.getAdditionalInstanceField(XposedHelpers.getSurroundingThis(param.thisObject), "mContext");

			if (context == null){
				Log.d("PresenceCheckWatchdogRunHook",  "step-4 context == null");
				return;
			}

			Log.d("PresenceCheckWatchdogRunHook",  "step-4 context != null");
			try{
				byte[] uId = (byte[]) XposedHelpers.callMethod(XposedHelpers.getSurroundingThis(param.thisObject), "getUid");
				Intent intentToStart = new Intent();
				intentToStart.putExtra(NfcAdapter.EXTRA_ID, uId);
				intentToStart.setData(null);
				intentToStart.setType(null);
				intentToStart.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
				intentToStart.setAction("android.nfc.action.TAG_LOST");

				PackageManager packageManager = context.getPackageManager();

				if (packageManager != null)
				{
					List<ResolveInfo> activities = packageManager.queryIntentActivities(intentToStart, 0);
					if (activities.size() > 0) {
						Log.d("PresenceCheckWatchdogRunHook", 
								String.format("startActivity - android.nfc.action.TAG_LOST(%x%x%x%x)", uId[0], uId[1], uId[2], uId[3]));
						context.startActivity(intentToStart);
					}
					else{
						Log.d("PresenceCheckWatchdogRunHook", 
								String.format("activities.size() <= 0 (%x%x%x%x)", uId[0], uId[1], uId[2], uId[3]));
					}
				}
			}catch (Exception e) {  
				e.printStackTrace();  
			}  
		}
	}

	//qlg 2013-08-11
	//Hook for NfcService.onRemoteEndpointDiscovered(TagEndpoint tag)
	class NfcServiceOnRemoteEndpointDiscoveredHook extends XC_MethodHook
	{
		@Override
		protected void beforeHookedMethod(MethodHookParam param) throws Throwable
		{
			try{
				//Change to preference only takes effect when this is called here
				AndroidAppHelper.reloadSharedPreferencesIfNeeded(prefs);

				if (!prefs.getBoolean(PREF_TAGLOST, true))
					return;

				Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
				XposedHelpers.setAdditionalInstanceField(param.args[0], "mContext", context);
				if (context != null)
					Log.d("NfcServiceOnRemoteEndpointDiscoveredHook","setAdditionalInstanceField - mContext != null");
				else
					Log.d("NfcServiceOnRemoteEndpointDiscoveredHook","setAdditionalInstanceField - mContext == null");
			}catch (Exception e) {  
				e.printStackTrace();  
			}  
		}
	}

	// Thanks to rovo89 for his suggested improvements: http://forum.xda-developers.com/showpost.php?p=35790508&postcount=185
	@Override
	public void initZygote(StartupParam startupParam) throws Throwable
	{
		prefs = AndroidAppHelper.getSharedPreferencesForPackage(MY_PACKAGE_NAME, PREFS, Context.MODE_PRIVATE);
	}

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable
	{
		if (lpparam.packageName.equals(PACKAGE_NFC))
		{
			try
			{
				//qlg 2013-08-12 , Nfc module of some kinds of ROMs may call checkScreenState in applyRouting and update mScreenState,
				//so we have to hook checkScreenState and modify the return value
				XposedHelpers.findAndHookMethod(PACKAGE_NFC + ".NfcService", lpparam.classLoader, "checkScreenState",
						new XC_MethodHook()
				{
					@Override
					protected void beforeHookedMethod(MethodHookParam param) throws Throwable
					{
						try{
							Boolean NeedScreenOnState = (Boolean)XposedHelpers.getAdditionalInstanceField(param.thisObject, "NeedScreenOnState") ;
							if (NeedScreenOnState == null)
								return;

							if (NeedScreenOnState == false)
								return;

							param.setResult(SCREEN_STATE_ON_UNLOCKED);
						}catch (Exception e) {  
							e.printStackTrace();  
						}  
					}
				});

				XposedHelpers.findAndHookMethod(PACKAGE_NFC + ".NfcService", lpparam.classLoader, "applyRouting", boolean.class,
						new XC_MethodHook()
				{
					@Override
					protected void beforeHookedMethod(MethodHookParam param) throws Throwable
					{
						try{
							//Change to preference only takes effect when this is called here
							AndroidAppHelper.reloadSharedPreferencesIfNeeded(prefs);

							final int currScreenState = (Integer) XposedHelpers.callMethod(param.thisObject, "checkScreenState");
							//We also don't need to run if the screen is already on, or if the user has chosen to enable NFC on the lockscreen only and the phone is not locked
							if ((currScreenState == SCREEN_STATE_ON_UNLOCKED) || (prefs.getBoolean(PREF_LOCKED, true) && currScreenState != SCREEN_STATE_ON_LOCKED))
							{
								XposedHelpers.setAdditionalInstanceField(param.thisObject, "mOrigScreenState", -1);
								return;
							}

							//qlg 2013-08-12
							//we are in applyRouting, set the flag NeedScreenOnState to true
							XposedHelpers.setAdditionalInstanceField(param.thisObject, "NeedScreenOnState", true);

							synchronized (param.thisObject)   //Not sure if this is correct, but NfcService.java insists on having accesses to the mScreenState variable synchronized, so I'm doing the same here
							{
								XposedHelpers.setAdditionalInstanceField(param.thisObject, "mOrigScreenState", XposedHelpers.getIntField(param.thisObject, "mScreenState"));
								XposedHelpers.setIntField(param.thisObject, "mScreenState", SCREEN_STATE_ON_UNLOCKED);
							}
						}catch (Exception e) {  
							e.printStackTrace();  
						}  
					}

					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable
					{
						try{
							//qlg 2013-08-12
							//exit from applyRouting, set the flag NeedScreenOnState to false
							XposedHelpers.setAdditionalInstanceField(param.thisObject, "NeedScreenOnState", false);

							final int mOrigScreenState = (Integer) XposedHelpers.getAdditionalInstanceField(param.thisObject, "mOrigScreenState");
							if (mOrigScreenState == -1)
								return;

							synchronized (param.thisObject)
							{
								//Restore original mScreenState value after applyRouting has run
								XposedHelpers.setIntField(param.thisObject, "mScreenState", mOrigScreenState);
							}
						}catch (Exception e) {  
							e.printStackTrace();  
						}  
					}
				});
			}
			catch (Throwable t) { XposedBridge.log(t); }


			//qlg 2013-08-09 added for Nfc tag lost message
			try{
				XposedHelpers.findAndHookMethod(PACKAGE_NFC + ".dhimpl.NativeNfcTag$PresenceCheckWatchdog", lpparam.classLoader, "run", 
						new PresenceCheckWatchdogRunHook());
			}
			catch (Throwable t) { XposedBridge.log(t); }

			try{
				XposedHelpers.findAndHookMethod(PACKAGE_NFC +".nxp.NativeNfcTag$PresenceCheckWatchdog", lpparam.classLoader, "run", 
						new PresenceCheckWatchdogRunHook());
			}
			catch (Throwable t) { XposedBridge.log(t); }

			try{
				XposedHelpers.findAndHookMethod(PACKAGE_NFC + ".NfcService", lpparam.classLoader, "onRemoteEndpointDiscovered",  
						PACKAGE_NFC + ".DeviceHost$TagEndpoint",
						new NfcServiceOnRemoteEndpointDiscoveredHook());
			}
			catch (Throwable t) { XposedBridge.log(t); }

			//			try{
			//				//public TransceiveResult transceive(int nativeHandle, byte[] data, boolean raw)
			//				XposedHelpers.findAndHookMethod(PACKAGE_NFC + ".NfcService$TagService", lpparam.classLoader, "transceive",  
			//						int.class, byte[].class, boolean.class,
			//						new TagServiceTransceiveHook());
			//			}
			//			catch (Throwable t) { XposedBridge.log(t); }

		}
	}

}
