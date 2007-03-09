<%@ page language="java" %>
<%@ page errorPage="/common/Error.jsp" %>
<%@ taglib uri="jstl-c" prefix="c" %>
<%@ taglib uri="jstl-fmt" prefix="fmt" %>
<%@ taglib uri="struts-html-el" prefix="html" %>
<%@ taglib uri="struts-tiles" prefix="tiles" %>
<%@ taglib uri="struts-bean" prefix="bean" %>
<%--
  NOTE: This copyright does *not* cover user programs that use HQ
  program services by normal system calls through the application
  program interfaces provided as part of the Hyperic Plug-in Development
  Kit or the Hyperic Client Development Kit - this is merely considered
  normal use of the program, and does *not* fall under the heading of
  "derived work".
  
  Copyright (C) [2004, 2005, 2006], Hyperic, Inc.
  This file is part of HQ.
  
  HQ is free software; you can redistribute it and/or modify
  it under the terms version 2 of the GNU General Public License as
  published by the Free Software Foundation. This program is distributed
  in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
  even the implied warranty of MERCHANTABILITY or FITNESS FOR A
  PARTICULAR PURPOSE. See the GNU General Public License for more
  details.
  
  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
  USA.
 --%>

<tiles:insert definition=".header.tab">
  <tiles:put name="tabKey" value="admin.home.CompanyWideSettingsTab"/>
  <tiles:put name="icon"><html:img page="/images/application_edit.gif" alt=""/></tiles:put>
</tiles:insert>

<!--  GENERAL PROPERTIES CONTENTS -->
<table width="100%" cellpadding="0" cellspacing="0" border="0" class="TableBottomLine" style="margin-bottom: 24px;">
<c:if test="${useroperations['administerCAM']}">
	<tr>
		<td width="20%" class="BlockLabel"><fmt:message key="admin.home.Settings"/></td>
		<td class="BlockContent"><html:link page="/admin/config/Config.do?mode=edit"><fmt:message key="admin.home.ServerConfig"/></html:link><fmt:message key="inform.config.admin.HQServerConfig"/></td>
	</tr>
</c:if>
	<tr>
		<td  width="20%" class="BlockLabel"></td>
		<td class="BlockContent"><html:link page="/admin/config/Config.do?mode=monitor"><fmt:message key="admin.home.ResourceTemplates"/></html:link>
            <tiles:importAttribute name="defaultMsg" ignore="true"/>
            <c:choose>
                <c:when test="${not empty defaultMsg}">
                    <fmt:message key="${defaultMsg}"/>
                </c:when>
                <c:otherwise>
                    <fmt:message key="inform.config.admin.MonitoringDefaults"/>
                </c:otherwise>
            </c:choose>
        </td>
	</tr>
    <c:if test="${useroperations['administerCAM']}">
    <tr>
		<td class="BlockLabel"></td>
		<td class="BlockContent"><html:link page="/admin/config/Config.do?mode=escalate"><fmt:message key="admin.home.EscalationSchemes"/></html:link><fmt:message key="inform.config.admin.EscalationScheme"/></td>
	</tr>
    </c:if>
</table>
<!--  /  -->


