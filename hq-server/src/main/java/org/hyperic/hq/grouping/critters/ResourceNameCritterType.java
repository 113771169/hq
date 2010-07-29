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

package org.hyperic.hq.grouping.critters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.hibernate.Query;
import org.hyperic.hq.authz.server.session.Resource;
import org.hyperic.hq.grouping.Critter;
import org.hyperic.hq.grouping.CritterDump;
import org.hyperic.hq.grouping.CritterTranslationContext;
import org.hyperic.hq.grouping.CritterType;
import org.hyperic.hq.grouping.GroupException;
import org.hyperic.hq.grouping.prop.CritterPropType;
import org.hyperic.hq.grouping.prop.StringCritterProp;

/**
 * This type of criteria is able to match resources by their name
 *
 * "Show me all resources named '.*web-infra.*'"
 */
public class ResourceNameCritterType
    extends BaseCritterType
{
    private static final String PROP_NAME = "name";

    public ResourceNameCritterType() {
        initialize("org.hyperic.hq.grouping.Resources", "resourceName"); 
        addPropDescription(PROP_NAME, CritterPropType.STRING);
    }

    public ResourceNameCritter newInstance(String name) 
        throws PatternSyntaxException {
        return new ResourceNameCritter(name, this);
    }
    
    public Critter newInstance(Map critterProps)
        throws GroupException
    {
        validate(critterProps);
        StringCritterProp c = (StringCritterProp) critterProps.get(PROP_NAME);
        return new ResourceNameCritter(c.getString(), this);
    }
    
    public Critter compose(CritterDump dump) throws GroupException {
        return newInstance(dump.getStringProp());
    }

    public void decompose(Critter critter, CritterDump dump)
        throws GroupException 
    {
        // verify that critter is of the right type
        if (!(critter instanceof ResourceNameCritter))
            throw new GroupException("Critter is not of valid type " + 
                                     "ResourceNameCritter");
        
        ResourceNameCritter resourceCritter = (ResourceNameCritter)critter;
        dump.setStringProp(resourceCritter.getNameRegex());
    }

    public boolean isUserVisible() {
        return true;
    }

    public boolean isSystem() {
        return false;
    }
    
    class ResourceNameCritter implements Critter {
        private final String _nameRegex;
        private final List _props;
        private final ResourceNameCritterType _type;

        ResourceNameCritter(String nameRegex, ResourceNameCritterType type)
            throws PatternSyntaxException {
            // will throw a PatternSyntaxException if there is something
            // wrong with nameRegex
            Pattern.compile(nameRegex);
            _nameRegex = nameRegex;

            List c = new ArrayList(1);
            c.add(new StringCritterProp(PROP_NAME, _nameRegex));
            _props = Collections.unmodifiableList(c);
            _type = type;
        }

        public List getProps() {
            return _props;
        }

        public String getSql(CritterTranslationContext ctx, String resourceAlias) {
            return ctx.getHQDialect().getRegExSQL(resourceAlias + ".name",
                ":@resourceName@", true, false);
        }

        public String getSqlJoins(CritterTranslationContext ctx,
            String resourceAlias) {
            return "";
        }

        public void bindSqlParams(CritterTranslationContext ctx, Query q) {
            q.setParameter(ctx.escape("resourceName"), _nameRegex);
        }

        public CritterType getCritterType() {
            return _type;
        }

        public String getNameRegex() {
            return _nameRegex;
        }

        public String getConfig() {
            Object[] args = { _nameRegex };
            return _type.getInstanceConfig().format(args);
        }

        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ResourceNameCritter)) return false;
        
            // make assumptions explicit
            assert _nameRegex != null;
        
            ResourceNameCritter critter = (ResourceNameCritter) other;
            if (!_nameRegex.equals(critter._nameRegex)) return false;
            return true;
        }

        public int hashCode() {
            int result = _nameRegex != null ? _nameRegex.hashCode() : 0;
            return result;
        }
        
        public boolean meets(Resource resource) {
            return resource.getName().matches(_nameRegex);
         }
    }
}
