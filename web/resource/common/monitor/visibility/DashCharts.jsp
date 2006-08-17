<%@ page language="java" %>
<%@ page errorPage="/common/Error.jsp" %>
<%@ taglib uri="struts-html-el" prefix="html" %>
<%@ taglib uri="jstl-c" prefix="c" %>
<%@ taglib uri="jstl-fmt" prefix="fmt" %>
<%@ taglib uri="hq" prefix="hq" %>
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


<c:set var="eid" value="${param.eid}"/>
<c:set var="ctype" value="${param.ctype}"/>

<hq:constant 
  classname="org.hyperic.hq.bizapp.shared.uibeans.MetricDisplayConstants" 
  symbol="HIGH_RANGE_KEY"
  var="high"/>
<hq:constant 
  classname="org.hyperic.hq.bizapp.shared.uibeans.MetricDisplayConstants" 
  symbol="LOW_RANGE_KEY"
  var="low"/>
<hq:constant var="GROUP" 
    classname="org.hyperic.hq.appdef.shared.AppdefEntityConstants" 
    symbol="APPDEF_TYPE_GROUP"/>

<html>
<head>
<META Http-Equiv="Cache-Control" Content="no-cache">
<META Http-Equiv="Pragma" Content="no-cache">
<META Http-Equiv="Expires" Content="0">

<script src="<html:rewrite page="/js/functions.js"/>" type="text/javascript"></script>
<script src="<html:rewrite page="/js/prototype.js"/>" type="text/javascript"></script>
<script src="<html:rewrite page="/js/effects.js"/>" type="text/javascript"></script>
<script src="<html:rewrite page="/js/rico.js"/>" type="text/javascript"></script>

<script language="JavaScript">
  var baseUrl = "<html:rewrite page="/resource/common/monitor/visibility/IndicatorCharts.do"/>";

  // Register the remove metric chart method
  ajaxEngine.registerRequest( 'indicatorCharts', baseUrl );

  function removeMetric(metric) {
    ajaxEngine.sendRequest(
        'indicatorCharts',
        'metric=' + metric,
        'action=remove',
        'eid=' + '<c:out value="${eid}"/>', 
        <c:if test="${not empty ctype}">
          'ctype=' + '<c:out value="${ctype}"/>',
        </c:if>
        'view=' + '<c:out value="${IndicatorViewsForm.view}"/>');
    new Effect.Fade(metric);
  }

  function moveMetricUp(metric) {
    ajaxEngine.sendRequest(
        'indicatorCharts',
        'metric=' + metric,
        'action=moveUp',
        'eid=' + '<c:out value="${eid}"/>', 
        <c:if test="${not empty ctype}">
          'ctype=' + '<c:out value="${ctype}"/>',
        </c:if>
        'view=' + '<c:out value="${IndicatorViewsForm.view}"/>');
    var root = $('root');
    var elem = $(metric);
    moveElementUp(elem, root);
  }

  function moveMetricDown(metric) {
    ajaxEngine.sendRequest(
        'indicatorCharts',
        'metric=' + metric,
        'action=moveDown',
        'eid=' + '<c:out value="${eid}"/>', 
        <c:if test="${not empty ctype}">
          'ctype=' + '<c:out value="${ctype}"/>',
        </c:if>
        'view=' + '<c:out value="${IndicatorViewsForm.view}"/>');
    var root = $('root');
    var elem = $(metric);
    moveElementDown(elem, root);
  }

</script>

<link rel=stylesheet href="<html:rewrite page="/css/win.css"/>" type="text/css">
</head>

<body bgcolor="#DBE3F5" onload="$('slowScreenSplash').style.display = 'none';">
<!-- <c:out value="${IndicatorViewsForm.addMetric}"/> -->
<c:forEach var="id" items="${IndicatorViewsForm.metric}">
<!-- <c:out value="metric: ${id}"/> -->
</c:forEach>

<ul id="root" class="boxy">
<c:forEach var="metric" varStatus="status" items="${chartDataKeys}">
  <c:url var="chartLink" value="/resource/common/monitor/Visibility.do">
    <c:param name="m" value="${metric.templateId}"/>
    <c:param name="eid" value="${metric.entityId}"/>
    <c:choose>
      <c:when test="${not empty metric.childType}">
        <c:param name="mode" value="chartSingleMetricMultiResource"/>
        <c:param name="ctype" value="${metric.childType}"/>
      </c:when>
      <c:when test="${metric.entityId.type == GROUP}">
        <c:param name="mode" value="chartSingleMetricMultiResource"/>
      </c:when>
      <c:otherwise>
        <c:param name="mode" value="chartSingleMetricSingleResource"/>
      </c:otherwise>
    </c:choose>
  </c:url>

  <c:url var="chartImg" value="/resource/HighLowChart">
    <c:param name="imageWidth" value="647"/>
    <c:param name="imageHeight" value="100"/>
    <c:param name="tid" value="${metric.templateId}"/>
    <c:param name="eid" value="${metric.entityId}"/>
    <c:if test="${not empty metric.childType}">
      <c:param name="ctype" value="${metric.childType}"/>
    </c:if>
    <c:param name="now" value="${IndicatorViewsForm.timeToken}"/>
    <c:param name="unitUnits" value="${metric.unitUnits}"/>
    <c:param name="unitScale" value="${metric.unitScale}"/>
  </c:url>

  <li id="<c:out value="${metric.templateId}"/>">
  <table width="650" border="0" cellpadding="2" bgcolor="#DBE3F5">
  <tr>
    <td>
      <table width="100%" border="0" cellpadding="0" cellspacing="1" style="margin-top: 1px; margin-bottom: 1px;">
        <tr>
          <td>
          <font class="BoldText">
            <a href="<c:out value="${chartLink}"/>" target="_top"><c:out value="${metric.label}"/></a>
          </font>
          <font class="FooterSmall">
             <fmt:message key="common.value.parenthesis">
               <fmt:param value="${metric.metricSource}"/>
             </fmt:message>
          </font></td>
          <td width="15%" nowrap="true"><font class="BoldText"><fmt:message key="resource.common.monitor.visibility.LowTH"/></font>: <c:out value="${metric.minMetric.valueFmt}"/></td>
          <td width="15%" nowrap="true"><font class="BoldText"><fmt:message key="resource.common.monitor.visibility.AvgTH"/></font>: <c:out value="${metric.avgMetric.valueFmt}"/></td>
          <td width="15%" nowrap="true"><font class="BoldText"><fmt:message key="resource.common.monitor.visibility.PeakTH"/></font>: <c:out value="${metric.maxMetric.valueFmt}"/></td>
          <td width="1%"><a href="javascript:moveMetricUp('<c:out value="${metric.templateId}"/>')"><html:img page="/images/dash_icon_up.gif" border="0"/></a></td>
          <td width="1%"><a href="javascript:moveMetricDown('<c:out value="${metric.templateId}"/>')"><html:img page="/images/dash_icon_down.gif" border="0"/></a></td>
          <td width="1%"><a href="javascript:removeMetric('<c:out value="${metric.templateId}"/>')"><html:img page="/images/dash-icon_delete.gif" border="0"/></a></td>
        </tr>
      </table>
    </td>
  </tr>
</table>
<html:img src="${chartImg}" border="0"/>
</li>
</c:forEach>
</ul>

<div id="slowScreenSplash" align="center" class="dialog" style="top:15%;left:25%;padding:5px;">
<fmt:message key="resource.common.monitor.visibility.request.wait"/>
</div>

</body>

