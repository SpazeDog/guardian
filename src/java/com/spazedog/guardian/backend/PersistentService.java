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

import java.text.DateFormat;
import java.util.Date;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;

import com.spazedog.guardian.ActivityLaunch;
import com.spazedog.guardian.R;
import com.spazedog.guardian.application.Controller;
import com.spazedog.guardian.backend.MonitorService.MonitorServiceControl.Status;

public final class PersistentService extends MonitorService {
	
	protected static final int PERSISTENT_ID = generateRandom();
	protected static final Object oLock = new Object();
	protected static int oStatus = Status.STOPPED;

	public PersistentService() {
		super("Guardian.PersistentService");
	}
	
	@Override
	protected void onHandleIntent(Intent intent) {
		if (intent.getExtras() == null) {
			intent.putExtras(new Bundle());
		}
		
		Controller controller = getController();
		PersistentServiceControl serviceControl = (PersistentServiceControl) controller.getServiceControl();
		
		PendingIntent pendingIntent = PendingIntent.getActivity(controller, 0, new Intent(controller, ActivityLaunch.class), 0);
		NotificationCompat.Builder builder = new NotificationCompat.Builder(controller)
			.setContentTitle("Guardian")
			.setContentText("Process monitoring is active")
			.setSmallIcon(R.mipmap.ic_launcher)
			.setContentIntent(pendingIntent)
			.setOngoing(true)
			.setOnlyAlertOnce(true);
		
		startForeground(PERSISTENT_ID, builder.build());
		
		serviceControl.alertStateChanged((oStatus = Status.STARTED));
		
		do {
			super.onHandleIntent(intent);
			
			if (oStatus == Status.STARTED) {
				Bundle bundle = intent.getExtras();
				int timeout = bundle.getInt("timeout", getSettings().getServiceInterval());
				long time = SystemClock.elapsedRealtime() + timeout;
				DateFormat dateFormat = android.text.format.DateFormat.getTimeFormat(controller);
				
				builder.setContentText("Last Scan: " + dateFormat.format(new Date()));
				startForeground(PERSISTENT_ID, builder.build());
				
				while (oStatus == Status.STARTED && timeout > 0) {
					synchronized (oLock) {
						try {
							oLock.wait( timeout ); break;
							
						} catch (Throwable e) {
							timeout = (int) (time - SystemClock.elapsedRealtime());
						}
					}	
				}
			}
			
		} while (oStatus == Status.STARTED);
		
		stopForeground(true);
		
		serviceControl.alertStateChanged((oStatus = Status.STOPPED));
	}

	public static final class PersistentServiceControl extends MonitorServiceControl {
		
		public PersistentServiceControl(Controller controller) {
			super(controller);
		}

		@Override
		public boolean start() {
			if (status() == Status.STOPPED) {
				alertStateChanged((oStatus = Status.PENDING));
				getController().startService(new Intent(Intent.ACTION_RUN, null, getController(), PersistentService.class));
				
				return true;
			}
			
			return false;
		}

		@Override
		public boolean stop() {
			if (status() == Status.STARTED) {
				alertStateChanged((oStatus = Status.PENDING));
				
				synchronized (oLock) {
					oLock.notifyAll();
				}
				
				return true;
			}
			
			return false;
		}
		
		@Override
		public void reset() {
			synchronized (oLock) {
				oLock.notifyAll();
			}
			
			getController().stopService(new Intent(Intent.ACTION_RUN, null, getController(), PersistentService.class));
			alertStateChanged((oStatus = Status.STOPPED));
		}

		@Override
		public int status() {
			return oStatus;
		}
		
		@Override
		public String identifier() {
			return "persistent";
		}
	}
}
