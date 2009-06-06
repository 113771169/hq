<%@ page language="java" %>
<%@ page errorPage="/common/Error.jsp" %>
<%@ taglib uri="struts-html-el" prefix="html" %>
<%@ taglib uri="jstl-fmt" prefix="fmt" %>
<%@ taglib uri="struts-tiles" prefix="tiles" %>
<%@ taglib uri="jstl-c" prefix="c" %>
<%@ taglib uri="hq" prefix="hq" %>
<%--
  NOTE: This copyright does *not* cover user programs that use HQ
  program services by normal system calls through the application
  program interfaces provided as part of the Hyperic Plug-in Development
  Kit or the Hyperic Client Development Kit - this is merely considered
  normal use of the program, and does *not* fall under the heading of
  "derived work".
  
  Copyright (C) [2004-2008], Hyperic, Inc.
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

<c:if test="${not empty escalation}">
<!-- Content Block Title: Notification -->
<tiles:insert definition=".header.tab">
  <tiles:put name="tabKey" value="monitoring.events.MiniTabs.Escalation"/>
</tiles:insert>

<table cellpadding="0" cellspacing="0" border="0" width="100%" class="TableBottomLine" style="margin-bottom: 10px;">
  <tr>
    <td width="20%" class="BlockLabel"><fmt:message key="common.header.EscalationName"/></td>
    <td class="BlockContent" colspan="2"><c:out value="${escalation.name}"/></td>
  </tr>
  <tr>
    <td colspan="3"  class="BlockContent">
      <tiles:insert page="/resource/common/monitor/alerts/config/ViewEscalation.jsp">
        <tiles:put name="chooseScheme" value="false"/>
      </tiles:insert>
    </td>
  </tr>
  <tr>
    <td width="20%" class="BlockLabel" style="padding-left: 4px;"><fmt:message key="common.label.EscalationActionLogs"/></td>
    <td class="BlockContent" colspan="2">&nbsp;</td>
  </tr>
  <c:forEach var="log" varStatus="status" items="${alert.escalationLogs}">
  <tr>
    <td width="20%" class="BlockLabel" style="padding-bottom:3px;">
      <span style="color:#333333;"><hq:dateFormatter value="${log.timeStamp}"/> - </span>
    </td>
    <td colspan="2"  class="BlockContent" style="padding-left: 4px;padding-bottom:3px;">
      <c:out value="${log.detail}"/>
    </td>
  </tr>
  </c:forEach>
  <c:if test="${alert.acknowledgeable}">
  <tr>
    <td width="20%" class="BlockLabel" style="border-top: solid #D5D8DE 1px;" valign="top" align="right"><span class="BoldText"><fmt:message key="resource.common.alert.ackNote"/></span></td>
    <td colspan="2" class="BlockContent" style="border-top: solid #D5D8DE 1px;">
        <html:textarea property="ackNote" cols="70" rows="4"/>	  
    </td>
  </tr>
  </c:if>
  <tr>
    <td width="20%" class="BlockLabel">&nbsp;</td>
    <td width="80%" class="BlockContent">
		 <c:if test="${escalation.pauseAllowed && alert.acknowledgeable}">
			  <div id="AlertEscalationOption" syle="text-align:left;">
			     <input type="checkbox" name="pause" value="true" checked="checked" onclick="dojo11.byId('pauseTimeSel').disabled = !this.checked;" />&nbsp;<fmt:message key="alert.escalation.pause"/>
			  </div>	  
		  </c:if>&nbsp;
          <div style="text-align:left;">
			  <tiles:insert page="/common/components/ActionButton.jsp">
  			     <tiles:put name="labelKey" value="resource.common.alert.action.acknowledge.label"/>
                 <tiles:put name="buttonClick">dojo.byId('mode').setAttribute('value', '<fmt:message key="resource.common.alert.action.acknowledge.label"/>'); document.forms[0].submit();</tiles:put>
                 <c:choose>
                    <c:when test="${alert.acknowledgeable}">
                       <tiles:put name="disabled" value="false"/>
                    </c:when>
                    <c:otherwise>
                       <tiles:put name="hidden" value="true"/>
                    </c:otherwise>
                 </c:choose>
              </tiles:insert>
          </div>
    </td>
  </tr>
</table>

<script type="text/javascript">
  var isButtonClicked = false;
  
  function checkSubmit() {
    if (isButtonClicked) {
      alert('<fmt:message key="error.PreviousRequestEtc"/>');
      return false;
    }
  }

  var escalationSpan = dojo11.byId("AlertEscalationOption");
  if (escalationSpan != null) {
	  escalationSpan.appendChild(hyperic.form.createEscalationPauseOptions({id: "pauseTimeSel", name: "pauseTime"}, <c:out value="${escalation.maxPauseTime}"/>));
  }
</script>

</c:if>
