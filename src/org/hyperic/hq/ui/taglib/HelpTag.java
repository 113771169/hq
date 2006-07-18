/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004, 2005, 2006], Hyperic, Inc.
 * This file is part of HQ.
 * 
 * HQ is free software; you can redistribute it and/or modify
 * it under the terms version 2 of the GNU General Public License as
 * published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

package org.hyperic.hq.ui.taglib;

import java.io.IOException;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;

import org.hyperic.hq.ui.Constants;

/**
 * return the link to the help system.
 */
public class HelpTag extends VarSetterBaseTag {
    private boolean context = true;
    
    //----------------------------------------------------public methods
    public boolean isContext() {
        return context;
    }

    public void setContext(boolean context) {
        this.context = context;
    }

    public int doStartTag() throws JspException{
        JspWriter output = pageContext.getOut();
        
        // Retrieve the context and compose the help URL
        String helpURL = (String) pageContext.getServletContext().getAttribute(
            Constants.HELP_BASE_URL_KEY);
        
        if (context) {
            String helpContext = (String)
                pageContext.getRequest().getAttribute(Constants.PAGE_TITLE_KEY);
            
            if ( helpContext != null)
                helpURL = helpURL + "ui-" + helpContext; 
        }

        try{
            output.print( helpURL );
        }
        catch(IOException e){
            throw new JspException(e);        
        }
        return SKIP_BODY;
    }
}
