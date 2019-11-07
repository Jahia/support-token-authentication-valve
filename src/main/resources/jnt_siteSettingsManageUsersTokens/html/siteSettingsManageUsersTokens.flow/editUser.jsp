<%--@elvariable id="userProperties" type="org.jahia.modules.token.users.management.UserProperties"--%>
<%@ page language="java" contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="functions" uri="http://www.jahia.org/tags/functions" %>
<%@ taglib prefix="user" uri="http://www.jahia.org/tags/user" %>
<%--@elvariable id="currentNode" type="org.jahia.services.content.JCRNodeWrapper"--%>
<%--@elvariable id="out" type="java.io.PrintWriter"--%>
<%--@elvariable id="script" type="org.jahia.services.render.scripting.Script"--%>
<%--@elvariable id="scriptInfo" type="java.lang.String"--%>
<%--@elvariable id="workspace" type="java.lang.String"--%>
<%--@elvariable id="renderContext" type="org.jahia.services.render.RenderContext"--%>
<%--@elvariable id="currentResource" type="org.jahia.services.render.Resource"--%>
<%--@elvariable id="url" type="org.jahia.services.render.URLGenerator"--%>
<%--@elvariable id="mailSettings" type="org.jahia.services.mail.MailSettings"--%>
<%--@elvariable id="flowRequestContext" type="org.springframework.webflow.execution.RequestContext"--%>
<template:addResources type="javascript" resources="jquery.min.js,jquery-ui.min.js,jquery.blockUI.js,workInProgress.js,admin-bootstrap.js"/>
<template:addResources type="css" resources="jquery-ui.smoothness.css,jquery-ui.smoothness-jahia.css"/>
<fmt:message key="label.workInProgressTitle" var="i18nWaiting"/><c:set var="i18nWaiting" value="${functions:escapeJavaScript(i18nWaiting)}"/>
<div>
    <h2><fmt:message key="label.edit"/>&nbsp;${userProperties.displayName}</h2>
    <c:forEach items="${flowRequestContext.messageContext.allMessages}" var="message">
        <c:if test="${message.severity eq 'ERROR'}">
            <div class="alert alert-error">
                <button type="button" class="close" data-dismiss="alert">&times;</button>
                ${message.text}
            </div>
        </c:if>
    </c:forEach>
    <div class="box-1">
        <form action="${flowExecutionUrl}" method="post" id="editUser" autocomplete="off">
            <div class="container-fluid">
                <div class="row-fluid">
                    <div class="span4">
                        <label for="recipient"><fmt:message key="label.recipient"/></label>
                        <input class="span12" type="text" name="recipient" id="recipient" value="support@jahia.com" autocomplete="off">
                    </div>
                    <div class="span4">
                        <label for="description"><fmt:message key="label.description"/></label>
                        <input class="span12" type="text" name="description" id="description" value="Access for Jahia Support" autocomplete="off">
                    </div>
                    <div class="span4">
                        <label for="expiration"><fmt:message key="label.expiration"/></label>
                        <input class="span12" type="text" name="expiration" id="expiration" value="60" autocomplete="off">
                    </div>
                </div>
            </div>
            <fieldset>
                <div class="container-fluid">
                    <div class="row-fluid">
                        <div class="span12">
                            <button class="btn btn-primary" type="submit" name="_eventId_add" onclick="workInProgress('${i18nWaiting}'); return true;">
                                <i class="icon-ok icon-white"></i>
                                &nbsp;<fmt:message key='label.add'/>
                            </button>
                            <button class="btn" type="submit" name="_eventId_cancel">
                                <i class="icon-ban-circle"></i>
                                &nbsp;<fmt:message key='label.cancel'/>
                            </button>
                        </div>
                    </div>
                </div>
            </fieldset>
        </form>
        <fieldset id="groupsFields" title="<fmt:message key="siteSettings.user.token.list"/>">
            <div class="container-fluid">
                <div class="row-fluid">
                    <div class="span12">
                        <label for="groupsFields"><fmt:message key="siteSettings.user.token.list"/></label>
                        <select class="span12 fontfix" name="selectMember" size="6" multiple>
                            <c:forEach items="${userTokens}" var="token">
                                <option value="${token}">${token}</option>
                            </c:forEach>
                        </select>
                    </div>
                </div>
            </div>
        </fieldset>
        <form action="${flowExecutionUrl}" method="post" id="editUser" autocomplete="off">
            <fieldset>
                <div class="container-fluid">
                    <div class="row-fluid">
                        <div class="span12">
                            <button class="btn" type="submit" name="_eventId_clear">
                                <i class="icon-ban-circle"></i>
                                &nbsp;<fmt:message key='label.clear_all_tokens'/>
                            </button>
                        </div>
                    </div>
                </div>
            </fieldset>
        </form>
    </div>
</div>