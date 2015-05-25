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

import android.os.Build;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.utils.ReflectException;

import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;

public class Injector implements IXposedHookZygoteInit {

	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				ReflectClass.forName("android.app.ActivityThread")
				.inject("systemMain", new XC_MethodHook() {
					@Override
					protected final void afterHookedMethod(final MethodHookParam param) {
						try {
							ReflectClass.forName("com.android.server.am.ActivityManagerService", Thread.currentThread().getContextClassLoader())
							.inject(new XC_MethodHook() {
								@Override
								protected final void afterHookedMethod(final MethodHookParam param) {
									bootstrapSystem(param.thisObject);
								}
							});
							
						} catch (ReflectException e) {}
					}
				});
				
			} else {
				ReflectClass.forName("com.android.server.am.ActivityManagerService")
				.inject("startRunning", new XC_MethodHook() {
					@Override
					protected final void afterHookedMethod(final MethodHookParam param) {
						bootstrapSystem(param.thisObject);
					}
				});
			}
			
		} catch (ReflectException e) {}
	}
	
	private void bootstrapSystem(Object am) {
		WakeLockService.init();
	}
}
