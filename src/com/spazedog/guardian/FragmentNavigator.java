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

package com.spazedog.guardian;

import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.spazedog.guardian.application.Controller.IServiceListener;
import com.spazedog.guardian.application.Controller.Status;
import com.spazedog.guardian.application.Settings;
import com.spazedog.guardian.utils.AbstractFragment;
import com.spazedog.guardian.utils.AbstractHandler;
import com.spazedog.guardian.views.IndicatorWidget;
import com.spazedog.guardian.views.SwitchWidget;
import com.spazedog.guardian.views.WidgetView;
import com.spazedog.guardian.views.WidgetView.WidgetChangeListener;

public class FragmentNavigator extends AbstractFragment implements OnClickListener, IServiceListener, WidgetChangeListener {
	
	private static class ServiceHandler extends AbstractHandler<FragmentNavigator> {
		public ServiceHandler(FragmentNavigator reference) {
			super(reference);
		}

		@Override
		public void handleMessage(Message msg) {
			FragmentNavigator fragment = getReference();
			
			if (fragment != null) {
				fragment.mSwitch.setChecked( msg.what > Status.STOPPED );
				fragment.mSwitch.setWidgetEnabled( msg.what == Status.STOPPED || msg.what == Status.STARTED );
			}
		}
	}
	
	private List<IndicatorWidget> mIndicators = new ArrayList<IndicatorWidget>();
	private SwitchWidget mSwitch;
	private ServiceHandler mServiceHandler;
	
	@Override 
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(Common.resolveAttr(getActivity(), R.attr.layout_fragmentNavigatorLayout), container, false);
	}
	
	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		findIndicators(view);
		setupIndicators();
		
		ActivityLaunch activity = (ActivityLaunch) getActivity();
		
		if (activity.getCurrentFragment() == null) {
			String tabIdString = activity.getIntent().getStringExtra("tab.id");
			IndicatorWidget currentIndicator = mIndicators.get(0);
			
			if (tabIdString != null) {
				int tabId = getResources().getIdentifier(tabIdString, "id", activity.getPackageName());
				
				if (tabId > 0) {
					for (IndicatorWidget widget : mIndicators) {
						if (widget.getId() == tabId) {
							currentIndicator = widget; break;
						}
					}
				}
			}
			
			currentIndicator.performClick();
		}
		
		mSwitch = (SwitchWidget) view.findViewById(R.id.service_switch);
		mServiceHandler = new ServiceHandler(this);
	}
	
	@Override
	public void onStart() {
		super.onStart();
		
		getController().addServiceListener(this);
		
		mSwitch.setWidgetChangeListener(this);
	}
	
	@Override
	public void onStop() {
		super.onStop();
		
		getController().removeServiceListener(this);
		
		mSwitch.setWidgetChangeListener(null);
	}
	
	private void findIndicators(View view) {
		if (view instanceof ViewGroup) {
			ViewGroup group = (ViewGroup) view;
			
			for (int i=0; i < group.getChildCount(); i++) {
				findIndicators( group.getChildAt(i) );
			}
			
			if (group instanceof IndicatorWidget) {
				String className = (String) group.getTag();
				
				if (className != null) {
					try {
						group.setTag(Class.forName(getActivity().getPackageName() + "." + className));
						group.setOnClickListener(this);
						
						mIndicators.add((IndicatorWidget) group);

					} catch (ClassNotFoundException e) {
						Log.e(getController().getPackageName(), e.getMessage(), e);
						group.setTag(null);
					}
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private void setupIndicators() {
		ActivityLaunch activity = (ActivityLaunch) getActivity();
		FragmentManager fragmentManager = activity.getSupportFragmentManager();
		
		for (IndicatorWidget widget : mIndicators) {
			Class<Fragment> clazz = (Class<Fragment>) widget.getTag();
			Boolean isLoaded = false;
			
			if (clazz != null) {
				isLoaded = fragmentManager.findFragmentByTag(clazz.getSimpleName()) != null;
				
				if (isLoaded) {
					activity.setTitle( widget.getTitle() );
				}
			}
			
			widget.setActivated(isLoaded);
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void onClick(View view) {
		if (view instanceof IndicatorWidget) {
			Class<Fragment> clazz = (Class<Fragment>) view.getTag();
			ActivityLaunch activity = (ActivityLaunch) getActivity(); 
			
			if (clazz != null) {
				try {
					activity.loadFragment(clazz.newInstance(), false);
					
				} catch (Throwable e) {}
			}
		}
	}
	
	@Override
	public void onReceiveMessage(String message, Object data, Boolean sticky) {
		if ("internal.fragment_attachment".equals(message) || "internal.backstack_changed".equals(message)) {
			setupIndicators();
		}
	}
	
	@Override
	public void onServiceChange(Integer status, Boolean sticky) {
		mServiceHandler.obtainMessage(status, sticky ? 1 : 0, 0).sendToTarget();
	}

	@Override
	public void onWidgetChanged(WidgetView<?> view, Object newValue) {
		if (view == mSwitch) {
			Settings settings = getSettings();
			
			/*
			 * This should always indicate the current state. 
			 * Our 'onServiceChange()' will make sure to update this.
			 */
			mSwitch.setChecked(settings.isServiceEnabled());
			
			settings.isServiceEnabled( (Boolean) newValue );
		}
	}
}
