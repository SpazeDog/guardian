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

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.spazedog.guardian.Common;
import com.spazedog.guardian.R;
import com.spazedog.lib.utilsLib.utils.Conversion;

public class SpinnerWidget extends WidgetView<Spinner> implements OnItemSelectedListener {
	
	protected Boolean mEnableListener = true;
	
	protected List<String> mSpinnerValues = new ArrayList<String>();
	protected List<String> mSpinnerNames = new ArrayList<String>();
	
	/*
	 * Used to make sure that the internal change listener does not run 
	 * the custom listener when it should not. 
	 */
	protected Integer mSelectedIndex = 0;
	
	public SpinnerWidget(Context context) {
		this(context, null);
	}
	
	public SpinnerWidget(Context context, AttributeSet attrs) {
		this(context, attrs, Conversion.attrToRes(context, R.attr.layout_viewSpinnerWidget));
	}
	
	public SpinnerWidget(Context context, AttributeSet attrs, int layoutRes) {
		super(context, attrs);
		
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(layoutRes, mWidgetHolder, true);
		
	    TypedArray types = context.obtainStyledAttributes(attrs, R.styleable.StringArrayOptions, 0, 0);
	    int arrayValuesId = types.getResourceId(R.styleable.StringArrayOptions_array_values, 0);
	    int arrayNamesId = types.getResourceId(R.styleable.StringArrayOptions_array_names, 0);
	    types.recycle();
    	
    	String[] values = arrayValuesId != 0 ? getResources().getStringArray(arrayValuesId) : null;
    	String[] names = arrayNamesId != 0 ? getResources().getStringArray(arrayNamesId) : null;
    	
    	setContent(names, values);
	}
	
	public void setContent(String[] names, String[] values) {
		if (values != null) {
			Spinner spinner = (Spinner) getWidget();
			
			spinner.setAdapter(null);
			mSpinnerValues.clear();
			mSpinnerNames.clear();
			
			for (int i=0; i < values.length; i++) {
				mSpinnerValues.add(values[i]);
				mSpinnerNames.add(
						names != null && names.length == values.length ? names[i] : values[i]
				);
			}

			spinner.setAdapter(
					new ArrayAdapter<String>(getContext(), R.layout.view_spinner_widget_item, mSpinnerNames)	
			);
		}
	}
	
	public void setSelectedValue(String value) {
		setSelectedValue(value, false);
	}
	
	public void setSelectedValue(String value, boolean invokeListener) {
		Spinner spinner = (Spinner) getWidget();
		mSelectedIndex = mSpinnerValues.indexOf(value);
		
		if (mSelectedIndex >= 0 && spinner.getSelectedItemPosition() != mSelectedIndex) {
			if (mEnableListener) {
				mEnableListener = false;
				
				spinner.setSelection(mSelectedIndex);
				
				if (invokeListener) {
					invokeOptionChangeListener(getSelectedValue());
				}
				
				mEnableListener = true;
				
			} else {
				spinner.setSelection(mSelectedIndex);
			}
		}
	}
	
	public String getSelectedValue() {
		return mSelectedIndex >= 0 ? mSpinnerValues.get(mSelectedIndex) : null;
	}

	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
		if (mEnableListener && mSelectedIndex != position) {
			invokeOptionChangeListener(mSpinnerValues.get(position));
		}
		
		mSelectedIndex = position;
	}

	@Override
	public void onNothingSelected(AdapterView<?> arg0) {}
	
	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		
		Spinner spinner = (Spinner) getWidget();
		spinner.setOnItemSelectedListener(this);
	}
	
	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		
		Spinner spinner = (Spinner) getWidget();
		spinner.setOnItemSelectedListener(null);
	}
}
