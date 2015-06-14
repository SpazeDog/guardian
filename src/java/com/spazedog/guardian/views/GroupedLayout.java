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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.spazedog.guardian.Common;
import com.spazedog.guardian.R;

public class GroupedLayout extends ExtendedLinearLayout {
	
	protected ViewGroup mFrame;
	protected TextView mHeadline;

	public GroupedLayout(Context context) {
		this(context, null);
	}
	
	public GroupedLayout(Context context, AttributeSet attrs) {
		this(context, attrs, Common.resolveAttr(context, R.attr.layout_viewGroupedLayout));
	}
	
	public GroupedLayout(Context context, AttributeSet attrs, int layoutRes) {
		super(context, attrs);
		
		setOrientation(LinearLayout.VERTICAL);
		setGravity(Gravity.CENTER_VERTICAL);
		
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(layoutRes, this, true);
		
	    TypedArray types = context.obtainStyledAttributes(attrs, R.styleable.TitleOptions, 0, 0);
	    String headline = types.getString(R.styleable.TitleOptions_title_text);
	    types.recycle();
	    
	    mFrame = (ViewGroup) findViewById(R.id.frame);
	    mHeadline = (TextView) findViewById(R.id.title);
	    
	    setTitle(headline);
	}
	
	@Override
	public void addView(View child, int index, android.view.ViewGroup.LayoutParams params) {
		if (mFrame != null) {
			mFrame.addView(child, index, params);
			
		} else {
			super.addView(child, index, params);
		}
	}
	
	public void setTitle(int resid) {
		if (resid >= 0) {
			mHeadline.setText(resid);
			mHeadline.setVisibility(View.VISIBLE);
			
		} else {
			mHeadline.setText("");
			mHeadline.setVisibility(View.GONE);
		}
	}
	
	public void setTitle(CharSequence text) {
		if (text != null) {
			mHeadline.setText(text);
			mHeadline.setVisibility(View.VISIBLE);
			
		} else {
			mHeadline.setText("");
			mHeadline.setVisibility(View.GONE);
		}
	}
}
