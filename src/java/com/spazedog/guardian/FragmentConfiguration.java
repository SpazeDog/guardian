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
import com.spazedog.guardian.application.Settings;
import com.spazedog.guardian.backend.MonitorService.MonitorServiceControl.Status;
import com.spazedog.guardian.utils.AbstractFragment;
import com.spazedog.guardian.utils.AbstractHandler;
import com.spazedog.guardian.views.CheckBoxWidget;
import com.spazedog.guardian.views.SpinnerWidget;
import com.spazedog.guardian.views.WidgetView;
import com.spazedog.guardian.views.WidgetView.WidgetChangeListener;
import com.spazedog.lib.rootfw4.RootFW;

public class FragmentConfiguration extends AbstractFragment implements IServiceListener, WidgetChangeListener {
	
	protected static class ServiceHandler extends AbstractHandler<FragmentConfiguration> {
		public ServiceHandler(FragmentConfiguration reference) {
			super(reference);
		}

		@Override
		public void handleMessage(Message msg) {
			FragmentConfiguration fragment = getReference();
			
			if (fragment != null) {
                fragment.mNotifyCheckBox.setWidgetEnabled( msg.what == Status.STOPPED || msg.what == Status.STARTED );
				fragment.mIntervalSpinner.setWidgetEnabled( msg.what == Status.STOPPED || msg.what == Status.STARTED );
				fragment.mEngineSpinner.setWidgetEnabled( msg.what == Status.STOPPED || msg.what == Status.STARTED );
			}
		}
	}

	protected CheckBoxWidget mNotifyCheckBox;
	protected CheckBoxWidget mLinuxCheckBox;
	protected CheckBoxWidget mRootCheckBox;
	protected SpinnerWidget mIntervalSpinner;
	protected SpinnerWidget mEngineSpinner;
	
	protected SpinnerWidget mThresholdSpinnerInt;
	protected SpinnerWidget mThresholdSpinnerNon;
	
	protected SpinnerWidget mActionSpinnerInt;
	protected SpinnerWidget mActionSpinnerNon;
	
	protected CheckBoxWidget mWakelockActionCheckBox;
	protected SpinnerWidget mWakelockTimeSpinner;
	
	protected ServiceHandler mServiceHandler;

	@Override 
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(Common.resolveAttr(this.getActivity(), R.attr.layout_fragmentConfigurationLayout), container, false);
	}
	
	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {		
		super.onViewCreated(view, savedInstanceState);
		
		Settings settings = getSettings();
		
		mServiceHandler = new ServiceHandler(this);

        mNotifyCheckBox = (CheckBoxWidget) view.findViewById(R.id.config_notify);
        mNotifyCheckBox.setChecked( settings.persistentNotify() );
        mNotifyCheckBox.setVisibility("persistent".equals(settings.getServiceEngine()) ? View.VISIBLE : View.GONE);
		
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
		
		mWakelockActionCheckBox = (CheckBoxWidget) view.findViewById(R.id.config_wakelock_action);
		mWakelockActionCheckBox.setChecked( "release".equals(settings.getServiceWakeLockAction()) );
		
		mWakelockTimeSpinner = (SpinnerWidget) view.findViewById(R.id.config_wakelock_time);
		mWakelockTimeSpinner.setSelectedValue( "" + settings.getServiceWakeLockTime() );
		
		/*
		 * No Xposed Support
		 */
		if (getController().getWakeLockManager() == null) {
			mWakelockActionCheckBox.setEnabled(false);
			mWakelockTimeSpinner.setEnabled(false);
		}
		
		/*
		 * No root support
		 */
		if (!Common.hasRoot()) {
			mRootCheckBox.setEnabled(false);
		}
	}
	
	@Override
	public void onStart() {
		super.onStart();
		
		getController().addServiceListener(this);

        mNotifyCheckBox.setWidgetChangeListener(this);
		mLinuxCheckBox.setWidgetChangeListener(this);
		mRootCheckBox.setWidgetChangeListener(this);
		mIntervalSpinner.setWidgetChangeListener(this);
		mEngineSpinner.setWidgetChangeListener(this);
		mThresholdSpinnerInt.setWidgetChangeListener(this);
		mThresholdSpinnerNon.setWidgetChangeListener(this);
		mActionSpinnerInt.setWidgetChangeListener(this);
		mActionSpinnerNon.setWidgetChangeListener(this);
		mWakelockActionCheckBox.setWidgetChangeListener(this);
		mWakelockTimeSpinner.setWidgetChangeListener(this);
	}
	
	@Override
	public void onStop() {
		super.onStop();
		
		getController().removeServiceListener(this);

        mNotifyCheckBox.setWidgetChangeListener(null);
		mLinuxCheckBox.setWidgetChangeListener(null);
		mRootCheckBox.setWidgetChangeListener(null);
		mIntervalSpinner.setWidgetChangeListener(null);
		mEngineSpinner.setWidgetChangeListener(null);
		mThresholdSpinnerInt.setWidgetChangeListener(null);
		mThresholdSpinnerNon.setWidgetChangeListener(null);
		mActionSpinnerInt.setWidgetChangeListener(null);
		mActionSpinnerNon.setWidgetChangeListener(null);
		mWakelockActionCheckBox.setWidgetChangeListener(null);
		mWakelockTimeSpinner.setWidgetChangeListener(null);
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
            mNotifyCheckBox.setVisibility("persistent".equals((String) newValue) ? View.VISIBLE : View.GONE);
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
			
		} else if (view == mWakelockActionCheckBox) {
			getSettings().setServiceWakeLockAction((Boolean) newValue ? "release" : "notify");
			
		} else if (view == mWakelockTimeSpinner) {
			getSettings().setServiceWakeLockTime(Long.valueOf( (String) newValue ));
			
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

		} else if (view == mNotifyCheckBox) {
            getSettings().persistentNotify((Boolean) newValue);
        }
	}
}
