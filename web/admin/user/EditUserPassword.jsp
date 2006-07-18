<%@ page language="java" %>
<%@ page errorPage="/common/Error.jsp" %>
<%@ taglib uri="jstl-fmt" prefix="fmt" %>
<%@ taglib uri="struts-tiles" prefix="tiles" %>
<%@ taglib uri="struts-html-el" prefix="html" %>
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


<c:set var="fullName" value="${User.firstName} ${User.lastName}"/>
<tiles:importAttribute name="administrator"/>
<!-- FORM -->
<html:form action="/admin/user/EditPassword">  

  <!--  PAGE TITLE -->
  <tiles:insert definition=".page.title.admin.user">
    <tiles:put name="titleKey" value="common.title.Edit"/>  
    <tiles:put name="titleName" beanName="fullName"/>   
  </tiles:insert>
  <!--  /  -->

  <!--  HEADER TITLE -->
  <tiles:insert definition=".header.tab">  
    <tiles:put name="tabKey" value="admin.user.changePassword.ChangePasswordTab"/>  
  </tiles:insert>

  <!--  /  -->
  <c:set var="tmpu" value="${param.u}" />
  <tiles:insert page="/admin/user/UserPasswordForm.jsp">
    <tiles:put name="userId" beanName="tmpu"/>    
    <tiles:put name="administrator" beanName="administrator"/>    
  </tiles:insert>

  <tiles:insert definition=".toolbar.empty"/>  

  <tiles:insert definition=".form.buttons"/>

  <tiles:insert definition=".page.footer"/>

</html:form>
<!-- /  -->
