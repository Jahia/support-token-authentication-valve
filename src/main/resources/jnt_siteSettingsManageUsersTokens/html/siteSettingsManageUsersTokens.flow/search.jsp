<%@ page language="java" contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="functions" uri="http://www.jahia.org/tags/functions" %>
<%@ taglib prefix="user" uri="http://www.jahia.org/tags/user" %>
<%@ page import="org.jahia.settings.SettingsBean "%>
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
<%--@elvariable id="flowExecutionUrl" type="java.lang.String"--%>
<%--@elvariable id="searchCriteria" type="org.jahia.modules.token.users.management.SearchCriteria"--%>
<template:addResources type="javascript" resources="jquery.min.js,jquery-ui.min.js,admin-bootstrap.js,jquery.metadata.js,jquery.tablesorter.js,jquery.tablecloth.js"/>
<template:addResources type="css" resources="admin-bootstrap.css"/>
<template:addResources type="css" resources="jquery-ui.smoothness.css,jquery-ui.smoothness-jahia.css,tablecloth.css"/>

<c:set var="userDisplayLimit" value="50"/>
<c:set var="jcrUserCountLimit" value="<%= SettingsBean.getInstance().getJahiaJCRUserCountLimit() %>"/>

<template:addResources>
    <script type="text/javascript">
        $(document).ready(function () {
            $("table").tablecloth({
                theme: "default",
                sortable: true
            });
        });

        function doUserAction(event, selectedUsers) {
            var form = $("#usersForm");
            form.find("#flowEvent").val(event);
            form.find("#selectedUsers").val(selectedUsers);
            form.submit();
        }

        function doUsersAction(event) {
            var val = [];
            $('.userCheckbox:checkbox:checked').each(function (i) {
                val[i] = $(this).val();
            });

            if (val.length > 0) {
                doUserAction(event, val.join(","));
            } else {
                alert("<fmt:message key="siteSettings.user.select.one"/>")
            }
        }
    </script>
</template:addResources>

<div class="box-1">
    <form class="form-inline " action="${flowExecutionUrl}" id="searchForm" method="post">
        <fieldset>
            <h2><fmt:message key="label.search"/></h2>
            <div class="input-append">
                <label style="display: none;"  for="searchString"><fmt:message key="label.search"/></label>
                <input class="span6" type="text" id="searchString" name="searchString"
                       value='${searchCriteria.searchString}'
                       onkeydown="if (event.keyCode == 13)
                                   submitForm('search');"/>
                <button class="btn btn-primary" type="submit"  name="_eventId_search">
                    <i class="icon-search icon-white"></i>
                    &nbsp;<fmt:message key='label.search'/>
                </button>
            </div>
            <br/>
            <input type="hidden" id="searchIn" name="searchIn" value="allProps"
                   <c:if test="${empty searchCriteria.searchIn or searchCriteria.searchIn eq 'allProps'}">checked</c:if>
                       onclick="$('.propCheck').attr('disabled', true);">
            </fieldset>
        </form>
    </div>

    <form style="display: none" action="${flowExecutionUrl}" id="usersForm" method="post">
    <input type="hidden" name="_eventId" id="flowEvent">
    <input type="hidden" name="selectedUsers" id="selectedUsers">
</form>

<div>
    <p>
        <c:forEach items="${flowRequestContext.messageContext.allMessages}" var="message">
            <c:if test="${message.severity eq 'INFO'}">
            <div class="alert alert-success">
                <button type="button" class="close" data-dismiss="alert">&times;</button>
                ${message.text}
            </div>
        </c:if>
        <c:if test="${message.severity eq 'ERROR'}">
            <div class="alert alert-error">
                <button type="button" class="close" data-dismiss="alert">&times;</button>
                ${message.text}
            </div>
        </c:if>
    </c:forEach>
</p>

<c:set var="userCount" value="${fn:length(users)}"/>
<div>
    <h2><fmt:message key="siteSettings.user.search.result"/></h2>
    <div class="alert alert-info">
        <c:if test="${userCount lt userDisplayLimit || jcrUserCountLimit lt 0}">
            <fmt:message key="siteSettings.user.search.found">
                <fmt:param value="${userCount}"/>
            </fmt:message>
        </c:if>
        <c:if test="${userCount ge userDisplayLimit}">&nbsp;<fmt:message
                key="siteSettings.user.search.found.limit">
                <fmt:param value="${userDisplayLimit}"/>
            </fmt:message>
        </c:if>
    </div>

    <table class="table table-bordered table-striped table-hover">
        <thead>
            <tr>
                <th class="{sorter: false}" width="5%">&nbsp;</th>
                <th class="sortable"><fmt:message key="label.name"/></th>
                <th width="10%"><fmt:message key="label.actions"/></th>
            </tr>
        </thead>
        <tbody>
            <c:choose>
                <%--@elvariable id="users" type="java.util.List"--%>
                <c:when test="${userCount eq 0}">
                    <tr>
                        <td colspan="4"><fmt:message key="siteSettings.user.search.no.result"/></td>
                    </tr>
                </c:when>
                <c:otherwise>
                    <c:forEach items="${users}" var="curUser" end="${userDisplayLimit - 1}" varStatus="loopStatus">
                        <tr class="sortable-row">
                            <td>${loopStatus.count}</td>
                            <td><a href="#" onclick="doUserAction('editUser', '${fn:escapeXml(curUser.path)}')">${user:displayName(curUser)}</a></td>
                            <td>
                                <a style="margin-bottom:0;" class="btn btn-small" title="${i18nEdit}" href="#edit" onclick="doUserAction('editUser', '${fn:escapeXml(curUser.path)}')">
                                    <i class="icon-edit"></i>
                                </a>
                            </td>
                        </tr>
                    </c:forEach>
                </c:otherwise>
            </c:choose>
        </tbody>
    </table>
</div>
</div>
