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

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentManager.OnBackStackChangedListener;
import android.support.v7.app.AppCompatActivity;

import com.spazedog.guardian.application.ApplicationImpl;
import com.spazedog.guardian.application.Controller;
import com.spazedog.guardian.application.Settings;
import com.spazedog.guardian.utils.ActivityLogic.IActivityLogic;
import com.spazedog.guardian.utils.ActivityLogic.IActivityLogicFragment;

public abstract class AbstractActivity extends AppCompatActivity implements IActivityLogic, ApplicationImpl {
	
	private ActivityLogic mLogic;
	
	public AbstractActivity() {
		mLogic = new ActivityLogic(this);
	}
	
	@Override
	public final Controller getController() {
		return (Controller) getApplicationContext();
	}
	
	@Override
	public final Settings getSettings() {
		Controller controller = getController();
		
		return controller != null ? controller.getSettings() : null;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		FragmentManager manager = getSupportFragmentManager();
		manager.addOnBackStackChangedListener(new OnBackStackChangedListener(){
			@Override
			public void onBackStackChanged() {
				sendMessage("internal.backstack_changed", null);
			}
		});
	}
	
	@Override
	public void onFragmentAttachment(IActivityLogicFragment fragment) {
		sendMessage("internal.fragment_attachment", fragment);
		
		mLogic.onFragmentAttachment(fragment);
	}
	
	@Override
	public void onFragmentDetachment(IActivityLogicFragment fragment) {
		mLogic.onFragmentDetachment(fragment);
		
		sendMessage("internal.fragment_detachment", fragment);
	}
	
	@Override
	public void onReceiveMessage(String message, Object data, Boolean sticky) {}
	
	@Override
	public final void sendMessage(String message, Object data) {
		mLogic.sendMessage(message, data);
	}
	
	@Override
	public final void sendMessage(String message, Object data, Boolean sticky) {
		mLogic.sendMessage(message, data, sticky);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Bundle bundle = new Bundle();
		bundle.putInt("requestCode", requestCode);
		bundle.putInt("resultCode", resultCode);
		
		if (data != null) {
			bundle.putParcelable("intent", data);
		}
		
		sendMessage("internal.activity_result", bundle);
	}
}
