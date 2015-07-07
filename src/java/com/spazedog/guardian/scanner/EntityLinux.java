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

package com.spazedog.guardian.scanner;


import android.content.Context;
import android.graphics.drawable.Drawable;

import com.spazedog.guardian.R;
import com.spazedog.guardian.scanner.containers.ProcEntity;

public class EntityLinux extends ProcEntity<EntityLinux> {

    protected LinuxDataLoader mDataLoader;

    public static EntityLinux cast(ProcEntity<?> instance) {
        if (instance != null && instance instanceof EntityLinux) {
            return (EntityLinux) instance;
        }

        return null;
    }

    public static LinuxDataLoader cast(DataLoader<?> instance) {
        if (instance != null && instance instanceof LinuxDataLoader) {
            return (LinuxDataLoader) instance;
        }

        return null;
    }

    public EntityLinux() {
        super();
    }

    @Override
    public LinuxDataLoader getDataLoader(Context context) {
        if (mDataLoader == null) {
            mDataLoader = new LinuxDataLoader(context);
        }

        return mDataLoader;
    }



    /* ============================================================================================================
	 * ------------------------------------------------------------------------------------------------------------
	 *
	 * DATA LOADER CLASS
	 *
	 * ------------------------------------------------------------------------------------------------------------
	 */

    public class LinuxDataLoader extends DataLoader<LinuxDataLoader> {

        protected LinuxDataLoader(Context context) {
            super(context);
        }

        @Override
        public Drawable getPackageDrawable() {
            return mContext.getResources().getDrawable(R.drawable.process_icon);
        }

        @Override
        public String getPackageName() {
            return null;
        }

        @Override
        public String getPackageLabel() {
            return null;
        }

        @Override
        public String getProcessName() {
            return EntityLinux.this.getProcessName();
        }

        @Override
        public int getProcessUid() {
            return EntityLinux.this.getProcessUid();
        }

        @Override
        public int getProcessId() {
            return EntityLinux.this.getProcessId();
        }

        @Override
        public int getImportance() {
            return EntityLinux.this.getImportance();
        }
    }
}
