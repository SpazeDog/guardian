package com.spazedog.guardian;

public class Constants {
	
	/*
	 * Print debug messages from the monitor service
	 */
	public static final boolean ENABLE_DEBUG = true;
	
	/*
	 * Test the alert/action system by targeting everything above 0 in CPU usage
	 */
	public static final boolean ENABLE_REPORT_TESTING = false;
	
	/*
	 * The problem with Xposed Services, is that they run in the Android 
	 * main process. If the app is updated, the Service is not updated along with it, 
	 * until the device has been rebooted. Any changes in things like Parcel order and Binder 
	 * transactions, could make the app crash when trying to communicate with the currently running service. 
	 * These ID's allows us to compare the running service with what we expect to be the running service version. 
	 */
	public static final int SERVICE_BINDER_ID = 1;
	public static final int SERVICE_PARCEL_ID = 1;
}
