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
import android.view.LayoutInflater;
import android.widget.TextView;

import com.spazedog.guardian.Common;
import com.spazedog.guardian.R;

public class TextboxWidget extends WidgetView<TextView> {
	
	public TextboxWidget(Context context) {
		this(context, null);
	}
	
	public TextboxWidget(Context context, AttributeSet attrs) {
		this(context, attrs, Common.resolveAttr(context, R.attr.layout_viewTextboxWidget));
	}
	
	public TextboxWidget(Context context, AttributeSet attrs, int layoutRes) {
		super(context, attrs);
		
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(layoutRes, mWidgetHolder, true);
	}
	
	public void setText(int resid) {
		if (resid > 0) {
			getWidget().setText(resid);
			
		} else {
			getWidget().setText("");
		}
	}
	
	public void setText(CharSequence text) {
		getWidget().setText(text != null ? text : "");
	}
	
	public CharSequence getText() {
		return getWidget().getText();
	}
}
