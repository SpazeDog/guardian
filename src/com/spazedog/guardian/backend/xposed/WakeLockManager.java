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

package com.spazedog.guardian.backend.xposed;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import android.os.RemoteException;

import com.spazedog.guardian.backend.xposed.WakeLockService.ProcessLockInfo;
import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.utils.ReflectException;

public class WakeLockManager {
	private static WeakReference<WakeLockManager> oInstance = new WeakReference<WakeLockManager>(null);
	
	private volatile IRWakeLockService mService;
	
	private WakeLockManager() throws Exception {
		establishConnection();
	}
	
	private void establishConnection() throws Exception {
		try {
			ReflectClass service = ReflectClass.forClass(IRWakeLockService.class).bindInterface("user.guardian.wakelock");
			
			if (service != null) {
				mService = (IRWakeLockService) service.getReceiver();
				
			} else {
				throw new Exception("Could not bind to WakeLock Service");
			}
			
		} catch (ReflectException e) {
			throw new Exception(e);
		}
	}
	
	public static WakeLockManager getInstance() {
		synchronized(oInstance) {
			WakeLockManager instance = oInstance.get();
			
			if (instance == null) {
				try {
					oInstance = new WeakReference<WakeLockManager>( (instance = new WakeLockManager()) );
					
				} catch (Exception e) {
					oInstance = new WeakReference<WakeLockManager>( (instance = null) );
				}
			}
			
			return instance;
		}
	}
	
	public List<ProcessLockInfo> getProcessLockInfo() {
		try {
			return mService.srv_getProcessLockInfo();
		
		} catch (RemoteException e) {
			try {
				establishConnection();
				
			} catch (Exception ei) {}
			
		} catch (NullPointerException e) {}
		
		return new ArrayList<ProcessLockInfo>();
	}
}
