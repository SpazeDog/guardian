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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.spazedog.guardian.application.Controller;
import com.spazedog.guardian.application.Settings;

public class MonitorReceiver extends BroadcastReceiver {
	
	public static final String ACTION_SCHEDULE_SERVICE = "guardian.intent.action.SCHEDULE_SERVICE";
	
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
				
			} else if (action.equals(ACTION_SCHEDULE_SERVICE)) {
				Bundle bundle = intent.getBundleExtra("service_extras");
				
				Intent serviceIntent = new Intent(Intent.ACTION_RUN, null, context, MonitorService.class);
				if (bundle != null) {
					serviceIntent.putExtras(bundle);
				}
				
				context.startService(serviceIntent);
			}
		}
	}
}
