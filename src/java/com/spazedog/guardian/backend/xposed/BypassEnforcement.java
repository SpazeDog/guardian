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


import android.os.Binder;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

import com.spazedog.guardian.Common.LOG;
import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.ReflectException;
import com.spazedog.lib.reflecttools.bridge.MethodBridge;

public class BypassEnforcement {

    public static void init() {
        if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP_MR1) {
            BypassEnforcement instance = new BypassEnforcement();

            try {
                ReflectClass.fromName("com.android.server.am.ActivityManagerService").bridge("getRunningAppProcesses", instance.getRunningAppProcesses);

            } catch (ReflectException e) {
                LOG.Error(instance, e.getMessage(), e);
            }
        }
    }

    private MethodBridge getRunningAppProcesses = new MethodBridge() {
        @Override
        public void bridgeBegin(BridgeParams params) {
            /*
             * I am not much for bad language, but Fuck Google, with capital F,
             * for restricting this harmless feature.
             *
             * Thanks to Xposed, we can hide the fact that we are not
             * the system calling for this list.
             */
            long id = Binder.clearCallingIdentity();

            params.setResult(params.invokeOriginal(params.args));

            Binder.restoreCallingIdentity(id);
        }
    };
}
