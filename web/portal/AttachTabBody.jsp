<%@ page language="java" %>
<%@ page errorPage="/common/Error.jsp" %>
<%@ taglib uri="jstl-c" prefix="c" %>
<%@ taglib uri="struts-html-el" prefix="html" %>
<%@ taglib uri="struts-tiles" prefix="tiles" %>

<!-- get the eid and mode here for the parent portal action and use that action instead of mastheadattach -->
<div style="display:none;">
<c:out value="${resourceViewTabAttachments}"></c:out> ---
<c:out value="${resourceViewTabAttachment.plugin.name}"></c:out>
<div style="padding:2px" id="SubTabSource">
<c:forEach var="attachment" items="${resourceViewTabAttachments}">
    <c:choose>
    <c:when test="${param.id eq attachment.attachment.id}">
    <div style="padding:1px;border:1px solid rgb(255, 114, 20);margin-right:2px;width: 100px; float: left;text-align: center;"><a href="<html:rewrite page="/TabBodyAttach.do?id=${attachment.attachment.id}&mode=${param.mode}&eid=${param.eid}"/>"><c:out value="${attachment.HTML}"/></a></div>
    </c:when>
    <c:otherwise>
    <div style="padding:1px;border:1px solid gray;margin-right:2px;width: 100px; float: left;text-align: center;"><a href="<html:rewrite page="/TabBodyAttach.do?id=${attachment.attachment.id}&mode=${param.mode}&eid=${param.eid}"/>"><c:out value="${attachment.HTML}"/></a></div>
    </c:otherwise>
    </c:choose>
</c:forEach>
</div>
</div>
<c:choose>
<c:when test="${resourceViewTabAttachment ne null}">
	<div id="attachPointContainer" style="padding:4px;">
		<c:url var="attachUrl" context="/hqu/${resourceViewTabAttachment.plugin.name}" value="${resourceViewTabAttachment.path}"/>
		<c:import url="${attachUrl}?attachId=${param.id}"/>
	</div>
</c:when>
<c:when test="${empty resourceViewTabAttachments}">
	<div style="padding: 100px 0px; color: gray; font-size: 15px;text-align:center;">
	  No views are available for this resource
	</div>
</c:when>
<c:otherwise>
	<div class="viewSelectionNote">
	<img src="<html:rewrite page="/images/arrow_up_transparent.gif"/>"/><span>Please choose a view from the list above.</span>
	</div>
</c:otherwise>
</c:choose>

<script type="text/javascript">
dojo.addOnLoad(function(){
    dojo.byId("SubTabTarget").innerHTML = dojo.byId("SubTabSource").innerHTML;
});
</script>
