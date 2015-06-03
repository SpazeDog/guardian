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
import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import com.spazedog.guardian.Common;
import com.spazedog.guardian.R;
import com.spazedog.guardian.application.Controller;
import com.spazedog.guardian.application.Controller.IControllerWrapper;
import com.spazedog.guardian.application.Settings;
import com.spazedog.guardian.application.Settings.ISettingsWrapper;
import com.spazedog.guardian.utils.ActivityLogic.IActivityLogic;
import com.spazedog.guardian.utils.FragmentLogic.IFragmentLogic;
import com.spazedog.guardian.views.IExtendedLayout;

public abstract class AbstractFragmentDialog extends DialogFragment implements IFragmentLogic, IDialog, IControllerWrapper, ISettingsWrapper {
	
	private FragmentLogic mLogic;
	
	/*
	 * isResumed() in the support library does not work 
	 * properly. It is true during onPause() as well.
	 */
	private boolean mAbstractFragmentDialog_IsResumed = false;
	
	public AbstractFragmentDialog() {
		mLogic = new FragmentLogic(this);
		
		setArguments(new Bundle());
		
		setStyle(0, R.style.App_Dialog);
	}
	
	public boolean isActive() {
		return mAbstractFragmentDialog_IsResumed;
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
	  Dialog dialog = super.onCreateDialog(savedInstanceState);
	  dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
	  dialog.setCanceledOnTouchOutside(false);
	  
	  return dialog;
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View layout = onCreateSizedView(inflater, container, savedInstanceState);
		
		if (layout != null && layout instanceof IExtendedLayout) {
			IExtendedLayout exLayout = (IExtendedLayout) layout;
			
			if (Common.getDisplaySW() < 600) {
				if (Common.isDisplayLandscape() && Common.getDisplayLW() >= 600) {
					exLayout.setMaxHeight( (int) (Common.getDisplayHeight() * 0.95) );
					exLayout.setTotalWidth( (int) (Common.getDisplayWidth() * 0.75) );
					
				} else if (Common.getDisplayLW() >= 600) {
					exLayout.setMaxHeight( (int) (Common.getDisplayHeight() * 0.90) );
					exLayout.setTotalWidth( (int) (Common.getDisplayWidth() * 0.90) );
				
				} else {
					exLayout.setMaxHeight( (int) (Common.getDisplayHeight() * 0.95) );
					exLayout.setTotalWidth( (int) (Common.getDisplayWidth() * 0.95) );
				}
				
			} else if (Common.isDisplayLandscape()) {
				exLayout.setMaxHeight( (int) (Common.getDisplayHeight() * 0.85) );
				exLayout.setTotalWidth( (int) (Common.getDisplayWidth() * 0.45) );
				
			} else {
				exLayout.setMaxHeight( (int) (Common.getDisplayHeight() * 0.75) );
				exLayout.setTotalWidth( (int) (Common.getDisplayWidth() * 0.65) );
			}
			
		} else if (layout != null) {
			if (Common.getDisplaySW() < 600) {
				if (Common.isDisplayLandscape() && Common.getDisplayLW() >= 600) {
					layout.setMinimumWidth( (int) (Common.getDisplayWidth() * 0.65) );
					
				} else {
					layout.setMinimumWidth( (int) (Common.getDisplayWidth() * 0.75) );
				}
				
			} else {
				if (Common.isDisplayLandscape()) {
					layout.setMinimumWidth( (int) (Common.getDisplayWidth() * 0.45) );
					
				} else {
					layout.setMinimumWidth( (int) (Common.getDisplayWidth() * 0.65) );
				}
			}
		}
		
		return layout;
	}
	
	public View onCreateSizedView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return null;
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		mAbstractFragmentDialog_IsResumed = true;
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		mAbstractFragmentDialog_IsResumed = false;
	}

	@Override
	public final AbstractActivity getParent() {	
		return (AbstractActivity) mLogic.getParent();
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
	
	public void onReceiveMessage(String message, Object data, Boolean sticky) {}
	
	public final void sendMessage(String message, Object data) {
		mLogic.sendMessage(message, data);
	}
	
	public final void sendMessage(String message, Object data, Boolean sticky) {
		mLogic.sendMessage(message, data, sticky);
	}
	
	@Override
	public void cancel() {
		// Empty
	}
	
	@Override
	public void onDestroyView() {
		/*
		 * Fix for the compatibility library issue: http://code.google.com/p/android/issues/detail?id=17423
		 */
	    if (getDialog() != null && getRetainInstance()) {
	        getDialog().setDismissMessage(null);
	    }
	     
	    super.onDestroyView();
	}
}
