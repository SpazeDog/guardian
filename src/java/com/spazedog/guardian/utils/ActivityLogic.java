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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import android.os.Message;

final public class ActivityLogic {
	
	public static interface OnFragmentScrollListener {
		public Float onFragmentScrollVertical(Float scrollSize, Float scrollPos);
	}
	
	public static interface IActivityLogic {
		public void onReceiveMessage(String message, Object data, Boolean sticky);
		public void onFragmentAttachment(IActivityLogicFragment fragment);
		public void onFragmentDetachment(IActivityLogicFragment fragment);
		public void sendMessage(String message, Object data);
		public void sendMessage(String message, Object data, Boolean sticky);
	}
	
	public static interface IActivityLogicFragment {
		public void onReceiveMessage(String message, Object data, Boolean sticky);
	}
	
	private final static class ActivityLogic_MessageHandler extends AbstractHandler<ActivityLogic> {
		public ActivityLogic_MessageHandler(ActivityLogic reference) {
			super(reference);
		}

		@Override
		public void handleMessage(Message msg) {
			ActivityLogic logic = getReference();
			
			if (logic != null) {
				IActivityLogic activity = logic.ActivityLogic_mActivity.get();
				
				if (activity != null) {
					Set<IActivityLogicFragment> fragments = new HashSet<IActivityLogicFragment>(logic.ActivityLogic_mFragments);
					Object[] input = (Object[]) msg.obj;
					String message = (String) input[0];
					Object data = input[1];
					
					activity.onReceiveMessage(message, data, false);
					
					for (IActivityLogicFragment fragment : fragments) {
						fragment.onReceiveMessage(message, data, false);
					}
				}
			}
		}
	}
	
	private Set<IActivityLogicFragment> ActivityLogic_mFragments = Collections.newSetFromMap(new WeakHashMap<IActivityLogicFragment, Boolean>());
	private Map<String, Object> ActivityLogic_mStickyMessages = new HashMap<String, Object>();
	
	private ActivityLogic_MessageHandler ActivityLogic_mMessageHandler;
	private WeakReference<IActivityLogic> ActivityLogic_mActivity;
	
	public ActivityLogic(IActivityLogic activity) {
		ActivityLogic_mActivity = new WeakReference<IActivityLogic>(activity);
		ActivityLogic_mMessageHandler = new ActivityLogic_MessageHandler(this);
	}
	
	public void onFragmentAttachment(IActivityLogicFragment fragment) {
		synchronized (ActivityLogic_mFragments) {
			ActivityLogic_mFragments.add(fragment);
			
			for (String message : ActivityLogic_mStickyMessages.keySet()) {
				fragment.onReceiveMessage(message, ActivityLogic_mStickyMessages.get(message), true);
			}
		}
	}
	
	public void onFragmentDetachment(IActivityLogicFragment fragment) {
		synchronized (ActivityLogic_mFragments) {
			ActivityLogic_mFragments.remove(fragment);
		}
	}
	
	public void sendMessage(String message, Object data) {
		sendMessage(message, data, false);
	}
	
	public void sendMessage(String message, Object data, Boolean sticky) {
		synchronized(ActivityLogic_mFragments) {
			if (sticky) {
				ActivityLogic_mStickyMessages.put(message, data);
				
			} else if (ActivityLogic_mStickyMessages.containsKey(message)) {
				ActivityLogic_mStickyMessages.remove(message);
			}

			ActivityLogic_mMessageHandler.obtainMessage(0, new Object[]{message, data}).sendToTarget();
		}
	}
}
