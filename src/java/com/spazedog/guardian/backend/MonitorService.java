/*
 * This file is part of the Guardian Project: https://github.com/spazedog/guardian
 *  
 * Copyright (c) 2015 Daniel Bergløv
 *
 * Guardian is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Guardian is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Guardian. If not, see <http://www.gnu.org/licenses/>
 */

package com.spazedog.guardian.backend;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.widget.Toast;

import com.spazedog.guardian.Common;
import com.spazedog.guardian.application.Controller;
import com.spazedog.guardian.application.Settings;
import com.spazedog.guardian.backend.PersistentService.PersistentServiceControl;
import com.spazedog.guardian.backend.ScheduledService.ScheduledServiceControl;
import com.spazedog.guardian.utils.AbstractHandler;

public abstract class MonitorService extends IntentService {
		
	protected static int generateRandom() {
		Random rand = new Random();
		int number = 0;
		
		for (int i=0; i < 10; i++) {
			/*
			 * number += [0-9] * 10^x
			 */
			number += Math.pow(10, i) * rand.nextInt(9);
		}
		
		return number;
	}
	
	protected static int NOTIFICATION_ID = generateRandom();
	
	public MonitorService(String name) {
		super(name);
		
		setIntentRedelivery(true);
	}
	
	public Controller getController() {
		return (Controller) getApplicationContext();
	}
	
	public Settings getSettings() {
		return getController().getSettings();
	}
	
	@Override
	protected void onHandleIntent(Intent intent) {
		Common.LOG.Debug(this, "--------------------------------------------------------");
		Common.LOG.Debug(this, "Starting process scanning at " + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).format(new Date()));
		
		Bundle extras = intent.getExtras();
		
		if (extras == null) {
			extras = new Bundle();
		}

		MonitorWorker worker = new MonitorWorker(getController(), extras);
		worker.start();
		
		intent.putExtras(extras);
		
		Common.LOG.Debug(this, "Process scanning ended at " + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).format(new Date()));
		Common.LOG.Debug(this, "========================================================");
	}
	
	public final static class MonitorServiceReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Controller controller = (Controller) context.getApplicationContext();
			Settings settings = controller.getSettings();

			if (action != null && controller.getSettings().isServiceEnabled()) {
				if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
					if (settings.isServiceEnabled()) {
						controller.startService();
					}
				}
			}
		}
	}
	
	public abstract static class MonitorServiceControl {
		
		private static class TimeoutHandler extends AbstractHandler<MonitorServiceControl> {
			public TimeoutHandler(MonitorServiceControl reference) {
				super(reference);
			}

			@Override
			public void handleMessage(Message msg) {
				MonitorServiceControl reference = getReference();
				Toast.makeText(reference.getController(), "The Service Request has timed out", Toast.LENGTH_LONG).show();
				reference.reset();
			}
		}
		
		/*
		 * This are service status codes that are sent via the internal message system to Activities and Fragments. 
		 * Status.PENDING means that a stop or start request has been sent to the service and Controller is now awaiting response from the service. 
		 * Status.STARTED and Status.STOPPED is the response that Controller received from the service and indicates it's current status. 
		 */
		public static class Status {
			public static final int STOPPED = -1;
			public static final int PENDING = 0;
			public static final int STARTED = 1;
		}
		
		public static interface IMonitorServiceListener {
			public void onServiceStateChanged(int state);
		}
		
		protected IMonitorServiceListener mListener;
		protected Controller mController;
		protected TimeoutHandler mTimeoutHandler;
		
		public static MonitorServiceControl getInstance(Controller controller, String identifier) {
			if ("persistent".equals(identifier)) {
				return new PersistentServiceControl(controller);
				
			} else {
				return new ScheduledServiceControl(controller);
			}
		}
		
		public MonitorServiceControl(Controller controller) {
			mController = controller;
			mTimeoutHandler = new TimeoutHandler(this);
		}
		
		public void setMonitorServiceListener(IMonitorServiceListener listener) {
			mListener = listener;
		}
		
		protected void alertStateChanged(int state) {
			if (mListener != null) {
				if (state == Status.PENDING) {
					mTimeoutHandler.sendEmptyMessageDelayed(0, 10000);
					
				} else {
					mTimeoutHandler.removeMessages(0);
				}
				
				mListener.onServiceStateChanged(state);
			}
		}
		
		public Controller getController() {
			return mController;
		}
		
		public Settings getSettings() {
			return mController.getSettings();
		}
		
		public abstract boolean start();
		public abstract boolean stop();
		public abstract void reset();
		public abstract int status();
		public abstract String identifier();
	}
}
