<%@ page language="java" %>
<%@ page errorPage="/common/Error.jsp" %>
<%@ taglib uri="struts-html-el" prefix="html" %>
<%@ taglib uri="struts-logic-el" prefix="logic" %>
<%@ taglib uri="struts-tiles" prefix="tiles" %>
<%@ taglib uri="jstl-c" prefix="c" %>
<%@ taglib uri="jstl-fmt" prefix="fmt" %>
<%@ taglib uri="hq" prefix="hq" %>
<%@ taglib uri="display" prefix="display" %>
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


<script language="JavaScript" src="<html:rewrite page="/js/listWidget.js"/>" type="text/javascript"></script>
<script type="text/javascript">
var pageData = new Array();
</script>

<c:set var="entityId" value="${Resource.entityId}"/>
<c:set var="selfAction" value="/resource/server/Inventory.do?mode=view&rid=${Resource.id}&type=${entityId.type}"/>


<hq:pageSize var="pageSize"/>

<tiles:insert definition=".page.title.resource.inventory.full">
    <tiles:put name="titleName"><hq:inventoryHierarchy resource="${entityId.appdefKey}" /></tiles:put>
    <tiles:put name="resource" beanName="Resource"/>
    <tiles:put name="resourceOwner" beanName="ResourceOwner"/>
    <tiles:put name="resourceModifier" beanName="ResourceModifier"/>
</tiles:insert>

<!-- CONTROL BAR -->
<hq:constant classname="org.hyperic.hq.ui.Constants" 
        symbol="CONTROL_ENABLED_ATTR" var="CONST_CONTROLLABLE"/>

<c:set var="canControl" value="${requestScope[CONST_CONTROLLABLE]}"/>
<c:choose>
<c:when test="${canControl}">
<tiles:insert definition=".tabs.resource.server.inventory">
    <tiles:put name="resource" beanName="Resource" />
    <tiles:put name="resourceId" beanName="Resource" beanProperty="id"/>
    <tiles:put name="resourceType" beanName="entityId" beanProperty="type"/>
</tiles:insert>
</c:when>
<c:otherwise>
<tiles:insert definition=".tabs.resource.server.inventory.nocontrol">
    <tiles:put name="resource" beanName="Resource" />
    <tiles:put name="resourceId" beanName="Resource" beanProperty="id"/>
    <tiles:put name="resourceType" beanName="entityId" beanProperty="type"/>
</tiles:insert>
</c:otherwise>
</c:choose>
&nbsp;<br>

<tiles:insert definition=".portlet.confirm"/>
<tiles:insert definition=".portlet.error"/>

<tiles:insert definition=".resource.common.inventory.generalProperties.view">
  <tiles:put name="resource" beanName="Resource"/>
  <tiles:put name="resourceOwner" beanName="ResourceOwner"/>
  <tiles:put name="resourceModifier" beanName="ResourceModifier"/>
</tiles:insert>
      &nbsp;<br>
<tiles:insert page="/resource/server/inventory/ViewTypeAndHostProperties.jsp"/>
&nbsp;<br>

<!-- new -->

<tiles:insert definition=".resource.server.inventory.serviceCounts">
  <tiles:put name="serviceCount" beanName="NumChildResources"/>
  <tiles:put name="serviceTypeMap" beanName="ResourceTypeMap"/>
</tiles:insert>
      &nbsp;<br>

<html:form action="/resource/server/inventory/RemoveServices">
<input type="hidden" name="rid" value="<c:out value="${Resource.id}"/>"/>
<input type="hidden" name="type" value="<c:out value="${entityId.type}"/>"/>
<tiles:insert definition=".resource.server.inventory.services">
  <tiles:put name="server" beanName="Resource"/>
  <tiles:put name="services" beanName="ChildResources"/>
  <tiles:put name="serviceCount" beanName="NumChildResources"/>
  <tiles:put name="selfAction" beanName="selfAction"/>
  <tiles:put name="autoInventory" beanName="autoInventory"/>
</tiles:insert>
</html:form>

      &nbsp;<br>
<html:form action="/resource/server/inventory/RemoveGroups">
<input type="hidden" name="rid" value="<c:out value="${Resource.id}"/>"/>
<input type="hidden" name="type" value="<c:out value="${entityId.type}"/>"/>
<tiles:insert definition=".resource.common.inventory.groups">
  <tiles:put name="resource" beanName="Resource"/>
  <tiles:put name="groups" beanName="AllResGrps"/>
  <tiles:put name="selfAction" beanName="selfAction"/>
</tiles:insert>
</html:form>

      &nbsp;<br>

<tiles:insert definition=".resource.common.inventory.viewConfigProperties">
    <tiles:put name="resource" beanName="Resource"/>
    <tiles:put name="resourceType" beanName="entityId" beanProperty="type"/>
    <tiles:put name="productConfigOptions" beanName="productConfigOptions"/>
    <tiles:put name="productConfigOptionsCount" beanName="productConfigOptionsCount"/>
    <tiles:put name="monitorConfigOptions" beanName="monitorConfigOptions"/>
    <tiles:put name="monitorConfigOptionsCount" beanName="monitorConfigOptionsCount"/>
    <tiles:put name="controlConfigOptions" beanName="controlConfigOptions"/>
    <tiles:put name="controlConfigOptionsCount" beanName="controlConfigOptionsCount"/>
    <tiles:put name="autodiscoveryMessageServiceList" beanName="autodiscoveryMessageServiceList"/>
</tiles:insert>



<tiles:insert definition=".page.footer"/>
