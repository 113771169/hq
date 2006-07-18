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


<tiles:importAttribute name="multiMetric" ignore="true"/>
<tiles:importAttribute name="multiResource" ignore="true"/>

<c:if test="${empty multiMetric}">
<c:set var="multiMetric" value="false"/>
</c:if>
<c:if test="${empty multiResource}">
<c:set var="multiResource" value="false"/>
</c:if>

<!--  METRIC CHART TITLE -->
<tiles:insert definition=".header.tab">
<tiles:put name="tabKey" value="resource.common.monitor.visibility.chart.MetricChartTab"/>
</tiles:insert>
<!--  /  -->

<table width="100%" cellpadding="0" cellspacing="0" border="0" class="MonitorBlockContainer">
  <tr>
    <td>
      <!-- Table Content -->
      <table width="100%" cellpadding="0" cellspacing="0" border="0">
        <tr>
          <td class="MonitorChartBlock" colspan="3">
            <tiles:insert definition=".resource.common.monitor.visibility.charts.metric.chartparams">
                <tiles:put name="multiResource" beanName="multiResource"/>
                <tiles:put name="ctype" value="${ctype}"/>
            </tiles:insert>
          </td>
        </tr>
        <tr>
          <td width="50%" class="MonitorChartCell"
            rowspan="2"><html:img page="/images/spacer.gif" width="1"
            height="1" border="0"/></td>
          <td class="MonitorChartCell">
            <c:forEach var="i" varStatus="status" begin="0" end="${chartDataKeysSize - 1}">
            <c:url var="chartUrl" value="/resource/MetricChart">
            <c:param name="chartDataKey" value="${chartDataKeys[i]}"/>
            <c:param name="unitUnits" value="${chartedMetrics[i].unitUnits}"/>
            <c:param name="unitScale" value="${chartedMetrics[i].unitScale}"/>
            <c:param name="showPeak" value="${ViewChartForm.showPeak}"/>
            <c:param name="showHighRange" value="${ViewChartForm.showHighRange}"/>
            <c:param name="showValues" value="${ViewChartForm.showValues}"/>
            <c:param name="showAverage" value="${ViewChartForm.showAverage}"/>
            <c:param name="showLowRange" value="${ViewChartForm.showLowRange}"/>
            <c:param name="showLow" value="${ViewChartForm.showLow}"/>
            <c:param name="collectionType" value="${chartedMetrics[i].collectionType}"/>
            <c:param name="showEvents" value="${ViewChartForm.showEvents}"/>
            <c:param name="showBaseline" value="${ViewChartForm.showBaseline}"/>
            <c:param name="baseline" value="${chartedMetrics[i].baselineRaw}"/>
            <c:param name="highRange" value="${chartedMetrics[i].highRangeRaw}"/>
            <c:param name="lowRange" value="${chartedMetrics[i].lowRangeRaw}"/>
            </c:url>
            <b><fmt:message key="resource.common.monitor.visibility.chart.Metric"/></b>
            <c:out value="${chartedMetrics[i].metricName}"/><br>
            <html:img src="${chartUrl}" width="755" height="300" border="0"/>
            <c:if test="${!status.last}">&nbsp;<br><br></c:if>
            </c:forEach>
          </td>
          <td width="50%" class="MonitorChartCell"
            rowspan="2"><html:img page="/images/spacer.gif" width="1"
            height="1" border="0"/></td>
        </tr>
        <tr>
          <td class="MonitorChartCell">
            <tiles:insert definition=".resource.common.monitor.visibility.charts.metric.chartlegend"/>
          </td>
        </tr>
        <tr>
          <td class="MonitorChartBlock" colspan="3">
            <tiles:insert page="/resource/common/monitor/visibility/ChartTimeIntervalToolbar.jsp">
            <tiles:put name="rangeNow" beanName="ViewChartForm" beanProperty="rangeNow"/>
            <tiles:put name="begin" beanName="metricRange" beanProperty="begin"/>
            <tiles:put name="end" beanName="metricRange" beanProperty="end"/>
            </tiles:insert>
            &nbsp;<br>
          </td>
        </tr>
        <tr>
          <td colspan="3">
            <tiles:insert definition=".resource.common.monitor.visibility.embeddedMetricDisplayRange">
              <tiles:put name="form" beanName="ViewChartForm"/>
              <tiles:put name="formName" value="ViewChartForm"/>
            </tiles:insert>
          </td>
        </tr>
      </table>
      <!--  /  -->
    </td>
  </tr>
</table>
