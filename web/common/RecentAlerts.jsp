<?xml version="1.0" encoding="ISO-8859-1"?>
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
<%@ page language="java" contentType="text/xml" %>
<%@ taglib uri="hq" prefix="hq" %>
<%@ taglib uri="struts-html-el" prefix="html" %>
<%@ taglib uri="jstl-c" prefix="c" %>
<%@ taglib uri="jstl-fmt" prefix="fmt" %>
<% response.setHeader("Pragma","no-cache");%>
<% response.setHeader("Cache-Control","no-store");%>
<% response.setDateHeader("Expires",-1);%>
<hq:recentAlerts var="recentAlerts" sizeVar="recentAlertsSize" maxAlerts="2"/>
<ajax-response>
  <response type="element" id="recentAlerts">
  <table><tr><td nowrap="true">
<c:choose>
  <c:when test="${recentAlertsSize > 0}">
    <ul class="boxy">
      <c:forEach var="alert" varStatus="status" items="${recentAlerts}">
        <c:url var="alertUrl" value="/alerts/Alerts.do">
          <c:param name="mode" value="viewAlert"/>
        </c:url>
        <li class="MastheadContent"><html:link href="${alertUrl}&amp;eid=${alert.type}:${alert.rid}&amp;a=${alert.id}" styleClass="MastheadLink"><hq:dateFormatter value="${alert.ctime}"/></html:link>
        <fmt:message key="common.label.Dash"/>
        <c:out value="${alert.resourceName}"/><fmt:message key="common.label.Colon"/>
        <c:out value="${alert.name}"/></li>
      </c:forEach>
    </ul>
  </c:when>
  <c:otherwise>
    <span class="MastheadContent"><fmt:message key="header.NoRecentAlerts"/></span>
  </c:otherwise>
</c:choose>
  </td></tr></table>
  </response>
</ajax-response>
