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

package com.spazedog.guardian.utils;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractThread<T> extends Thread {
	
	private WeakReference<T> AbstractThread_mReference;
	
	private Boolean AbstractThread_mInterrupted = false;
	
	private Set<String> AbstractThread_mLocks = new HashSet<String>();
	
	public AbstractThread(T reference) {
		AbstractThread_mReference = new WeakReference<T>(reference);
	}
	
	public final T getReference() {
		return AbstractThread_mReference.get();
	}
	
	@Override
	public void run() {
		onPrepare();
		
		while (!isInterrupted()) {
			onRun();
			
			if (isLocked() && !isInterrupted()) {
				synchronized(this) {
					do {
						try {
							wait(0);
						
						} catch (Throwable e) {}
						 
					} while (isLocked() && !isInterrupted());
				}
			}
		}
		
		onTerminate();
	}
	
	public void onPrepare() {}
	public void onRun() {}
	public void onTerminate() {}
	
	@Override
	public void interrupt() {
		AbstractThread_mInterrupted = true;
		 
		super.interrupt();
	}
	
	@Override
	public boolean isInterrupted() {
		return AbstractThread_mInterrupted || super.isInterrupted();
	}
	
	public boolean isLocked() {
		return AbstractThread_mLocks.size() > 0;
	}
	
	public boolean isLocked(String id) {
		return AbstractThread_mLocks.contains(id);
	}
	
	public boolean lock(String id) {
		return AbstractThread_mLocks.add(id);
	}
	
	public boolean release() {
		boolean changed = AbstractThread_mLocks.size() > 0;
		AbstractThread_mLocks.clear();
		
		synchronized(this) {
			notifyAll();
		}
		
		return changed;
	}
	
	public boolean release(String id) {
		boolean changed = AbstractThread_mLocks.remove(id);
		
		synchronized(this) {
			notifyAll();
		}
		
		return changed;
	}
}
