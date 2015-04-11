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
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import com.spazedog.guardian.Common;
import com.spazedog.guardian.R;

public class IndicatorWidget extends WidgetView<View> {
	
	public IndicatorWidget(Context context) {
		this(context, null);
	}
	
	public IndicatorWidget(Context context, AttributeSet attrs) {
		this(context, attrs, Common.resolveAttr(context, R.attr.layout_viewIndicatorWidget));
	}
	
	public IndicatorWidget(Context context, AttributeSet attrs, int layoutRes) {
		super(context, attrs, Common.resolveAttr(context, R.attr.layout_viewIndicatorLayout));
		
		setOrientation(LinearLayout.HORIZONTAL);
		setGravity(Gravity.CENTER_VERTICAL);
		
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(layoutRes, mWidgetHolder, true);
	}
	
	@Override
	public boolean isActivated() {
		return getWidget().isActivated();
	}
	
	@Override
	public void setActivated(boolean checked) {
		setActivated(checked, false);
	}
	
	public void setActivated(boolean activate, boolean invokeListener) {
		View widget = getWidget();
		
		if (widget.isActivated() != activate) {
			widget.setActivated(activate);
			
			if (invokeListener) {
				invokeOptionChangeListener((Boolean) activate);
			}
		}
	}
}
