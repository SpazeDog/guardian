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

import com.spazedog.guardian.utils.ActivityLogic.IActivityLogic;
import com.spazedog.guardian.utils.ActivityLogic.IActivityLogicFragment;

final public class FragmentLogic {
	
	public static interface IFragmentLogic extends IActivityLogicFragment {
		public IActivityLogic getParent();
		public void sendMessage(String message, Object data);
		public void sendMessage(String message, Object data, Boolean sticky);
	}
	
	private WeakReference<IActivityLogic> FragmentLogic_mActivity;
	private WeakReference<IFragmentLogic> FragmentLogic_mFragment;
	
	public FragmentLogic(IFragmentLogic fragment) {
		FragmentLogic_mFragment = new WeakReference<IFragmentLogic>(fragment);
	}
	
	public void onAttach(IActivityLogic activity) {
		FragmentLogic_mActivity = new WeakReference<IActivityLogic>(activity);

		IFragmentLogic fragment = FragmentLogic_mFragment.get();
		if (fragment != null) {
			activity.onFragmentAttachment(fragment);
		}
	}
	
	public void onDetach() {
		IFragmentLogic fragment = FragmentLogic_mFragment.get();
		IActivityLogic activity = FragmentLogic_mActivity.get();
		
		if (fragment != null && activity != null) {
			activity.onFragmentDetachment(fragment);
		}
		
		FragmentLogic_mActivity.clear();
	}
	
	public IActivityLogic getParent() {		
		return FragmentLogic_mActivity.get();
	}
	
	public void sendMessage(String message, Object data) {
		sendMessage(message, data, false);
	}
	
	public void sendMessage(String message, Object data, Boolean sticky) {
		IActivityLogic activity = getParent();
		
		if (activity != null) {
			activity.sendMessage(message, data, sticky);
		}
	}
}
