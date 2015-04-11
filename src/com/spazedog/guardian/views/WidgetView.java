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

package com.spazedog.guardian.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.spazedog.guardian.Common;
import com.spazedog.guardian.R;

public abstract class WidgetView<T extends View> extends ExtendedLinearLayout {
	
	public static interface WidgetChangeListener {
		public void onWidgetChanged(WidgetView<?> view, Object newValue);
	}
	
	protected WidgetChangeListener mListener;
	protected Boolean mActiveListener = false;
	
	protected ViewGroup mWidgetHolder;
	protected TextView mTitle;
	protected TextView mSummary;
	
	public WidgetView(Context context) {
		this(context, null);
	}
	
	public WidgetView(Context context, AttributeSet attrs) {
		this(context, attrs, Common.resolveAttr(context, R.attr.layout_viewWidgetLayout));
	}
	
	public WidgetView(Context context, AttributeSet attrs, int layoutRes) {
		super(context, attrs);
		
		setOrientation(LinearLayout.VERTICAL);
		
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(layoutRes, this, true);
		
	    TypedArray types = context.obtainStyledAttributes(attrs, R.styleable.SummaryOptions, 0, 0);
	    String summary = types.getString(R.styleable.SummaryOptions_summary_text);
	    types.recycle();
	    
	    types = context.obtainStyledAttributes(attrs, R.styleable.TitleOptions, 0, 0);
	    String title = types.getString(R.styleable.TitleOptions_title_text);
	    types.recycle();
	    
        /*
         * Android screws up when restoring states with ID's, when there is multiple view with the same ID.
         * So let's remove the ID's on all views. 
         */
        	
	    mWidgetHolder = (ViewGroup) findViewById(R.id.widget);
	    mWidgetHolder.setId(NO_ID);
        
        mTitle = (TextView) findViewById(R.id.title);
        mTitle.setId(NO_ID);
        
        mSummary = (TextView) findViewById(R.id.summary);
        mSummary.setId(NO_ID);
        
        setTitle(title);
        setSummary(summary);
	}
	
	public void setWidget(T widget) {
		if (mWidgetHolder != null) {
			mWidgetHolder.addView(widget);
		}
	}
	
	@SuppressWarnings("unchecked")
	public T getWidget() {
		if (mWidgetHolder != null) {
			return (T) mWidgetHolder.getChildAt(0);
		}
		
		return null;
	}
	
	public void setTitle(int resid) {
		if (resid > 0) {
			mTitle.setText(resid);
			mTitle.setVisibility(View.VISIBLE);
			
		} else {
			mTitle.setVisibility(View.GONE);
		}
	}
	
	public void setTitle(CharSequence text) {
		mTitle.setText(text != null ? text : "");
		
		if (text != null) {
			mTitle.setVisibility(View.VISIBLE);
			
		} else {
			mTitle.setVisibility(View.GONE);
		}
	}
	
	public CharSequence getTitle() {
		return mTitle.getText();
	}

	public void setSummary(int resid) {
		if (resid > 0) {
			mSummary.setText(resid);
			mSummary.setVisibility(View.VISIBLE);
			
		} else {
			mSummary.setVisibility(View.GONE);
		}
	}
	
	public void setSummary(CharSequence text) {
		mSummary.setText(text != null ? text : "");
		
		if (text != null) {
			mSummary.setVisibility(View.VISIBLE);
			
		} else {
			mSummary.setVisibility(View.GONE);
		}
	}
	
	public CharSequence getSummary() {
		return mSummary.getText();
	}
	
	public void setWidgetChangeListener(WidgetChangeListener listener) {
		mListener = listener;
	}
	
	protected void invokeOptionChangeListener(Object newValue) {
		if (mListener != null && !mActiveListener) {
			mActiveListener = true;
			mListener.onWidgetChanged(this, newValue);
			mActiveListener = false;
		}
	}
	
	public boolean isWidgetEnabled() {
		return getWidget().isEnabled();
	}
	
	public void setWidgetEnabled(boolean enabled) {
		if (enabled && !isEnabled()) {
			setEnabled(enabled);
			
		} else {
			getWidget().setEnabled(enabled);
		}
	}
	
	private void setEnabledRecursive(View view, boolean enabled) {
		if (view == this) {
			super.setEnabled(enabled);
			
		} else {
			view.setEnabled(enabled);
		}
		
		if (view instanceof ViewGroup) {
			for (int i=0; i < ((ViewGroup)view).getChildCount(); i++) {
				setEnabledRecursive( ((ViewGroup)view).getChildAt(i), enabled );
			}
		}
	}
	
	@Override
	public void setEnabled(boolean enabled) {
		setEnabledRecursive(this, enabled);
	}
}
