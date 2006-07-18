<%@ page language="java" %>
<%@ page errorPage="/common/Error.jsp" %>
<%@ taglib uri="struts-html-el" prefix="html" %>
<%@ taglib uri="struts-tiles" prefix="tiles" %>
<%@ taglib uri="jstl-fmt" prefix="fmt" %>
<%@ taglib uri="jstl-c" prefix="c" %>
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


<!--  GENERAL PROPERTIES TITLE -->
<tiles:insert definition=".header.tab">
  <tiles:put name="tabKey" value="resource.application.inventory.ApplicationProperties"/>
</tiles:insert>
<!--  /  -->


<!--  TYPE AND HOST PROPERTIES CONTENTS -->
<table width="100%" cellpadding="0" cellspacing="0" border="0">
	<tr>
		<td width="20%" class="BlockLabel"><fmt:message key="resource.application.inventory.properties.applicationType"/></td>
		<td width="30%" class="BlockContent"><c:out value="${Resource.applicationType.name}"/></td>
		<td width="20%" class="BlockLabel"><fmt:message key="resource.application.inventory.properties.businessContact"/></td>
		<td width="30%" class="BlockContent"><c:out value="${Resource.businessContact}"/></td>
	</tr>
	<tr>
		<td width="20%" class="BlockLabel"><fmt:message key="resource.application.inventory.properties.engContact"/></td>
		<td width="30%" class="BlockContent"><c:out value="${Resource.engContact}"/></td>
		<td width="20%" class="BlockLabel"><fmt:message key="resource.application.inventory.properties.opsContact"/></td>
		<td width="30%" class="BlockContent"><c:out value="${Resource.opsContact}"/></td>
	</tr>
	<tr>
      <td colspan="4" class="BlockBottomLine"><html:img page="/images/spacer.gif" width="1" height="1" border="0"/></td>
    </tr>
</table>

<!--  /  -->
