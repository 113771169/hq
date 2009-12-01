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
package org.hyperic.hq.bizapp.explorer.providers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.authz.server.session.ResourceGroup;
import org.hyperic.hq.bizapp.explorer.ExplorerContext;
import org.hyperic.hq.bizapp.explorer.ExplorerItem;
import org.hyperic.hq.bizapp.explorer.ExplorerView;
import org.hyperic.hq.bizapp.explorer.ExplorerViewHQUGroup;
import org.hyperic.hq.bizapp.explorer.ExplorerViewProvider;
import org.hyperic.hq.bizapp.explorer.ExplorerViewType;
import org.hyperic.hq.bizapp.explorer.types.GroupItem;
import org.hyperic.hq.bizapp.explorer.types.GroupItemType;
import org.hyperic.hq.hqu.AttachmentDescriptor;
import org.hyperic.hq.hqu.server.session.Attachment;
import org.hyperic.hq.hqu.server.session.UIPluginManagerImpl;
import org.hyperic.hq.hqu.server.session.ViewResourceCategory;
import org.hyperic.hq.hqu.shared.UIPluginManager;

/**
 * This provider is capable of providing views for {@link GroupItem}s.
 * 
 * The views returned are of type {@link ExplorerViewHQUGroup} and represent
 * all the views a user can see on their 'Views' tab of the group's 
 * indicator page. 
 */
public class HQUGroupProvider implements ExplorerViewProvider {
    private final UIPluginManager _pluginMan = 
        UIPluginManagerImpl.getOne();
    
    public HQUGroupProvider() {
    }

    public String getName() {
        return "hquGroup";
    }

    public Collection getViewFor(ExplorerContext ctx, ExplorerItem item) {
        if (item.getType().getName().equals(GroupItemType.NAME) == false) {
            return Collections.EMPTY_LIST;
        }
        
        GroupItem gItem = (GroupItem)item;
        ResourceGroup g = gItem.getGroup();
        Collection desc = 
            _pluginMan.findAttachments(AppdefEntityID.newGroupID(g.getId()),
                                       ViewResourceCategory.VIEWS,
                                       ctx.getSubject()); 

        List res = new ArrayList(desc.size());
        
        for (Iterator i=desc.iterator(); i.hasNext(); ) {
            final AttachmentDescriptor d = (AttachmentDescriptor)i.next();
            final String viewName =
                d.getAttachment().getView().getPlugin().getName();
            
            ExplorerView eview = new ExplorerViewHQUGroup() {
                public String getName() {
                    return viewName; 
                }

                public String getText() {
                    return d.getAttachment().getView().getDescription();
                }

                public String getStyleClass() {
                    return d.getIconClass();
                }
                
                public AttachmentDescriptor getAttachmentDescriptor() {
                    return d;
                }

                public ExplorerViewType getType() {
                    return ExplorerViewType.HQU_GROUP_VIEW;
                }
            };
            res.add(eview);
        }
        
        return res;
    }
}
