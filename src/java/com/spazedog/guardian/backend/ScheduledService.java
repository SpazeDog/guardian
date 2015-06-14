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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

import com.spazedog.guardian.Common;
import com.spazedog.guardian.application.Controller;
import com.spazedog.guardian.backend.MonitorService.MonitorServiceControl.Status;

public final class ScheduledService extends MonitorService {
	
	protected static int oStatus = -2;

	public ScheduledService() {
		super("Guardian.ScheduledService");
	}
	
	@Override
	protected void onHandleIntent(Intent intent) {
		Common.LOG.Debug(this, "Running Service Intent");
		
		Bundle bundle = intent.getExtras();
		Controller controller = getController();
		ScheduledServiceControl serviceControl = (ScheduledServiceControl) controller.getServiceControl();
		boolean stateChange = bundle.getBoolean("stateChange", false);
		int state = bundle.getInt("state", Status.STARTED);
		
		oStatus = state;
		
		if (state == Status.STARTED) {
			super.onHandleIntent(intent);
			
			bundle = intent.getExtras();
			bundle.remove("stateChange");
			bundle.remove("state");
			
			int timeout = bundle.getInt("timeout", getSettings().getServiceInterval());
			
			serviceControl.setScheduler(timeout, bundle);
			
		} else {
			serviceControl.removeScheduler();
		}
		
		if (stateChange) {
			serviceControl.alertStateChanged(state);
		}
	}
	
	public static final class ScheduledServiceReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			Common.LOG.Debug(this, "Receiving Service Start Request");
			
			String action = intent.getAction();
			
			if (action.equals("guardian.intent.action.SCHEDULE_SERVICE")) {
				Bundle bundle = intent.getBundleExtra("service_extras");
				Intent serviceIntent = new Intent(Intent.ACTION_RUN, null, context, ScheduledService.class);
				
				if (bundle != null) {
					serviceIntent.putExtras(bundle);
					
				} else {
					serviceIntent.putExtras(new Bundle());
				}
				
				context.startService(serviceIntent);
			}
		}
	}

	public static final class ScheduledServiceControl extends MonitorServiceControl {
		
		private boolean mRejectScheduler = false;
		
		public ScheduledServiceControl(Controller controller) {
			super(controller);
		}

		@Override
		public boolean start() {
			if (status() == Status.STOPPED) {
				Bundle extras = new Bundle();
				extras.putBoolean("stateChange", true);
				extras.putInt("state", Status.STARTED);
				
				mRejectScheduler = false;
				
				alertStateChanged((oStatus = Status.PENDING));
				setScheduler(500, extras);
				
				return true;
			}
			
			return false;
		}

		@Override
		public boolean stop() {
			if (status() == Status.STARTED) {
				Bundle extras = new Bundle();
				extras.putBoolean("stateChange", true);
				extras.putInt("state", Status.STOPPED);
				
				mRejectScheduler = false;
				
				alertStateChanged((oStatus = Status.PENDING));
				setScheduler(500, extras);
				
				return true;
			}
			
			return false;
		}
		
		@Override
		public void reset() {
			mRejectScheduler = true;
			removeScheduler();
			alertStateChanged((oStatus = Status.STOPPED));
		}

		@Override
		public int status() {
			if (oStatus == -2) {
				oStatus = hasScheduler() ? Status.STARTED : Status.STOPPED;
			}
			
			return oStatus;
		}
		
		@Override
		public String identifier() {
			return "scheduled";
		}
		
		protected Intent getSchedulerIntent() {
			return new Intent("guardian.intent.action.SCHEDULE_SERVICE", null, getController(), ScheduledServiceReceiver.class);
		}
		
		protected boolean hasScheduler() {
			return PendingIntent.getBroadcast(getController(), 1, getSchedulerIntent(), PendingIntent.FLAG_NO_CREATE) != null;
		}
		
		protected void removeScheduler() {
			PendingIntent pendingIntent = PendingIntent.getBroadcast(getController(), 1, getSchedulerIntent(), PendingIntent.FLAG_NO_CREATE);
			
			if (pendingIntent != null) {
				Common.LOG.Debug(this, "Removing service scheduler");
				
				((AlarmManager) getController().getSystemService(Context.ALARM_SERVICE)).cancel(pendingIntent);
			}
		}
		
		protected void setScheduler(Integer timeout, Bundle extras) {
			if (!mRejectScheduler) {
				Common.LOG.Debug(this, "Setting new service scheduler, Timeout = " + timeout);
				
				Intent intent = getSchedulerIntent();
				
				if (extras != null) {
					intent.putExtra("service_extras", extras);
				}
				
				Controller controller = getController();
				AlarmManager alarmManager = (AlarmManager) controller.getSystemService(Context.ALARM_SERVICE);
				PendingIntent pendingIntent = PendingIntent.getBroadcast(controller, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
				
				alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime()+timeout, pendingIntent);
			}
		}
	}
}
