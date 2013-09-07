package pk.qwerty12.nfclockscreenoffenabler;

import java.util.List;

import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import android.app.AndroidAppHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.XModuleResources;
import android.media.SoundPool;
import android.nfc.NfcAdapter;
import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import de.robv.android.xposed.XC_MethodHook;

public class NFCLockScreenOffEnabler implements IXposedHookZygoteInit, IXposedHookLoadPackage
{
	//Thanks to Tungstwenty for the preferences code, which I have taken from his Keyboard42DictInjector and made a bad job of it
	private static final String MY_PACKAGE_NAME = NFCLockScreenOffEnabler.class.getPackage().getName();
	private String MODULE_PATH;
	
	private SharedPreferences prefs;
	private Context mContext = null;
	
	private XModuleResources modRes = null;
	private SoundPool mSoundPool = null;
	private Object nfcServiceObject = null;

	// Taken from NfcService.java, Copyright (C) 2010 The Android Open Source Project, Licensed under the Apache License, Version 2.0
	// Screen state, used by mScreenState
	//private static final int SCREEN_STATE_OFF = 1;
	private static final int SCREEN_STATE_ON_LOCKED = 2;
	private static final int SCREEN_STATE_ON_UNLOCKED = 3;
	
	int mTagLostSound;
	
	//Hook for NfcNativeTag$PresenceCheckWatchdog.run()
	class PresenceCheckWatchdogRunHook extends XC_MethodHook
	{
		@Override
		protected void afterHookedMethod(MethodHookParam param) throws Throwable
		{
			if (!prefs.getBoolean(Common.PREF_TAGLOST, true))
				return;

			//broadcast tag lost message
			try {  
				Class<?> activityManagerNative = Class.forName("android.app.ActivityManagerNative");  

				if (activityManagerNative != null){
					Object am = activityManagerNative.getMethod("getDefault").invoke(activityManagerNative);  

					if (am != null)
						am.getClass().getMethod("resumeAppSwitches").invoke(am);
				}
			} catch (Exception e) {  
				e.printStackTrace();  
			}  

			Context context = (Context) XposedHelpers.getAdditionalInstanceField(XposedHelpers.getSurroundingThis(param.thisObject), "mContext");

			if (context == null){
				Log.d("PresenceCheckWatchdogRunHook",  "step-4 context == null");
				return;
			}

			Log.d("PresenceCheckWatchdogRunHook",  "step-4 context != null");
			try {
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
				
				playTagLostSound();
			} catch (Exception e) {  
				e.printStackTrace();  
			}  
		}
	}

	//Hook for NfcService.onRemoteEndpointDiscovered(TagEndpoint tag)
	class NfcServiceOnRemoteEndpointDiscoveredHook extends XC_MethodHook
	{
		@Override
		protected void beforeHookedMethod(MethodHookParam param) throws Throwable
		{
			try {
				if (!prefs.getBoolean(Common.PREF_TAGLOST, true))
					return;

				Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
				XposedHelpers.setAdditionalInstanceField(param.args[0], "mContext", context);
				if (context != null)
					Log.d("NfcServiceOnRemoteEndpointDiscoveredHook","setAdditionalInstanceField - mContext != null");
				else
					Log.d("NfcServiceOnRemoteEndpointDiscoveredHook","setAdditionalInstanceField - mContext == null");
			} catch (Exception e) {  
				e.printStackTrace();  
			}  
		}
	}

	// Thanks to rovo89 for his suggested improvements: http://forum.xda-developers.com/showpost.php?p=35790508&postcount=185
	@Override
	public void initZygote(StartupParam startupParam) throws Throwable
	{
		prefs = AndroidAppHelper.getSharedPreferencesForPackage(MY_PACKAGE_NAME, Common.PREFS, Context.MODE_PRIVATE);
		MODULE_PATH = startupParam.modulePath;
	}

	public void playTagLostSound() {
		if (!prefs.getBoolean(Common.PLAY_TAG_LOST_SOUND, true))
			return;
		
		synchronized (nfcServiceObject) {
			if (mSoundPool == null) {
				Log.w("NfcService", "Not playing sound when NFC is disabled");
				return;
			}
			
			mSoundPool.play(mTagLostSound, 1.0f, 1.0f, 0, 0, 1.0f);
		}
	}

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable
	{
		if (lpparam.packageName.equals(Common.PACKAGE_NFC))
		{
			modRes = XModuleResources.createInstance(MODULE_PATH, null);
			
			try {
				Class<?> NfcService = findClass(Common.PACKAGE_NFC + ".NfcService", lpparam.classLoader);
				
				// Don't reload settings on every call, that can cause slowdowns.
				// This intent is fired from NFCLockScreenOffEnablerActivity when
				// any of the parameters change.
				XposedBridge.hookAllConstructors(NfcService, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param)
							throws Throwable {
						nfcServiceObject = param.thisObject;
						mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
						mContext.registerReceiver(new BroadcastReceiver() {
							@Override
							public void onReceive(Context context, Intent intent) {
								XposedBridge.log(MY_PACKAGE_NAME + ": " + "Settings updated, reloading...");
								AndroidAppHelper.reloadSharedPreferencesIfNeeded(prefs);
							}
						}, new IntentFilter(Common.SETTINGS_UPDATED_INTENT));
					}
				});
				
				findAndHookMethod(NfcService, "initSoundPool", new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param)
							throws Throwable {
						mSoundPool = (SoundPool) XposedHelpers.getObjectField(param.thisObject, "mSoundPool");
						synchronized (param.thisObject) {
							if (mSoundPool != null)
								mTagLostSound = mSoundPool.load(modRes.openRawResourceFd(R.raw.tag_lost), 1);
						}
					}
				});
				
				// Nfc module of some kinds of ROMs may call checkScreenState in applyRouting
				// and update mScreenState, so we have to hook checkScreenState and modify
				// the return value
				findAndHookMethod(NfcService, "checkScreenState", new XC_MethodHook()
				{
					@Override
					protected void beforeHookedMethod(MethodHookParam param) throws Throwable
					{
						try {
							Boolean NeedScreenOnState = (Boolean)XposedHelpers.getAdditionalInstanceField(param.thisObject, "NeedScreenOnState") ;
							if (NeedScreenOnState == null)
								return;

							if (NeedScreenOnState == false)
								return;

							param.setResult(SCREEN_STATE_ON_UNLOCKED);
						} catch (Exception e) {  
							e.printStackTrace();  
						}  
					}
				});

				findAndHookMethod(NfcService, "applyRouting", boolean.class, new XC_MethodHook()
				{
					@Override
					protected void beforeHookedMethod(MethodHookParam param) throws Throwable
					{
						try {
							final int currScreenState = (Integer) XposedHelpers.callMethod(param.thisObject, "checkScreenState");
							// We also don't need to run if the screen is already on, or if the user
							// has chosen to enable NFC on the lockscreen only and the phone is not locked
							if ((currScreenState == SCREEN_STATE_ON_UNLOCKED)
									|| (prefs.getBoolean(Common.PREF_LOCKED, true)
											&& currScreenState != SCREEN_STATE_ON_LOCKED))
							{
								XposedHelpers.setAdditionalInstanceField(param.thisObject, "mOrigScreenState", -1);
								return;
							}

							// we are in applyRouting, set the flag NeedScreenOnState to true
							XposedHelpers.setAdditionalInstanceField(param.thisObject, "NeedScreenOnState", true);

							synchronized (param.thisObject) // Not sure if this is correct, but NfcService.java insists on having accesses to the mScreenState variable synchronized, so I'm doing the same here
							{
								XposedHelpers.setAdditionalInstanceField(param.thisObject, "mOrigScreenState", XposedHelpers.getIntField(param.thisObject, "mScreenState"));
								XposedHelpers.setIntField(param.thisObject, "mScreenState", SCREEN_STATE_ON_UNLOCKED);
							}
						} catch (Exception e) {  
							e.printStackTrace();  
						}  
					}

					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable
					{
						try {
							// exit from applyRouting, set the flag NeedScreenOnState to false
							XposedHelpers.setAdditionalInstanceField(param.thisObject, "NeedScreenOnState", false);

							final int mOrigScreenState = (Integer) XposedHelpers.getAdditionalInstanceField(param.thisObject, "mOrigScreenState");
							if (mOrigScreenState == -1)
								return;

							synchronized (param.thisObject)
							{
								// Restore original mScreenState value after applyRouting has run
								XposedHelpers.setIntField(param.thisObject, "mScreenState", mOrigScreenState);
							}
						} catch (Exception e) {  
							e.printStackTrace();  
						}  
					}
				});
				
				XposedHelpers.findAndHookMethod(NfcService, "onRemoteEndpointDiscovered",  
						Common.PACKAGE_NFC + ".DeviceHost$TagEndpoint",
						new NfcServiceOnRemoteEndpointDiscoveredHook());
				
			} catch (ClassNotFoundError e) {
				XposedBridge.log("Not hooking class .NfcService");
			}

			try {
				Class<?> PresenceCheckWatchDog = findClass(Common.PACKAGE_NFC + ".dhimpl.NativeNfcTag$PresenceCheckWatchdog", lpparam.classLoader);
				XposedHelpers.findAndHookMethod(PresenceCheckWatchDog, "run", new PresenceCheckWatchdogRunHook());
			} catch (ClassNotFoundError e) {
				XposedBridge.log("Not hooking class .dhimpl.NativeNfcTag$PresenceCheckWatchdog");
			}

			try {
				Class<?> PresenceCheckWatchdog = findClass(Common.PACKAGE_NFC +".nxp.NativeNfcTag$PresenceCheckWatchdog", lpparam.classLoader);
				XposedHelpers.findAndHookMethod(PresenceCheckWatchdog, "run", new PresenceCheckWatchdogRunHook());
			} catch (ClassNotFoundError e) {
				XposedBridge.log("Not hooking class .nxp.NativeNfcTag$PresenceCheckWatchdog");
			}

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
