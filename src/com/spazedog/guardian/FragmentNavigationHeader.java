package com.spazedog.guardian;

import android.app.ProgressDialog;
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
import com.spazedog.guardian.views.SwitchWidget;
import com.spazedog.guardian.views.WidgetView;
import com.spazedog.guardian.views.WidgetView.WidgetChangeListener;

public class FragmentNavigationHeader extends AbstractFragment implements IServiceListener, WidgetChangeListener {

	private static class ServiceHandler extends AbstractHandler<FragmentNavigationHeader> {
		private ProgressDialog mProgress;
		
		/*
		 * In some 4.x Android version the Handler.removeMessages does not work properly.
		 * We use this to make sure that progress is not displayed when service is not pending.
		 */
		private boolean mShowProgress = false;
		
		public ServiceHandler(FragmentNavigationHeader reference) {
			super(reference);
		}

		@Override
		public void handleMessage(Message msg) {
			FragmentNavigationHeader fragment = getReference();
			
			removeMessages(2);
			
			if (fragment != null) {
				switch (msg.what) {
					case 1:
						mShowProgress = msg.arg1 == Status.PENDING;
						fragment.mSwitch.setChecked( msg.arg1 > Status.STOPPED );
						fragment.mSwitch.setWidgetEnabled( msg.arg1 == Status.STOPPED || msg.arg1 == Status.STARTED );
						
						if (mShowProgress) {
							sendEmptyMessageDelayed(2, 1000);
							
						} else if (mProgress != null) {
							mProgress.dismiss();
							mProgress = null;
						}
						
						break;
						
					case 2:
						if (mShowProgress && (mProgress == null || !mProgress.isShowing())) {
							try {
								mProgress = ProgressDialog.show(fragment.getActivity(), "", "Waiting for Service Response. Please wait...", true, true);
								
							} catch (Throwable e) {}
						}
				}
			}
		}
	}
	
	private SwitchWidget mSwitch;
	private ServiceHandler mServiceHandler;
	
	@Override 
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_navigation_header_layout, container, false);
	}
	
	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
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
	
	@Override
	public void onServiceChange(Integer status, Boolean sticky) {
		mServiceHandler.obtainMessage(1, status, sticky ? 1 : 0).sendToTarget();
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
