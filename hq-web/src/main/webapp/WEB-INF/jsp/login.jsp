<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://www.springframework.org/tags" prefix="spring" %>

<div id="loginPanel">
	<form id="loginForm" name="loginForm" action="<spring:url value="/j_spring_security_check" />" method="POST">
		<div class="fieldsetTitle"><fmt:message key="login.signin.message" /></div>
		<div class="fieldsetNote"><fmt:message key="login.signin.instructions" /></div>
		<fieldset>
			<c:if test="${not empty param.authfailed}">
				<p>
					<div class="msgPanel msgError">
						<c:choose>
							<c:when test="${not empty errorMessage}">
								${errorMessage}
							</c:when>
							<c:otherwise>
								<fmt:message key="login.error.login" />								
							</c:otherwise>
						</c:choose>
					</div>
				</p>
			</c:if>
			<div class="fieldRow">
				<label for="j_username"><fmt:message key="login.field.username" /></label> 
				<input style="width: 75%;" id="usernameInput" type="text" id="j_username" name="j_username" value="" />
			</div>
			<div class="fieldRow">
				<label for="j_password"><fmt:message key="login.field.password" /></label> 
				<input style="width: 75%;" id="passwordInput" type="password" id="j_password" name="j_password" />
			</div>
			<div class="submitButtonContainer">
				<input id="submit" type="submit" name="submit" class="button42" value="<fmt:message key="login.signin" />" />
				<c:if test="${guestEnabled}">
					<div class="guestUserLinkContainer">
						<fmt:message key="login.or" />&nbsp;<a href="#" id="guestLoginLink" class="guestUser"><fmt:message key="login.signInAsGuest" /></a>
					</div>
				</c:if>
			</div>
		</fieldset>
	</form>
</div>
<script>
	dojo.require("dijit.Dialog");
	
	dojo.addOnLoad(function() {
		var username = dojo.byId("usernameInput");
		var password = dojo.byId("passwordInput");

		dojo.connect(username, "onfocus", function(e) { dojo.addClass(e.target.parentNode, "active"); });
		dojo.connect(username, "onblur", function(e) { dojo.removeClass(e.target.parentNode, "active"); });

		dojo.connect(password, "onfocus", function(e) { dojo.addClass(e.target.parentNode, "active"); });
		dojo.connect(password, "onblur", function(e) { dojo.removeClass(e.target.parentNode, "active"); });

		username.focus();

		<c:if test="${guestEnabled}">
			dojo.connect(dojo.byId("guestLoginLink"), "onclick", function() {
				var username = dojo.byId("usernameInput");
				var password = dojo.byId("passwordInput");

				username.value = "${guestUsername}";
				document.forms["loginForm"]["submit"].click();
				username.disabled = true;
				password.disabled = true;
			});
		</c:if>

		var dialog = new dijit.Dialog("loginPanel");
		
		dojo.style(dialog.closeButtonNode, "visibility", "hidden");
		dialog.setContent(dojo.byId("loginPanel"));
		dialog.show();
	});
</script>