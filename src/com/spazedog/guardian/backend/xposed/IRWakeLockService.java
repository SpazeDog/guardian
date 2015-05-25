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

import java.util.ArrayList;
import java.util.List;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import com.spazedog.guardian.backend.xposed.WakeLockService.ProcessLockInfo;
import com.spazedog.guardian.backend.xposed.WakeLockService.WakeLockInfo;

public interface IRWakeLockService extends IInterface {
	static final String DESCRIPTOR = IRWakeLockService.class.getName();
	
	static final int TRANSACTION_getProcessLockInfo = (IBinder.FIRST_CALL_TRANSACTION);
	
	public List<ProcessLockInfo> srv_getProcessLockInfo() throws RemoteException;
	
	public static abstract class Stub extends Binder implements IRWakeLockService {
		public Stub() {
			attachInterface(this, DESCRIPTOR);
		}
		
		public static IRWakeLockService asInterface(IBinder binder) {
			if (binder != null) {
				IInterface localInterface = binder.queryLocalInterface(DESCRIPTOR);
				
				if (localInterface != null && localInterface instanceof IRWakeLockService) {
					return (IRWakeLockService) localInterface;
					
				} else {
					return new Proxy(binder);
				}
			}
			
			return null;
		}
		
		@Override
		public IBinder asBinder() {
			return this;
		}
		
		@Override 
		public boolean onTransact(int type, Parcel args, Parcel caller, int flags) throws RemoteException {
			if (type == INTERFACE_TRANSACTION) {
				caller.writeString(DESCRIPTOR);
				
			} else if (type >= FIRST_CALL_TRANSACTION && type <= LAST_CALL_TRANSACTION) {
				args.enforceInterface(DESCRIPTOR);
				
				if ((flags & IBinder.FLAG_ONEWAY) == 0 && caller != null) {
					caller.writeNoException();
				}
				
				switch (type) {
					case TRANSACTION_getProcessLockInfo: {
						caller.writeTypedList(srv_getProcessLockInfo());
					}
				}
				
			} else {
				return false;
			}
			
			return true;
		}
	}
	
	public static class Proxy implements IRWakeLockService {
		
		private IBinder mBinder;
		
		public Proxy(IBinder binder) {
			mBinder = binder;
		}

		@Override
		public IBinder asBinder() {
			return mBinder;
		}
		
		@Override
		public List<ProcessLockInfo> srv_getProcessLockInfo() throws RemoteException {
			Parcel args = Parcel.obtain();
			Parcel callee = Parcel.obtain();
			List<ProcessLockInfo> lockList = new ArrayList<ProcessLockInfo>();
			
			try {
				args.writeInterfaceToken(DESCRIPTOR);
				mBinder.transact(Stub.TRANSACTION_getProcessLockInfo, args, callee, 0);
				callee.readTypedList(lockList, ProcessLockInfo.CREATOR);
				
			} finally {
				args.recycle();
				callee.recycle();
			}
			
			return lockList;
		}
	}
}
