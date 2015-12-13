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

import android.content.Context;

import com.spazedog.lib.reflecttools.bridge.InitBridge;

public class Injector extends InitBridge {
	
	public static final void initialize() throws Throwable {
		InitBridge.initialize( Injector.class );
	}
	
	@Override
	public void onSystemInit(Context systemContext) {
		WakeLockService.init();
		BypassEnforcement.init();
	}
}
