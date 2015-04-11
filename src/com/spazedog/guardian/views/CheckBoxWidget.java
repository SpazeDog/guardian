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
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.spazedog.guardian.Common;
import com.spazedog.guardian.R;

public class CheckBoxWidget extends WidgetView<CompoundButton> implements OnCheckedChangeListener {
	
	public CheckBoxWidget(Context context) {
		this(context, null);
	}
	
	public CheckBoxWidget(Context context, AttributeSet attrs) {
		this(context, attrs, Common.resolveAttr(context, R.attr.layout_viewCheckboxWidget));
	}
	
	public CheckBoxWidget(Context context, AttributeSet attrs, int layoutRes) {
		super(context, attrs);
		
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(layoutRes, mWidgetHolder, true);
		
	    TypedArray types = context.obtainStyledAttributes(attrs, R.styleable.CompoundOptions, 0, 0);
	    Boolean checked = types.getBoolean(R.styleable.CompoundOptions_compound_checked, false);
	    types.recycle();
	    
	    getWidget().setChecked(checked);
	}
	
	@Override
	public void onCheckedChanged(CompoundButton view, boolean checked) {
		invokeOptionChangeListener((Boolean) checked);
	}
	
	public Boolean isChecked() {
		return ((CompoundButton) getWidget()).isChecked();
	}
	
	public void setChecked(boolean checked) {
		setChecked(checked, false);
	}
	
	public void setChecked(boolean checked, boolean invokeListener) {
		CompoundButton widget = (CompoundButton) getWidget();
		
		if (widget.isChecked() != checked) {
			widget.setOnCheckedChangeListener(null);
			widget.setChecked(checked);
			
			if (invokeListener) {
				invokeOptionChangeListener((Boolean) checked);
			}
			
			widget.setOnCheckedChangeListener(this);
		}
	}
	
	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		
		CompoundButton widget = (CompoundButton) getWidget();
		widget.setOnCheckedChangeListener(this);
	}
	
	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		
		CompoundButton widget = (CompoundButton) getWidget();
		widget.setOnCheckedChangeListener(null);
	}
}
