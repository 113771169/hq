<%@ page pageEncoding="UTF-8"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ page errorPage="/common/Error.jsp" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://struts.apache.org/tags-html-el" prefix="html" %>

<%--
  NOTE: This copyright does *not* cover user programs that use HQ
  program services by normal system calls through the application
  program interfaces provided as part of the Hyperic Plug-in Development
  Kit or the Hyperic Client Development Kit - this is merely considered
  normal use of the program, and does *not* fall under the heading of
  "derived work".
  
  Copyright (C) [2004-2009], Hyperic, Inc.
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
<link rel="stylesheet" href="<html:rewrite page="/js/dojo/1.1/dojo/resources/dojo.css"/>" type="text/css"/>
<link rel="stylesheet" href="<html:rewrite page="/js/dojo/1.1/dijit/themes/tundra/tundra.css"/>" type="text/css"/>
<link rel="shortcut icon" href="<html:rewrite page="/images/4.0/icons/favicon.ico"/>"/>
<link rel="stylesheet" href="<html:rewrite page="/css/win.css"/>" type="text/css"/>
<link rel="stylesheet" href="<html:rewrite page="/css/HQ_40.css"/>" type="text/css"/>
<!--[if IE 7]>
<link rel="stylesheet" href="<html:rewrite page="/css/ie7.css"/>" type="text/css"/>
<![endif]-->
<!--[if lte IE 6]>
<link rel="stylesheet" href="<html:rewrite page="/css/ie6.css"/>" type="text/css"/>
<![endif]-->

<!-- TODO: ADxMenu.js script invocation must be moved to the "lte IE 6" section above once we start using the HTML5 doctype which will kick IE7 into strict mode. -->
<!--[if IE]>
<link rel="stylesheet" href="<html:rewrite page="/css/ie.css"/>" type="text/css"/>
<script type="text/javascript" src="/js/ADxMenu.js"></script>
<![endif]-->

<script type="text/javascript">
djConfig = { isDebug: false, locale: 'en-us' }
</script>
<script type="text/javascript" src="<html:rewrite page='/js/dojo/0.4.3/dojo.js.uncompressed.js'/>"></script> 
<script type="text/javascript">
djConfig.parseOnLoad = true;
djConfig.baseUrl = '/js/dojo/1.1/dojo/';
djConfig.scopeMap = [
        ["dojo", "dojo11"],
        ["dijit", "dijit11"],
        ["dojox", "dojox11"]
    ];
</script>
<script src="<html:rewrite page='/js/dojo/1.1/dojo/dojo.js.uncompressed.js'/>" type="text/javascript"></script>
<script type="text/javascript">
    var imagePath = "<html:rewrite page="/images/"/>";
    dojo11.require('dojo.date');
</script>
<script src="<html:rewrite page='/js/prototype.js'/>" type="text/javascript"></script>
<script src="<html:rewrite page='/js/popup.js'/>" type="text/javascript"></script>
<script src="<html:rewrite page='/js/diagram.js'/>" type="text/javascript"></script>
<script src="<html:rewrite page='/js/functions.js'/>" type="text/javascript"></script>
<script src="<html:rewrite page='/js/lib/lib.js'/>" type="text/javascript"></script>
<script src="<html:rewrite page='/js/lib/charts.js'/>" type="text/javascript"></script>
<script type="text/javascript">
	hyperic.data.escalation = {};
	hyperic.data.escalation.pauseSelect = document.createElement("select");
	hyperic.data.escalation.pauseSelect.options[0] = new Option("5 <fmt:message key="alert.config.props.CB.Enable.TimeUnit.1"/>", "300000");
	hyperic.data.escalation.pauseSelect.options[1] = new Option("10 <fmt:message key="alert.config.props.CB.Enable.TimeUnit.1"/>", "600000");
	hyperic.data.escalation.pauseSelect.options[2] = new Option("20 <fmt:message key="alert.config.props.CB.Enable.TimeUnit.1"/>", "1200000");
	hyperic.data.escalation.pauseSelect.options[3] = new Option("30 <fmt:message key="alert.config.props.CB.Enable.TimeUnit.1"/>", "1800000");
	hyperic.data.escalation.pauseSelect.options[4] = new Option("45 <fmt:message key="alert.config.props.CB.Enable.TimeUnit.1"/>", "2700000");
	hyperic.data.escalation.pauseSelect.options[5] = new Option("60 <fmt:message key="alert.config.props.CB.Enable.TimeUnit.1"/>", "3600000");
	hyperic.data.escalation.pauseSelect.options[6] = new Option("2 <fmt:message key="alert.config.props.CB.Enable.TimeUnit.2"/>", "7200000");
	hyperic.data.escalation.pauseSelect.options[7] = new Option("4 <fmt:message key="alert.config.props.CB.Enable.TimeUnit.2"/>", "14400000");
	hyperic.data.escalation.pauseSelect.options[8] = new Option("8 <fmt:message key="alert.config.props.CB.Enable.TimeUnit.2"/>", "28800000");
	hyperic.data.escalation.pauseSelect.options[9] = new Option("12 <fmt:message key="alert.config.props.CB.Enable.TimeUnit.2"/>", "43200000");
	hyperic.data.escalation.pauseSelect.options[10] = new Option("24 <fmt:message key="alert.config.props.CB.Enable.TimeUnit.2"/>", "86400000");
	hyperic.data.escalation.pauseSelect.options[11] = new Option("48 <fmt:message key="alert.config.props.CB.Enable.TimeUnit.2"/>", "172800000");
	hyperic.data.escalation.pauseSelect.options[12] = new Option("72 <fmt:message key="alert.config.props.CB.Enable.TimeUnit.2"/>", "259200000");
	hyperic.data.escalation.pauseSelect.options[13] = new Option("<fmt:message key="alert.config.props.CB.Enable.UntilFixed"/>", "<%= Long.MAX_VALUE %>");
</script>