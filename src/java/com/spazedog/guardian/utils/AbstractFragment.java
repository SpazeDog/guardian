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

import android.app.Activity;
import android.support.v4.app.Fragment;

import com.spazedog.guardian.application.ApplicationImpl;
import com.spazedog.guardian.application.Controller;
import com.spazedog.guardian.application.Settings;
import com.spazedog.guardian.utils.ActivityLogic.IActivityLogic;
import com.spazedog.guardian.utils.FragmentLogic.IFragmentLogic;

public abstract class AbstractFragment extends Fragment implements IFragmentLogic, ApplicationImpl {
	
	private FragmentLogic mLogic;
	
	public AbstractFragment() {
		mLogic = new FragmentLogic(this);
	}

	@Override
	public final AbstractActivity getParent() {		
		return (AbstractActivity) mLogic.getParent();
	}
	
	@Override
	public final Controller getController() {
		AbstractActivity activity = getParent();
		
		return activity != null ? activity.getController() : null;
	}
	
	@Override
	public final Settings getSettings() {
		Controller controller = getController();
		
		return controller != null ? controller.getSettings() : null;
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		
		mLogic.onAttach((IActivityLogic) activity);
	}
	
	@Override
	public void onDetach() {
		super.onDetach();
		
		mLogic.onDetach();
	}
	
	public void onReceiveMessage(String message, Object data, Boolean sticky) {}
	
	public final void sendMessage(String message, Object data) {
		mLogic.sendMessage(message, data);
	}
	
	public final void sendMessage(String message, Object data, Boolean sticky) {
		mLogic.sendMessage(message, data, sticky);
	}
}
