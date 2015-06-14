package com.spazedog.guardian.views;

public interface IExtendedLayout {
	
	public void adoptWidth(boolean adopt);
	public void adoptHeight(boolean adopt);
	
	public void setMinHeight(int min);
	public void setMinWidth(int min);
	public void setMaxHeight(int max);
	public void setMaxWidth(int max);
	public void setTotalHeight(int total);
	public void setTotalWidth(int total);
	
	public void setShadowOffset(int shadowTopOffset);
	public void setShadowVisible(boolean shadowVisible);
}
