<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section="form">
        <div style="margin-top: 1em;">
            <p>${msg("redirectingToExternalLoginForm")}</p>
            <p><a href="${externalLoginUrl}">${externalLoginUrl}</a></p>
        </div>
        <script>
          setTimeout(function() { window.location.href = "${externalLoginUrl}"; }, 5000);
        </script>
    </#if>
</@layout.registrationLayout>