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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.spazedog.guardian.application.Controller.IServiceListener;
import com.spazedog.guardian.application.Controller.Status;
import com.spazedog.guardian.application.Settings;
import com.spazedog.guardian.utils.AbstractFragment;
import com.spazedog.guardian.utils.AbstractHandler;
import com.spazedog.guardian.views.CheckBoxWidget;
import com.spazedog.guardian.views.SpinnerWidget;
import com.spazedog.guardian.views.WidgetView;
import com.spazedog.guardian.views.WidgetView.WidgetChangeListener;
import com.spazedog.lib.rootfw4.RootFW;

public class FragmentConfiguration extends AbstractFragment implements IServiceListener, WidgetChangeListener {
	
	private static class ServiceHandler extends AbstractHandler<FragmentConfiguration> {
		public ServiceHandler(FragmentConfiguration reference) {
			super(reference);
		}

		@Override
		public void handleMessage(Message msg) {
			FragmentConfiguration fragment = getReference();
			
			if (fragment != null) {
				fragment.mIntervalSpinner.setWidgetEnabled( msg.what == Status.STOPPED || msg.what == Status.STARTED );
				fragment.mEngineSpinner.setWidgetEnabled( msg.what == Status.STOPPED || msg.what == Status.STARTED );
			}
		}
	}
	
	private CheckBoxWidget mLinuxCheckBox;
	private CheckBoxWidget mRootCheckBox;
	private SpinnerWidget mIntervalSpinner;
	private SpinnerWidget mEngineSpinner;
	
	private SpinnerWidget mThresholdSpinnerInt;
	private SpinnerWidget mThresholdSpinnerNon;
	
	private SpinnerWidget mActionSpinnerInt;
	private SpinnerWidget mActionSpinnerNon;
	
	private ServiceHandler mServiceHandler;

	@Override 
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(Common.resolveAttr(this.getActivity(), R.attr.layout_fragmentConfigurationLayout), container, false);
	}
	
	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		Settings settings = getSettings();
		
		mServiceHandler = new ServiceHandler(this);
		
		mLinuxCheckBox = (CheckBoxWidget) view.findViewById(R.id.config_linux_proc);
		mLinuxCheckBox.setChecked( settings.monitorLinux() );
		
		mRootCheckBox = (CheckBoxWidget) view.findViewById(R.id.config_root);
		mRootCheckBox.setChecked( settings.isRootEnabled() );
		
		mIntervalSpinner = (SpinnerWidget) view.findViewById(R.id.config_interval);
		mIntervalSpinner.setSelectedValue( "" + settings.getServiceInterval() );
		
		mEngineSpinner = (SpinnerWidget) view.findViewById(R.id.config_engine);
		mEngineSpinner.setSelectedValue( settings.getServiceEngine() );
		
		mThresholdSpinnerInt = (SpinnerWidget) view.findViewById(R.id.config_threshold_interactive);
		mThresholdSpinnerInt.setSelectedValue( "" + settings.getServiceThreshold(true) );
		
		mThresholdSpinnerNon = (SpinnerWidget) view.findViewById(R.id.config_threshold_noninteractive);
		mThresholdSpinnerNon.setSelectedValue( "" + settings.getServiceThreshold(false) );
		
		mActionSpinnerInt = (SpinnerWidget) view.findViewById(R.id.config_action_interactive);
		mActionSpinnerInt.setSelectedValue( settings.getServiceAction(true) );
		
		mActionSpinnerNon = (SpinnerWidget) view.findViewById(R.id.config_action_noninteractive);
		mActionSpinnerNon.setSelectedValue( settings.getServiceAction(false) );
	}
	
	@Override
	public void onStart() {
		super.onStart();
		
		getController().addServiceListener(this);
		
		mLinuxCheckBox.setWidgetChangeListener(this);
		mRootCheckBox.setWidgetChangeListener(this);
		mIntervalSpinner.setWidgetChangeListener(this);
		mEngineSpinner.setWidgetChangeListener(this);
		mThresholdSpinnerInt.setWidgetChangeListener(this);
		mThresholdSpinnerNon.setWidgetChangeListener(this);
		mActionSpinnerInt.setWidgetChangeListener(this);
		mActionSpinnerNon.setWidgetChangeListener(this);
	}
	
	@Override
	public void onStop() {
		super.onStop();
		
		getController().removeServiceListener(this);
		
		mLinuxCheckBox.setWidgetChangeListener(null);
		mRootCheckBox.setWidgetChangeListener(null);
		mIntervalSpinner.setWidgetChangeListener(null);
		mEngineSpinner.setWidgetChangeListener(null);
		mThresholdSpinnerInt.setWidgetChangeListener(null);
		mThresholdSpinnerNon.setWidgetChangeListener(null);
		mActionSpinnerInt.setWidgetChangeListener(null);
		mActionSpinnerNon.setWidgetChangeListener(null);
	}
	
	@Override
	public void onServiceChange(Integer status, Boolean sticky) {
		mServiceHandler.obtainMessage(status, sticky ? 1 : 0, 0).sendToTarget();
	}
	
	@Override
	public void onWidgetChanged(WidgetView<?> view, Object newValue) {
		if (view == mIntervalSpinner) {
			getSettings().setServiceInterval( Integer.valueOf( (String) newValue ) );
			
		} else if (view == mEngineSpinner) {
			getSettings().setServiceEngine( (String) newValue );
			
		} else if (view == mThresholdSpinnerInt) {
			getSettings().setServiceThreshold( Integer.valueOf( (String) newValue ), true );
			
		} else if (view == mThresholdSpinnerNon) {
			getSettings().setServiceThreshold( Integer.valueOf( (String) newValue ), false );
			
		} else if (view == mActionSpinnerInt) {
			getSettings().setServiceAction( (String) newValue, true );
			
		} else if (view == mActionSpinnerNon) {
			getSettings().setServiceAction( (String) newValue, false );
			
		} else if (view == mLinuxCheckBox) {
			getSettings().monitorLinux((Boolean) newValue);
			
		} else if (view == mRootCheckBox) {
			if (((Boolean) newValue)) {
				if (RootFW.connect() && RootFW.isRoot()) {
					RootFW.disconnect();
					
					getSettings().isRootEnabled(true);
					
				} else {
					mRootCheckBox.setChecked(false);
					
					AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).create();
					alertDialog.setTitle("SuperUser Rejected");
					alertDialog.setMessage("It was not possible to obtain SuperUser privileges. Either the request was rejected by the SuperUser control application, or the device does not have root access!");
					alertDialog.setCanceledOnTouchOutside(false);
					alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Close", new DialogInterface.OnClickListener() {
				        public void onClick(DialogInterface dialog, int which) {
				            dialog.dismiss();
				        }
					});
					
					alertDialog.show();
				}
				
			} else {
				getSettings().isRootEnabled(false);
			}
		}
	}
}
